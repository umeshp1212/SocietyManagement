# Deployment Guide — Separate Admin + Member PWA Apps

This guide covers building and deploying the two-app frontend:

- **Admin + Public** served at the site root (`https://ppvcd.in/`)
- **Member PWA** served at `https://ppvcd.in/member/`

Both apps share the same Spring Boot backend via the `/api/` proxy. The source
lives in `frontend-new/` as an Angular multi-project workspace.

---

## 1. URL Map

| URL                          | Served by      | Notes                              |
| ---------------------------- | -------------- | ---------------------------------- |
| `https://ppvcd.in/`          | admin-portal   | Public landing page                |
| `https://ppvcd.in/login`     | admin-portal   | Admin login                        |
| `https://ppvcd.in/dashboard` | admin-portal   | Admin app (and all admin routes)   |
| `https://ppvcd.in/member/`   | member-portal  | Member PWA login/dashboard         |
| `https://ppvcd.in/api/`      | backend (8080) | Proxied by nginx to the backend    |

The public landing page links to `"/member/login"` for members. Admin keeps all
its existing routes at the root, matching the previous `/app/*` behaviour minus
the `/app` prefix.

---

## 2. Project Layout

```
frontend-new/
├── angular.json                 # admin-portal (baseHref /) + member-portal (baseHref /member/)
├── Dockerfile                   # builds both apps, serves via nginx
├── nginx.conf                   # routing: / -> admin, /member/ -> member PWA, /api/ -> backend
├── package.json                 # build:admin, build:member, build:all scripts
└── projects/
    ├── admin-portal/            # self-contained (own core/, shared/, modules/)
    └── member-portal/           # self-contained PWA (own core/, shared/, manifest, ngsw-config)
```

Each app is self-contained: it has its own `core/` (services, guards,
interceptors, models), `shared/components`, and `environments`. There is no
cross-app import dependency.

---

## 3. Build (local verification)

```powershell
cd frontend-new
npm ci
npm run build:admin      # -> dist/admin-portal/browser
npm run build:member     # -> dist/member-portal/browser  (+ ngsw-worker.js, ngsw.json, manifest.json)
```

The Angular `application` builder outputs to `dist/<project>/browser`.
A successful member build must contain `ngsw-worker.js`, `ngsw.json`, and
`manifest.json` in the `browser` folder — these power the installable PWA.

---

## 4. Build & Deploy with Docker Compose

The frontend service now builds from `frontend-new/`:

```yaml
frontend:
  build:
    context: ./frontend-new
    dockerfile: Dockerfile
  ports:
    - "80:80"
```

Full stack:

```powershell
# From the repo root
docker compose build frontend
docker compose up -d
```

### Validate the nginx config before deploying

Docker was not available in the authoring environment, so the nginx config has
**not** been smoke-tested live. Validate it on a machine with Docker:

```powershell
cd frontend-new
# Syntax check only
docker run --rm -v "${PWD}/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.25-alpine nginx -t
```

Then do a full build and a local run of the compose stack and walk the
verification checklist in section 8 before pointing production traffic at it.

The Dockerfile does a multi-stage build:
1. `node:18-alpine` — `npm ci`, then builds admin-portal and member-portal in production mode.
2. `nginx:1.25-alpine` — copies `dist/admin-portal/browser` → `/usr/share/nginx/html/admin-portal`
   and `dist/member-portal/browser` → `/usr/share/nginx/html/member`, plus `nginx.conf`.
   (The member files land in a `member/` directory so the on-disk path matches the
   `/member/` URL and nginx `root` resolves assets correctly.)

---

## 5. nginx Routing Summary

- `location /api/` → `proxy_pass http://backend:8080/api/` (unchanged).
- `location /member/` → serves the member PWA, SPA fallback to its `index.html`.
  - `ngsw-worker.js`, `ngsw.json`, `safety-worker.js`, `manifest.json` are served
    with `no-cache` (worker/json) so PWA updates roll out immediately.
  - `ngsw-worker.js` gets `Service-Worker-Allowed: /member/` so the service
    worker scope is valid.
- `location /` → serves admin-portal, SPA fallback to its `index.html`.
  - Fingerprinted JS/CSS/images are cached `1y, immutable`; `index.html` is `no-cache`.

---

## 6. HTTPS (required for PWA)

A PWA will only install and run its service worker over **HTTPS** (localhost is
exempt for dev). Terminate TLS at your edge (load balancer, Cloudflare, or an
nginx TLS front) for `ppvcd.in`. If TLS terminates inside this nginx container,
uncomment the HTTP→HTTPS redirect block at the top of `nginx.conf` and add the
`listen 443 ssl;` server block with your certificate paths.

Without HTTPS in production:
- The service worker will not register.
- The "Add to Home Screen" / install prompt will not appear.

---

## 7. ⚠️ Pre-Go-Live Blocker: PWA Icons

`projects/member-portal/src/manifest.json` references 8 icon sizes under
`assets/icons/` (72, 96, 128, 144, 152, 192, 384, 512 px), but
**`projects/member-portal/src/assets/icons/` is currently empty.**

Impact if not fixed:
- Chrome/Android requires at least a 192px and a 512px icon to show the install
  prompt. Missing icons means **no installable PWA**.
- The home-screen icon will be blank/default.

Action required before production:
1. Add real PNG icons at the 8 sizes listed above into
   `projects/member-portal/src/assets/icons/`.
   (Generate from one 512×512 source, e.g. with a PWA asset generator.)
2. Rebuild the member portal and redeploy.
3. At minimum provide `icon-192x192.png` and `icon-512x512.png`.

---

## 8. Post-Deploy Verification Checklist

Run these against the live site after deploy:

- [ ] `https://ppvcd.in/` loads the public landing page.
- [ ] `https://ppvcd.in/login` loads admin login; admin routes work.
- [ ] `https://ppvcd.in/member/` loads the member login.
- [ ] `https://ppvcd.in/api/...` reaches the backend (e.g. settings/public endpoint).
- [ ] DevTools → Application → Manifest shows the member manifest with icons.
- [ ] DevTools → Application → Service Workers shows `ngsw-worker.js` activated
      with scope `/member/`.
- [ ] On mobile Chrome/Safari, the install / "Add to Home Screen" prompt appears
      on the member portal (HTTPS + icons required).
- [ ] Launching from the home screen opens standalone (no browser address bar).
- [ ] A new deploy is picked up on next launch (service worker update; HTML is no-cache).

---

## 9. Rollback

This work is on branch `feature/separate-angular-apps`. The original single-app
frontend is untouched in `frontend/`. To roll back:

1. Revert `docker-compose.yml` `frontend.build.context` to `./frontend`.
2. `docker compose build frontend && docker compose up -d`.

The old app serves admin and member together exactly as before.

---

## 10. Notes / Follow-ups

- `frontend/` (the original single app) is retained for rollback. Remove it once
  the two-app setup is confirmed stable in production.
- The member payment flow uses Razorpay inline checkout (no return-URL route), so
  no payment redirect changes were needed.
- There is an unused `shared` library project in the workspace from an earlier
  approach; it is not referenced by either app and can be deleted later.
