# Nginx Configuration - Landing Page + Angular App

## Updated Nginx Server Block

The application now has two parts:
- **Landing page** at `https://ppvcd.in/` (static HTML showing committee members)
- **Angular app** at `https://ppvcd.in/app/` (the management application)

### Updated Nginx Config

```bash
sudo nano /etc/nginx/sites-available/society-management
```

Replace the existing server block with:

```nginx
server {
    listen 80;
    server_name ppvcd.in www.ppvcd.in;

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 1000;

    # Backend API proxy
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

    # Angular App - served from /app/
    location /app/ {
        alias /var/www/society-management/app/;
        try_files $uri $uri/ /app/index.html;
    }

    # Landing page - served from root
    location = / {
        root /var/www/society-management/landing;
        try_files /index.html =404;
    }

    location / {
        root /var/www/society-management/landing;
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets (excluding /api/ paths)
    location ~* ^(?!/api/).*\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$ {
        root /var/www/society-management/app;
        expires 1y;
        add_header Cache-Control "public, immutable";
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
