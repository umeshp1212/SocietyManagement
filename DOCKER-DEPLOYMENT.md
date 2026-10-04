# Docker Deployment — Full Stack (nginx + backend + MySQL)

Single `docker-compose.yml` orchestrating three containers:

| Service    | Image/Build        | Role                                               | Published ports |
| ---------- | ------------------ | -------------------------------------------------- | --------------- |
| `mysql`    | mysql:8.0          | Database                                           | none (internal) |
| `backend`  | `./backend`        | Spring Boot API (`prod` profile, context-path /api)| none (internal) |
| `frontend` | `./frontend-new`   | nginx: TLS + admin (`/`) + member PWA (`/member/`) + `/api/` proxy | 80, 443 |

Only `frontend` is exposed to the host. nginx proxies `/api/` to the `backend`
container over the internal `society-network`; `backend` talks to `mysql` the
same way. This mirrors the previous production topology but containerized.

---

## 1. Prerequisites on the host

- Docker + Docker Compose plugin
- DNS for `ppvcd.in` / `www.ppvcd.in` pointing at the host
- Existing Let's Encrypt certs at `/etc/letsencrypt/live/ppvcd.in/` on the host
  (mounted read-only into the nginx container)

---

## 2. Configure secrets

```bash
cp .env.example .env
# edit .env — set DB passwords, JWT_SECRET (required), mail + payment creds
```

`JWT_SECRET` is mandatory: compose uses `${JWT_SECRET:?...}` and will refuse to
start the backend if it is unset.

`FRONTEND_BASE_PATH` is intentionally **empty** — the admin SPA now lives at the
site root (not `/app`), so backend-generated links (password-reset emails, etc.)
resolve to `https://ppvcd.in/...`.

---

## 3. Build & start

```bash
docker compose build
docker compose up -d
docker compose ps
docker compose logs -f backend     # watch startup / DB init
```

Startup order is handled automatically: `mysql` becomes healthy → `backend`
starts → `frontend` starts.

---

## 4. Data persistence (volumes)

| Volume            | Mounted at (container)        | Contents                 |
| ----------------- | ----------------------------- | ------------------------ |
| `mysql_data`      | `/var/lib/mysql`              | Database files           |
| `uploads_data`    | `/app/uploads`                | User-uploaded files      |
| `backend_logs`    | `/app/logs`                   | Application logs         |
| `certbot_webroot` | `/var/www/certbot` (frontend) | ACME challenge webroot   |

The backend's `application-prod.yml` hardcodes upload/log paths to
`/opt/society-management/...`, but the container runs as a non-root user whose
writable area is `/app`. Compose therefore overrides those paths via
`APP_UPLOAD_DIR`, `LOGGING_FILE_PATH`, and `LOGGING_FILE_NAME` (env vars take
precedence over YAML in Spring Boot) and maps them to the volumes above.

> Migrating existing prod data? Copy the host's current
> `/opt/society-management/uploads` into the `uploads_data` volume before
> going live, otherwise previously uploaded files won't be found.

---

## 5. TLS / certificate renewal (IMPORTANT)

The nginx container mounts `/etc/letsencrypt:ro` from the host and terminates
TLS using the existing certs. Renewal still happens on the **host** via certbot;
the container just re-reads the renewed files.

Two things to make renewal work with the containerized nginx:

1. Point host certbot at the shared ACME webroot the container serves. The
   `frontend` nginx serves `/.well-known/acme-challenge/` from
   `/var/www/certbot`. Run renewals with the matching webroot, e.g.:
   ```bash
   certbot renew --webroot -w /var/lib/docker/volumes/<project>_certbot_webroot/_data
   ```
   (Find the exact path with `docker volume inspect <project>_certbot_webroot`.)
   Alternatively, bind-mount a host directory instead of the named volume and
   point certbot at that path.

2. After a successful renewal, reload nginx in the container so it picks up the
   new cert:
   ```bash
   docker compose exec frontend nginx -s reload
   ```
   A deploy hook automates this:
   ```bash
   certbot renew --deploy-hook "docker compose -f /path/to/docker-compose.yml exec -T frontend nginx -s reload"
   ```

If you prefer, keep TLS on a host nginx instead and change the container to
expose only HTTP — tell me and I'll adjust.

---

## 6. Validate before go-live

Docker was not available in the authoring environment, so validate on the host:

```bash
# nginx config syntax
docker compose exec frontend nginx -t

# endpoints
curl -I  https://ppvcd.in/                 # admin/public landing
curl -I  https://ppvcd.in/member/          # member PWA shell
curl -sI https://ppvcd.in/api/settings/public   # backend reachable through proxy
```

Then walk the PWA checklist in `DEPLOYMENT-SEPARATE-APPS.md` (manifest, service
worker scope `/member/`, install prompt). Remember the **PWA icons are still
missing** (`frontend-new/projects/member-portal/src/assets/icons/` is empty) —
add real 192px + 512px PNGs before relying on the install prompt.

---

## 7. Common operations

```bash
docker compose restart backend          # restart one service
docker compose logs -f frontend         # tail nginx logs
docker compose down                     # stop (keeps volumes/data)
docker compose down -v                  # stop AND delete volumes (DESTROYS DATA)
docker compose build backend && docker compose up -d backend   # redeploy backend only
```

---

## 8. Rollback

The previous single-app frontend remains in `frontend/`. To revert the frontend:
change the `frontend.build.context` in `docker-compose.yml` back to `./frontend`
and point its Dockerfile/nginx at the old `/app/` layout, then rebuild. Backend
and MySQL services are unchanged by the PWA work.
