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

5. **Set permissions:**
```bash
sudo chown -R www-data:www-data /var/www/society-management
```

6. **Test and reload Nginx:**
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

### Key Changes from Previous Setup

1. **Angular app moved from `/` to `/app/`** — `base href` changed to `/app/` in `index.html`
2. **Landing page at root `/`** — static HTML that calls public APIs for society info and committee members
3. **Nginx uses `alias`** for `/app/` instead of `root` to correctly map the directory
4. **`try_files` for `/app/`** falls back to `/app/index.html` for Angular SPA routing

### Public API Endpoints (No Auth Required)

The landing page calls these endpoints:
- `GET /api/settings/public` — Society name, address, registration number
- `GET /api/committee-members/public` — Active committee members with photos

### Troubleshooting

| Issue | Fix |
|-------|-----|
| Landing page shows blank | Check `/var/www/society-management/landing/index.html` exists |
| Angular app 404 on refresh | Ensure `try_files` has `/app/index.html` fallback |
| API calls 404 from landing page | Verify `/api/` location block is above the root `/` block |
| Committee photos not loading | Apply the Nginx regex fix: `^(?!/api/)` for static assets |
| Angular assets (JS/CSS) 404 | Ensure files are in `/var/www/society-management/app/` not a subdirectory |
