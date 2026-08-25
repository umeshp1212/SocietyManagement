# Nginx Configuration - Landing Page + Angular App

## Updated Nginx Server Block

The application now has two parts:
- **Landing page** at `https://ppvcd.in/` (static HTML showing committee members)
- **Angular app** at `https://ppvcd.in/app/` (the management application)

### Nginx Config File

Path: `/etc/nginx/sites-enabled/society-management`

```bash
sudo nano /etc/nginx/sites-enabled/society-management
```

Replace the **entire file** with:

```nginx
# HTTP - Redirect to HTTPS
server {
    listen 80;
    server_name ppvcd.in www.ppvcd.in;
    return 301 https://$host$request_uri;
}

# HTTPS - Main config
server {
    listen 443 ssl;
    server_name ppvcd.in www.ppvcd.in;

    # SSL
    ssl_certificate /etc/letsencrypt/live/ppvcd.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/ppvcd.in/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Gzip
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 1000;

    # Backend API (handles all /api/* including file view/download)
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
        proxy_connect_timeout 90s;
        client_max_body_size 10M;
    }

    # Angular App at /app/
    location /app/ {
        root /var/www/society-management;
        try_files $uri $uri/ /app/index.html;
    }

    # Landing page at root /
    location / {
        root /var/www/society-management/landing;
        try_files $uri $uri/ /index.html;
    }
}
```

### Test and Reload

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### Directory Structure on Server

```
/var/www/society-management/
├── app/              ← Angular build files (index.html, *.js, *.css, chunks)
└── landing/          ← Landing page (index.html)
```

### Deployment Steps

1. **Build the Angular app:**
```bash
cd frontend
npx ng build --configuration production
```

2. **Create directory structure on server:**
```bash
sudo mkdir -p /var/www/society-management/app
sudo mkdir -p /var/www/society-management/landing
```

3. **Deploy Angular app to /app/ directory:**
```bash
sudo rm -rf /var/www/society-management/app/*
sudo cp -r dist/society-management/browser/* /var/www/society-management/app/
```

4. **Deploy landing page to /landing/ directory:**
```bash
sudo cp frontend/src/landing/index.html /var/www/society-management/landing/index.html
```

5. **Deploy backend JAR:**
```bash
cd backend
mvn clean package -DskipTests
scp target/society-management-*.jar ubuntu@<EC2_IP>:/opt/society-management/backend/app.jar
# On server:
sudo systemctl restart society-backend
```

6. **Set permissions:**
```bash
sudo chown -R www-data:www-data /var/www/society-management
```

7. **Test and reload Nginx:**
```bash
sudo nginx -t
sudo systemctl reload nginx
```

### How It Works

| URL | What's Served |
|-----|---------------|
| `https://ppvcd.in/` | Landing page (`/var/www/society-management/landing/index.html`) |
| `https://ppvcd.in/app/login` | Angular app (`/var/www/society-management/app/index.html`) |
| `https://ppvcd.in/app/dashboard` | Angular app (SPA routing) |
| `https://ppvcd.in/api/*` | Backend API (proxied to Spring Boot on port 8080) |
| `https://ppvcd.in/api/files/view/*` | Uploaded files (served inline via backend) |
| `https://ppvcd.in/api/files/download/*` | Uploaded files (download via backend) |

### Key Points

- Angular base href is `/app/` so all Angular routes resolve under `/app/`
- Landing page is plain HTML that calls public API endpoints via JavaScript fetch
- The `/api/` proxy block handles everything including file uploads/downloads — no separate block needed
- SSL is managed by Certbot with auto-renewal

### Public API Endpoints (No Auth Required)

The landing page calls these endpoints:
- `GET /api/settings/public` — Society name, address, registration number
- `GET /api/committee-members/public` — Active committee members with photos

### Troubleshooting

| Issue | Fix |
|-------|-----|
| Site unreachable | `sudo systemctl status nginx` — check if running |
| Conflicting server name warning | Remove duplicate server blocks with same `server_name` |
| SSL error "can't provide secure connection" | Check `ssl_certificate` path, add `include /etc/letsencrypt/options-ssl-nginx.conf;` |
| Landing page blank | Check `/var/www/society-management/landing/index.html` exists |
| Angular app 404 on refresh | Ensure `try_files` has `/app/index.html` fallback |
| Angular chunks/CSS 404 | Use `root` not `alias` for `/app/` location. Verify files at `/var/www/society-management/app/` |
| API 403 on public endpoints | Redeploy backend JAR with updated SecurityConfig |
| Uploaded file view 404 | No separate `/api/files/` block needed — `/api/` proxy handles it |
| Committee photos not loading | `/files/view/**` must be permitted in SecurityConfig |

---

## Database Migration (Run on Production MySQL)

After deploying the new backend JAR, run these SQL commands on production to fix schema issues:

### Fix `status` Column Length (Required)

The `vouchers.status` column may be too short to hold `PENDING_APPROVAL` (16 characters). This causes "Data truncated for column 'status'" error when submitting vouchers for approval.

```sql
-- Check current column definition
SHOW COLUMNS FROM vouchers WHERE Field = 'status';

-- Fix: expand to VARCHAR(20)
ALTER TABLE vouchers MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
```

### Create `committee_members` Table (Auto-created by Hibernate)

Hibernate `ddl-auto: update` will auto-create this table on first deploy. If it doesn't, run manually:

```sql
CREATE TABLE IF NOT EXISTS committee_members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    designation VARCHAR(100) NOT NULL,
    photo_path VARCHAR(500),
    phone VARCHAR(15),
    email VARCHAR(100),
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255),
    created_on DATETIME,
    modified_by VARCHAR(255),
    modified_on DATETIME
);
```

### Create Upload Directory for Committee Photos

```bash
sudo mkdir -p /opt/society-management/uploads/committee
sudo chown -R society-backend:society-backend /opt/society-management/uploads/committee
```

---

## Full Deployment Checklist

| Step | Command | Where |
|------|---------|-------|
| 1. Build frontend | `cd frontend && npx ng build --configuration production` | Local |
| 2. Build backend | `cd backend && mvn clean package -DskipTests` | Local |
| 3. Copy frontend to server | `sudo cp -r dist/society-management/browser/* /var/www/society-management/app/` | Server |
| 4. Copy landing page | `sudo cp frontend/src/landing/index.html /var/www/society-management/landing/` | Server |
| 5. Copy backend JAR | `scp target/*.jar ubuntu@<IP>:/opt/society-management/backend/app.jar` | Local→Server |
| 6. Run DB migration | `ALTER TABLE vouchers MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';` | MySQL |
| 7. Restart backend | `sudo systemctl restart society-backend` | Server |
| 8. Reload Nginx | `sudo nginx -t && sudo systemctl reload nginx` | Server |
| 9. Set permissions | `sudo chown -R www-data:www-data /var/www/society-management` | Server |
| 10. Verify | Check `https://ppvcd.in/`, `/app/login`, `/api/settings/public` | Browser |
