# Docker Deployment — Step by Step (from a fresh server)

This guide takes you from a server with **no Docker installed** to a running
production stack:

| Service    | Role                                                        | Ports (host) |
| ---------- | ----------------------------------------------------------- | ------------ |
| `mysql`    | Database                                                    | internal     |
| `backend`  | Spring Boot API (`prod` profile, context-path `/api`)       | internal     |
| `frontend` | nginx: TLS + admin at `/` + member PWA at `/member/` + `/api/` proxy | 80, 443 |

Only `frontend` is reachable from outside. It proxies `/api/` to `backend`, and
`backend` talks to `mysql`, all over a private Docker network.

> Assumes **Ubuntu/Debian** (typical AWS EC2). For Amazon Linux, the only
> difference is the Docker install step (section 2) — notes included inline.

---

## Step 0 — What you need before starting

- SSH access to the server with `sudo`.
- DNS: `ppvcd.in` and `www.ppvcd.in` A-records point to the server's public IP.
- Ports **80** and **443** open in the firewall / security group.
- The project code on the server (we clone it in Step 3).

---

## Step 1 — Connect and update the server

```bash
ssh ubuntu@YOUR_SERVER_IP
sudo apt update && sudo apt upgrade -y
```

---

## Step 2 — Install Docker + Docker Compose

### Ubuntu / Debian

```bash
# Remove any old versions (safe to ignore "not installed" errors)
sudo apt remove -y docker docker-engine docker.io containerd runc 2>/dev/null

# Install prerequisites
sudo apt install -y ca-certificates curl gnupg

# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add the Docker apt repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine + Compose plugin
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

### Amazon Linux 2 / 2023 (only if NOT Ubuntu)

```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl enable --now docker
# Compose plugin:
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

### Start Docker and allow your user to run it without sudo

```bash
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
# Log out and back in (or run `newgrp docker`) for the group change to apply.
newgrp docker
```

### Verify

```bash
docker --version
docker compose version
docker run --rm hello-world      # should print "Hello from Docker!"
```

---

## Step 3 — Get the project onto the server

```bash
# If using git:
sudo apt install -y git
git clone <YOUR_REPO_URL> society-management
cd society-management
git checkout feature/separate-angular-apps   # the branch with the two-app PWA setup

# (Or copy the project with scp/rsync from your machine instead of git.)
```

From here, all commands run from the project root (where `docker-compose.yml` is).

---

## Step 4 — Obtain TLS certificates (Let's Encrypt)

The nginx container expects certs at `/etc/letsencrypt/live/ppvcd.in/`. Get them
on the host **before** the first start.

```bash
sudo apt install -y certbot

# Make sure nothing is already using port 80, then:
sudo certbot certonly --standalone -d ppvcd.in -d www.ppvcd.in \
  --agree-tos -m you@example.com --no-eff-email
```

This creates:
- `/etc/letsencrypt/live/ppvcd.in/fullchain.pem`
- `/etc/letsencrypt/live/ppvcd.in/privkey.pem`
- `/etc/letsencrypt/options-ssl-nginx.conf`
- `/etc/letsencrypt/ssl-dhparams.pem`

> If `options-ssl-nginx.conf` or `ssl-dhparams.pem` are missing, create them:
> ```bash
> sudo curl -s https://raw.githubusercontent.com/certbot/certbot/main/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf \
>   -o /etc/letsencrypt/options-ssl-nginx.conf
> sudo openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048
> ```

Automated renewal is covered in Step 9.

---

## Step 5 — Configure secrets (`.env`)

```bash
cp .env.example .env
nano .env        # or vi .env
```

Fill in real values. At minimum:
- `MYSQL_ROOT_PASSWORD`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` — **required**, long random string (compose refuses to start without it)
- `MAIL_USERNAME`, `MAIL_PASSWORD` (Gmail app password)
- Payment creds (`CASHFREE_*`, `RAZORPAY_*`) if used

Leave `FRONTEND_BASE_PATH` **empty** — admin now lives at the site root, so this
keeps backend-generated links (e.g. password-reset emails) pointing at
`https://ppvcd.in/...`.

Generate a strong JWT secret:
```bash
openssl rand -base64 48
```

---

## Step 6 — (If migrating) bring over existing uploads

Skip if this is a brand-new install. If the old server stored uploads at
`/opt/society-management/uploads`, load them into the Docker volume so existing
files keep working:

```bash
# Create the volume by starting once (Step 7) OR create it explicitly:
docker volume create society-management_uploads_data

# Copy existing files into the volume
docker run --rm \
  -v /opt/society-management/uploads:/src:ro \
  -v society-management_uploads_data:/dst \
  alpine sh -c "cp -av /src/. /dst/"
```

> The volume name is `<project>_uploads_data`, where `<project>` is the compose
> project name (defaults to the folder name, e.g. `society-management`). Confirm
> with `docker volume ls`.

---

## Step 7 — Build and start the stack

```bash
docker compose build          # builds backend + frontend images (first run is slow)
docker compose up -d          # start all services in the background
docker compose ps             # all three should be "running"/"healthy"
```

Watch the backend come up (DB init, Spring Boot start):
```bash
docker compose logs -f backend
# Ctrl-C to stop following (containers keep running)
```

Startup is ordered automatically: `mysql` healthy → `backend` → `frontend`.

---

## Step 8 — Verify the deployment

```bash
# nginx config is valid
docker compose exec frontend nginx -t

# From the server (or your laptop):
curl -I  https://ppvcd.in/                      # admin + public landing -> 200
curl -I  https://ppvcd.in/member/               # member PWA shell -> 200
curl -sI https://ppvcd.in/api/settings/public   # backend reachable via proxy
```

In a browser:
- `https://ppvcd.in/` — public landing + admin login
- `https://ppvcd.in/member/` — member portal
- DevTools → Application → Service Workers: `ngsw-worker.js` active, scope `/member/`
- DevTools → Application → Manifest: member manifest loads

> ⚠️ **PWA icons are still missing** — `frontend-new/projects/member-portal/src/assets/icons/`
> is empty while the manifest references 8 sizes. The install / "Add to Home
> Screen" prompt will not appear until you add real icons (at least
> `icon-192x192.png` and `icon-512x512.png`), then rebuild the frontend
> (`docker compose build frontend && docker compose up -d frontend`).

---

## Step 9 — Automate certificate renewal

Certs expire every 90 days. Renew on the host and reload the container's nginx.

Because the container holds port 80, use the shared ACME webroot that nginx
serves (`/var/www/certbot` inside the container → the `certbot_webroot` volume):

```bash
# Find the webroot volume path on the host
docker volume inspect society-management_certbot_webroot --format '{{ .Mountpoint }}'
```

Test a dry-run renewal (replace the path with the Mountpoint from above):
```bash
sudo certbot renew --dry-run \
  --webroot -w /var/lib/docker/volumes/society-management_certbot_webroot/_data
```

Add a deploy hook so nginx reloads automatically after each real renewal:
```bash
sudo certbot renew \
  --webroot -w /var/lib/docker/volumes/society-management_certbot_webroot/_data \
  --deploy-hook "docker compose -f $(pwd)/docker-compose.yml exec -T frontend nginx -s reload"
```

certbot installs a systemd timer / cron entry automatically; the `--webroot` and
`--deploy-hook` options are stored in the renewal config so future automatic
renewals use them too.

> Simpler alternative: if you'd rather terminate TLS with a host nginx (outside
> Docker) and run the container as HTTP-only behind it, ask and I'll provide that
> variant — it removes the cert-mounting and reload steps.

---

## Step 10 — Day-to-day operations

```bash
# Redeploy after pulling new code
git pull
docker compose build
docker compose up -d

# Redeploy a single service
docker compose build backend && docker compose up -d backend
docker compose build frontend && docker compose up -d frontend

# Logs
docker compose logs -f backend
docker compose logs -f frontend

# Restart / stop
docker compose restart backend
docker compose down            # stop everything (DATA PRESERVED in volumes)

# DANGER: deletes volumes = deletes DB + uploads
docker compose down -v
```

---

## Data & persistence reference

| Volume            | Container path                | Contents               |
| ----------------- | ----------------------------- | ---------------------- |
| `mysql_data`      | `/var/lib/mysql`              | Database               |
| `uploads_data`    | `/app/uploads`                | Uploaded files         |
| `backend_logs`    | `/app/logs`                   | App logs               |
| `certbot_webroot` | `/var/www/certbot` (frontend) | ACME challenge files   |

The backend's `application-prod.yml` hardcodes `/opt/society-management/...` for
uploads/logs, but the container user can only write under `/app`. Compose
overrides those paths via `APP_UPLOAD_DIR`, `LOGGING_FILE_PATH`, and
`LOGGING_FILE_NAME` (env vars beat YAML in Spring Boot) and maps them to volumes.

---

## Troubleshooting

| Symptom                                   | Likely cause / fix                                                        |
| ----------------------------------------- | ------------------------------------------------------------------------- |
| `docker: permission denied`               | You skipped `usermod -aG docker` + re-login. Run `newgrp docker`.         |
| Backend restarts / "JWT_SECRET" error     | `JWT_SECRET` not set in `.env`.                                           |
| 502 on `/api/...`                         | Backend not healthy yet — `docker compose logs backend`.                  |
| nginx won't start, cert error             | Certs missing/wrong path — verify `/etc/letsencrypt/live/ppvcd.in/`.      |
| `/member/` 404s on refresh                | Rebuild frontend; confirm member files landed in `.../html/member`.      |
| Uploaded files missing after migrate      | Re-run Step 6; confirm the volume name with `docker volume ls`.          |
| Port 80/443 in use                        | Another web server (host nginx/apache) is running — stop it first.        |

---

## Rollback

The original single-app frontend is still in `frontend/`. To revert the
frontend only: change `frontend.build.context` in `docker-compose.yml` back to
`./frontend` (with its old `/app/`-based nginx) and rebuild. Backend and MySQL
are unaffected by the PWA work.
