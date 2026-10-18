# New Society Onboarding & Docker Deployment Guide

Complete, start-to-finish playbook for onboarding a **new customer society** onto
the Society Management application: AWS instance, domain purchase, DNS, TLS,
Docker deployment, third-party accounts (payments, email/SMTP, push), and the
**super admin credentials** to hand over.

> **Architecture note (read this first).** This application is **single-tenant**:
> one deployment = one society = one database. You do **not** add a new society
> inside an existing instance. For each new customer you stand up a **fresh,
> isolated stack** (its own server or at least its own DB + domain + payment keys +
> SMTP). This guide produces exactly that.

This guide folds in **every real-world problem** hit on previous deployments
(collation crash, upload path, JWT, cert renewal while nginx holds port 80,
Razorpay HTTPS requirement, PWA icons, OTP/SMS) with the fix for each, so the
next onboarding is error-free.

---

## 0. Pre-Flight Checklist (what you collect from/for the customer)

Fill this in before touching a server. Everything downstream references it.

| Item | Example | Where it's used |
|---|---|---|
| Society legal name | `Green Valley CHS Ltd.` | `society_settings`, PDFs, NOCs |
| Domain | `greenvalleychs.in` | DNS, TLS, all URLs |
| Admin contact email | `admin@greenvalleychs.in` | TLS registration, super admin email |
| Voucher prefix | `GVC/CD` | voucher numbering (optional change) |
| Payment gateway | Razorpay and/or Cashfree | online maintenance payments |
| SMTP sender | `noreply@greenvalleychs.in` or Gmail | OTP + password reset email |
| SMS provider | MSG91 / Twilio (optional) | member OTP via SMS |
| Push (FCM) | optional, free | visitor/gate approval push |
| Server size | t3.small (2 vCPU / 2 GB) min | EC2 instance |

Also generate the **three secrets** now and store them in your password manager:

```bash
# Strong app DB password
openssl rand -base64 24
# MySQL root password
openssl rand -base64 24
# JWT secret (REQUIRED, >= 64 chars — compose refuses to start without it)
openssl rand -base64 48
```

---

## 1. AWS Account & EC2 Instance

### 1.1 Create / use an AWS account
1. Go to <https://aws.amazon.com> → **Create an AWS Account** (or sign in).
2. Add a payment method and verify the phone/OTP.
3. Choose a **region close to the society** (India → `ap-south-1` Mumbai). Keep
   all resources in one region.

### 1.2 Launch the EC2 instance
1. EC2 Console → **Launch instance**.
2. **Name:** `greenvalley-society`.
3. **AMI:** Ubuntu Server 22.04 LTS (x86_64). (Amazon Linux works too — Docker
   install differs slightly; both covered in section 5.)
4. **Instance type:** `t3.small` minimum (the Spring Boot + MySQL + 2 Angular
   builds need ~2 GB RAM; `t2.micro`/free tier will OOM during the build —
   see Troubleshooting).
5. **Key pair:** create a new one, download the `.pem`, keep it safe. This is
   your only SSH key.
6. **Storage:** 30 GB gp3 (DB + uploads + Docker images grow).
7. **Network / Security group** — create one with these inbound rules:

   | Type | Port | Source | Why |
   |---|---|---|---|
   | SSH | 22 | **Your IP only** | admin access |
   | HTTP | 80 | 0.0.0.0/0 | web + ACME cert challenge |
   | HTTPS | 443 | 0.0.0.0/0 | the actual site |

   > Do **not** open 3306 (MySQL) or 8080 (backend). They stay on the private
   > Docker network. Opening them is a security hole and is unnecessary.

### 1.3 Allocate an Elastic IP (so the IP never changes)
1. EC2 → **Elastic IPs** → **Allocate**.
2. **Associate** it with your instance.
3. Note this IP — e.g. `13.233.x.x`. **All DNS records point here.**

> **Why:** a stopped/started EC2 gets a new public IP unless you use an Elastic
> IP. Pointing DNS at a non-elastic IP means the site breaks on every reboot.

---

## 2. Domain Purchase & DNS

### 2.1 Buy the domain
Use any registrar (GoDaddy, Namecheap, BigRock, or **AWS Route 53**). For `.in`
domains BigRock/GoDaddy are common in India.
1. Search the domain (`greenvalleychs.in`) → add to cart → pay.
2. Complete registrant details (the society's details are fine).

### 2.2 Point the domain at the Elastic IP (A records)
In the registrar's DNS panel (or Route 53 hosted zone), create:

| Type | Host / Name | Value | TTL |
|---|---|---|---|
| A | `@` (root) | `13.233.x.x` (Elastic IP) | 300 |
| A | `www` | `13.233.x.x` (Elastic IP) | 300 |

> If you use **Route 53**: create a Hosted Zone for the domain, copy the 4 NS
> records into your registrar's "nameservers" setting, then add the A records in
> the hosted zone.

### 2.3 Verify DNS propagation (do NOT skip — TLS fails otherwise)
```bash
# From your laptop
nslookup greenvalleychs.in
nslookup www.greenvalleychs.in
# Both must return your Elastic IP. Propagation can take 5 min to a few hours.
```
Also check globally: <https://dnschecker.org> → both records resolve to the IP.

> **Common error:** running certbot (section 6) before DNS resolves → "challenge
> failed". Always confirm `nslookup` returns your IP first.

---

## 3. Third-Party Accounts (do these in parallel with DNS propagation)

You can set these up while DNS propagates. Only the payment gateway and SMTP are
mandatory for a fully functional member portal; FCM push is optional.

### 3.1 Payment gateway

The app supports **Razorpay** (member portal inline checkout) and **Cashfree**
(maintenance payment redirect). Pick what the customer prefers; you can configure
both. Razorpay is the primary member-portal gateway.

**Razorpay**
1. Sign up at <https://razorpay.com> as a **business** account.
2. Complete **KYC** (PAN, GST if any, bank account, address proof). KYC approval
   can take 1–3 working days — **start early**.
3. Dashboard → **Settings → API Keys → Generate Key**:
   - `Key ID` — `rzp_live_xxxxxxxxxx` (or `rzp_test_xxx` for staging)
   - `Key Secret` — shown **once**, save it immediately.
4. Dashboard → **Settings → Webhooks → Add New Webhook**:
   - URL: `https://greenvalleychs.in/api/member/payments/webhook`
   - Generate a random **webhook secret** → this becomes `RAZORPAY_WEBHOOK_SECRET`.
   - Active events: `payment.authorized`, `payment.captured`, `payment.failed`.
   > The backend **requires** `RAZORPAY_WEBHOOK_SECRET` in production to verify the
   > `X-Razorpay-Signature`. Don't leave it blank.
5. **Live mode needs HTTPS** (section 6). Test mode works on HTTP for staging.

**Cashfree (optional / alternative)**
1. Sign up at <https://www.cashfree.com>, complete KYC.
2. Get `App ID` + `Secret Key` (production set).
3. Return URL: `https://greenvalleychs.in/maintenance/payment-status`
4. Notify (webhook) URL: `https://greenvalleychs.in/api/member/payments/webhook/cashfree`

### 3.2 Email / SMTP (OTP + password reset — mandatory)

**Option A — Gmail App Password (quickest, fine for low volume)**
1. Use a dedicated Gmail (e.g. `greenvalley.society@gmail.com`).
2. Google Account → **Security** → enable **2-Step Verification**.
3. Security → **App passwords** → generate a 16-char password.
4. `MAIL_USERNAME` = the Gmail address, `MAIL_PASSWORD` = the 16-char app password
   (not the normal password).

**Option B — Transactional provider (recommended for scale): Amazon SES / SendGrid / Mailgun**
1. Verify the sending domain (add the DKIM/SPF records at the registrar).
2. Get SMTP host/port/username/password and set `MAIL_HOST`/`MAIL_PORT`/
   `MAIL_USERNAME`/`MAIL_PASSWORD` accordingly.
   - Amazon SES: `email-smtp.ap-south-1.amazonaws.com`, port 587.
3. Move the account **out of sandbox** (SES) so it can email real members.

> **Gotcha:** Gmail silently rejects if you use the account password instead of an
> App Password, or if 2FA isn't on. Symptom: backend logs "Failed to send OTP
> email" / "Username and Password not accepted".

### 3.3 SMS gateway (optional — member OTP via SMS)
Out of the box, OTP is sent by **email** and logged to the backend console. To add
real SMS, integrate MSG91 / Twilio / TextLocal (requires a code change in
`OtpService.java` to call the SMS API). This is optional; email OTP is enough to
go live.

### 3.4 Firebase Cloud Messaging — push for visitor/gate approvals (optional, free)
Only needed if the customer wants the guard→owner push. The gate flow works
without it via in-app polling. To enable:
1. <https://console.firebase.google.com> → create a project (no billing needed).
2. Project settings → General → copy the web app config (`apiKey`, `authDomain`,
   `projectId`, `messagingSenderId`, `appId`).
3. Cloud Messaging → Web Push certificates → generate → copy the **VAPID key**.
4. Service accounts → **Generate new private key** → download the JSON.
   This JSON goes on the server at `secrets/fcm-service-account.json` (section 8).

---

## 4. Get the Code onto the Server

```bash
# From your laptop, SSH in using the key pair and the Elastic IP
chmod 400 greenvalley-key.pem           # (Linux/Mac) lock down the key
ssh -i greenvalley-key.pem ubuntu@13.233.x.x   # Amazon Linux: user is ec2-user
```

On the server:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y git
git clone <YOUR_REPO_URL> society-management
cd society-management
git checkout main          # or the branch with the two-app PWA setup
```
All later commands run from this project root (where `docker-compose.yml` lives).

---

## 5. Install Docker + Compose

### Ubuntu / Debian
```bash
sudo apt remove -y docker docker-engine docker.io containerd runc 2>/dev/null
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

### Amazon Linux 2 / 2023 (only if NOT Ubuntu)
```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl enable --now docker
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

### Enable Docker + run without sudo
```bash
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker        # or log out & back in
docker --version
docker compose version
docker run --rm hello-world     # expect "Hello from Docker!"
```

> **Error:** `docker: permission denied` → you skipped `usermod -aG docker` + re-login.
> Run `newgrp docker` (or reconnect SSH).

---

## 6. TLS Certificates (Let's Encrypt)

The nginx container expects certs at `/etc/letsencrypt/live/<domain>/`. Obtain
them on the **host** before the first start, while port 80 is free.

```bash
sudo apt install -y certbot
# Make sure nothing is on port 80 yet (don't start compose first).
sudo certbot certonly --standalone \
  -d greenvalleychs.in -d www.greenvalleychs.in \
  --agree-tos -m admin@greenvalleychs.in --no-eff-email
```

This creates:
- `/etc/letsencrypt/live/greenvalleychs.in/fullchain.pem`
- `/etc/letsencrypt/live/greenvalleychs.in/privkey.pem`
- `/etc/letsencrypt/options-ssl-nginx.conf`
- `/etc/letsencrypt/ssl-dhparams.pem`

> If the last two are missing, create them:
> ```bash
> sudo curl -s https://raw.githubusercontent.com/certbot/certbot/main/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf \
>   -o /etc/letsencrypt/options-ssl-nginx.conf
> sudo openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048
> ```

> **Errors & fixes**
> - "challenge failed / NXDOMAIN" → DNS not propagated yet (section 2.3), or port
>   80 blocked in the security group.
> - "Problem binding to port 80" → something already on 80. Don't run compose
>   before certbot; or `sudo fuser -k 80/tcp`.

**Certbot must use the same domain you put in `.env` as `DOMAIN`.** nginx reads
its `server_name` and cert path from that one variable at container start, so the
cert you issue here (`/etc/letsencrypt/live/$DOMAIN/`) just needs to match — see
section 7.3. Nothing in the image is hardcoded to a specific society.

---

## 7. Configure This Society's Instance

> **This onboarding uses a SEPARATE, self-contained stack under `onboarding/`.**
> It reuses the same application source (`./backend`, `./frontend-new`) but has its
> own `onboarding/docker-compose.yml`, its own domain-parameterized frontend build
> (`onboarding/frontend.Dockerfile`), and its own `onboarding/.env`. Your existing
> `docker-compose.yml` and `frontend-new/` files are **never touched**. Run every
> command below from the **project root**.

### 7.1 `.env` — all secrets & per-society values
```bash
cp onboarding/.env.example onboarding/.env
nano onboarding/.env
```
Fill in using the values from section 0/3. A complete new-society `onboarding/.env`:

```env
# ---- Domain (SINGLE SOURCE OF TRUTH — set this ONE value) ----
# Drives nginx server_name + TLS cert paths, APP_BASE_URL, FRONTEND_URL, and the
# Cashfree callback URLs. No other URL needs editing.
DOMAIN=greenvalleychs.in
# Optional; defaults to www.$DOMAIN if left blank.
DOMAIN_WWW=

# ---- MySQL ----
MYSQL_ROOT_PASSWORD=<openssl rand output #2>
DB_USERNAME=society_app
DB_PASSWORD=<openssl rand output #1>

# ---- Security (REQUIRED) ----
JWT_SECRET=<openssl rand -base64 48 output>

# ---- Email (OTP + password reset) ----
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=greenvalley.society@gmail.com
MAIL_PASSWORD=<16-char gmail app password>

# ---- Razorpay (member portal) ----
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxx
RAZORPAY_KEY_SECRET=<razorpay secret>
RAZORPAY_WEBHOOK_SECRET=<razorpay webhook secret>

# ---- Cashfree (optional — return/notify URLs auto-derived from DOMAIN) ----
CASHFREE_APP_ID=
CASHFREE_SECRET_KEY=
CASHFREE_ENVIRONMENT=production

# ---- Firebase Cloud Messaging (optional push) ----
FCM_ENABLED=false
FCM_PROJECT_ID=
VISITOR_EXPIRY_SCAN_MS=60000
VISITOR_REPUSH_MS=10000

# ---- Application URLs ----
# Admin SPA is served at site root -> keep EMPTY so reset/email links are correct.
# APP_BASE_URL and FRONTEND_URL are derived from DOMAIN automatically.
FRONTEND_BASE_PATH=
```

> **Critical gotchas carried over from past deployments**
> - `DOMAIN` **must** be set — the frontend entrypoint and backend URL interpolation
>   both refuse to start without it. This single value replaces all the per-URL edits
>   that used to be needed.
> - `JWT_SECRET` **must** be set — compose refuses to start the backend without it.
> - Keep `FRONTEND_BASE_PATH` **empty**. The admin SPA now lives at `/`, so a
>   stale `/app` here breaks password-reset links in emails.
> - Lock the file down: `chmod 600 .env`.

### 7.2 Per-society branding / voucher prefix (optional)
The seed `data.sql` inserts a placeholder society (`ABC Cooperative Housing
Society Ltd.`) only if `society_settings` is empty. After first login, set the
real society name, address, registration number, and committee names from
**Admin → Settings → Society Settings**. That drives PDFs and NOC certificates
(which use `{societyName}`, `{registrationNumber}`, etc.).

If the customer wants a custom **voucher prefix** (default `PPV/CD/...`), change
it in Settings or the voucher config rather than editing seed SQL.

### 7.3 Domain is parameterized — nothing to edit
The onboarding frontend image is **domain-agnostic**. nginx's `server_name` and the
TLS cert paths are rendered at **container start** from the single `DOMAIN` variable
in `onboarding/.env` (see section 7.1). You set `DOMAIN=greenvalleychs.in` once and
everything derives from it:

- nginx `server_name greenvalleychs.in www.greenvalleychs.in`
- certs at `/etc/letsencrypt/live/greenvalleychs.in/`
- backend `APP_BASE_URL` / `FRONTEND_URL` and the Cashfree callback URLs

How it works: `onboarding/frontend.Dockerfile` bakes in an nginx config **template**
(with `${DOMAIN}` / `${DOMAIN_WWW}` placeholders) and an entrypoint that runs
`envsubst` on **only those two** at startup (nginx's own `$host`/`$uri` variables are
preserved), writes the final config, then starts nginx. `onboarding/docker-compose.yml`
passes `DOMAIN` (and optional `DOMAIN_WWW`, which defaults to `www.$DOMAIN`) into the
container. None of this lives in `frontend-new/` — your live build stays as-is.

Result: to re-point the whole stack to a different domain you change one line in
`.env` and `docker compose up -d` — **no file edits, no image rebuild**. (The
compose file still mounts the host's `/etc/letsencrypt` read-only so the container
sees the certs certbot created for that domain.)

> If you prefer not to terminate TLS in the container at all, run a **host nginx**
> as the TLS front and keep the container HTTP-only — ask and I'll provide that
> variant.

### 7.4 PWA icons (pre-go-live blocker for the member app)
The member PWA manifest references 8 icon sizes, but
`frontend-new/projects/member-portal/src/assets/icons/` may be empty. Without at
least `icon-192x192.png` and `icon-512x512.png`, Chrome/Android won't show the
"Add to Home Screen" install prompt. Generate icons from one 512×512 source and
drop them in before building, or the install prompt simply won't appear (the app
still works in the browser).

---

## 8. (Optional) Enable FCM Push

Skip if the customer doesn't want push. To enable:
```bash
mkdir -p secrets
cp /path/to/firebase-service-account.json secrets/fcm-service-account.json
```
In `.env`: `FCM_ENABLED=true` and `FCM_PROJECT_ID=<your-project-id>`.

Then set the **client** config (compiled into the image, so before building):
- `frontend-new/projects/member-portal/src/environments/environment.prod.ts` —
  `fcm.enabled: true` + the web config + `vapidKey`.
- `frontend-new/projects/member-portal/src/firebase-messaging-sw.js` — same
  `firebaseConfig`.

`secrets/` is git-ignored — never commit it.

---

## 9. Build & Start the Stack

```bash
docker compose build        # first build is slow (Maven + 2 Angular apps)
docker compose up -d
docker compose ps           # mysql (healthy), backend, frontend all "running"
docker compose logs -f backend   # watch DB init + Spring Boot start; Ctrl-C to detach
```
Startup is ordered automatically: `mysql` healthy → `backend` → `frontend`.

On first backend start, watch the log for:
```
Default admin user created. Username: admin, Password: Admin@123
*** PLEASE CHANGE THE DEFAULT PASSWORD AFTER FIRST LOGIN ***
Admin user exists. Username: admin
```
That is your **super admin** (see section 11).

> **Build/runtime errors you may hit (all previously seen) & fixes**
> - **Build killed / OOM on t2.micro** → use `t3.small`+, or add swap:
>   ```bash
>   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
>   sudo mkswap /swapfile && sudo swapon /swapfile
>   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
>   ```
> - **"Illegal mix of collations"** on startup/requests → handled automatically:
>   `data.sql` pins the DB + key tables to `utf8mb4_0900_ai_ci` on every start.
>   If you see it on a pre-existing DB, re-run `docker compose up -d backend` so
>   the seed migration runs; it's idempotent.
> - **Backend restarts with JWT error** → `JWT_SECRET` missing in `.env`.
> - **502 on `/api/...`** → backend not healthy yet; `docker compose logs backend`.
> - **nginx won't start / cert error** → cert path/domain mismatch (section 7.3);
>   verify `/etc/letsencrypt/live/<domain>/` exists.
> - **Uploads/logs permission errors** → already handled: compose overrides the
>   hardcoded `/opt/society-management/...` paths with `/app/uploads` and
>   `/app/logs` (env vars beat YAML) and maps them to volumes.

---

## 10. Verify the Deployment

```bash
docker compose exec frontend nginx -t            # config valid
curl -I  https://greenvalleychs.in/              # admin + landing -> 200
curl -I  https://greenvalleychs.in/member/       # member PWA -> 200
curl -I  https://greenvalleychs.in/gate/         # gate PWA -> 200
curl -sI https://greenvalleychs.in/api/settings/public   # backend via proxy
```
In a browser:
- `https://greenvalleychs.in/` — public landing + admin login
- `https://greenvalleychs.in/member/` — member portal (OTP login)
- `https://greenvalleychs.in/gate/` — guard portal
- DevTools → Application → Service Workers: `ngsw-worker.js` active, scope `/member/`
- DevTools → Application → Manifest: icons present (else fix section 7.4)

Payment smoke test (Razorpay **test** keys first): log in as a member with a
registered phone, open a bill, pay with test card `4111 1111 1111 1111` / any
future expiry / any CVV, UPI `success@razorpay`. Confirm the payment records
against the bill, then switch to live keys.

---

## 11. Super Admin Credentials (hand-over)

The backend auto-seeds one super admin on first start:

| Field | Value |
|---|---|
| URL | `https://greenvalleychs.in/` (admin login) |
| Username | `admin` |
| Password | `Admin@123` |
| Role | `SUPER_ADMIN` (full access to all modules) |

**Immediately after handover, do all three:**
1. Log in as `admin` / `Admin@123`.
2. Change the password (Profile → Change Password). The password is **not** reset
   on restart, so your new password sticks.
3. Update **Settings → Society Settings** with the real society details.

### Giving the customer a branded super admin (recommended)
Rather than sharing the generic `admin`, create a named super admin for the
customer and then disable/keep `admin` as a break-glass account:
1. Admin → **Users → Add User** → set username (e.g. `gv_admin`), email, phone.
2. Assign the **Super Admin** role.
3. Share that credential with the customer; have them set their own password.

> **Change the seeded password even if you create a named admin** — `admin` /
> `Admin@123` is public knowledge from this codebase.

### Reset the super admin password from the DB (if locked out)
```bash
# Generate a bcrypt hash for the new password, then update the user row.
docker compose exec backend sh -c 'echo "use a BCrypt tool / app Change Password"'
# Easiest: use the app's Forgot Password flow (needs working SMTP), or:
docker compose exec mysql mysql -u root -p society_management \
  -e "UPDATE users SET is_active=1 WHERE username='admin';"
```
The simplest supported path is the **Forgot Password** email flow once SMTP works;
the `DataInitializer` also re-activates `admin` and re-grants `SUPER_ADMIN` on
every restart, so the account can't be accidentally orphaned.

---

## 12. Automate TLS Renewal

Certs expire every 90 days. Because the **container holds port 80**, renewals use
the shared ACME webroot the container serves (`/var/www/certbot` →
`certbot_webroot` volume), not standalone.

**Easiest — use the bundled script (reads `DOMAIN` from `.env` automatically):**
```bash
sudo bash scripts/setup-cert-renewal.sh
# or pass the domain explicitly: sudo DOMAIN=greenvalleychs.in bash scripts/setup-cert-renewal.sh
```
It switches the renewal to webroot, installs an nginx-reload deploy hook, and runs
a dry-run to verify — all keyed off the same `DOMAIN` the rest of the stack uses,
so there's no society-specific value to edit.

**Manual equivalent (if you prefer):**
```bash
# Find the webroot volume path on the host (project name defaults to the folder):
docker volume inspect society-management_certbot_webroot --format '{{ .Mountpoint }}'

# Dry run (use the Mountpoint from above):
sudo certbot renew --dry-run \
  --webroot -w /var/lib/docker/volumes/society-management_certbot_webroot/_data

# Register the real renewal with an nginx-reload deploy hook:
sudo certbot renew \
  --webroot -w /var/lib/docker/volumes/society-management_certbot_webroot/_data \
  --deploy-hook "docker compose -f $(pwd)/docker-compose.yml exec -T frontend nginx -s reload"
```
certbot installs its own systemd timer; the webroot + deploy-hook are saved in the
renewal config, so future auto-renewals reload nginx automatically.

---

## 13. Backups (set up on day one)

```bash
# Nightly DB dump to host (add to crontab -e)
0 2 * * * docker compose -f /home/ubuntu/society-management/docker-compose.yml \
  exec -T mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" society_management \
  > /home/ubuntu/backups/db_$(date +\%Y\%m\%d).sql
```
Also back up the **uploads** volume (voucher PDFs, visitor photos) and copy both
off-box (S3) periodically:
```bash
docker run --rm -v society-management_uploads_data:/src:ro -v $PWD/backups:/dst \
  alpine sh -c "tar czf /dst/uploads_$(date +%Y%m%d).tgz -C /src ."
```

---

## 14. Day-to-Day Operations

```bash
# Redeploy after code update
git pull && docker compose build && docker compose up -d
# One service
docker compose build backend  && docker compose up -d backend
docker compose build frontend && docker compose up -d frontend
# Logs
docker compose logs -f backend
docker compose logs -f frontend
# Restart / stop (DATA PRESERVED in volumes)
docker compose restart backend
docker compose down
# DANGER: deletes volumes = deletes DB + uploads
docker compose down -v
```

### Data & persistence reference
| Volume / mount | Container path | Contents |
|---|---|---|
| `mysql_data` | `/var/lib/mysql` | database |
| `uploads_data` | `/app/uploads` | voucher PDFs, visitor photos |
| `backend_logs` | `/app/logs` | app logs |
| `certbot_webroot` | `/var/www/certbot` (frontend) | ACME challenge |
| `./secrets` (ro) | `/app/secrets` | FCM service-account JSON (optional) |

---

## 15. Consolidated Troubleshooting (every issue seen to date)

| Symptom | Cause / Fix |
|---|---|
| `docker: permission denied` | `usermod -aG docker $USER` + re-login / `newgrp docker` |
| Backend restarts, "JWT_SECRET" error | Set `JWT_SECRET` in `.env` (required) |
| "Illegal mix of collations" | Re-run backend; `data.sql` pins `utf8mb4_0900_ai_ci` (idempotent) |
| Build killed / OOM | Use `t3.small`+ or add 2 GB swap (section 9) |
| 502 on `/api/...` | Backend not healthy yet — check `docker compose logs backend` |
| nginx won't start, cert error | `DOMAIN` in `.env` must match the certbot domain; confirm `/etc/letsencrypt/live/$DOMAIN/` exists. Check the render: `docker compose exec frontend cat /etc/nginx/conf.d/society.conf \| grep server_name` |
| nginx exits: "DOMAIN is required" | `DOMAIN` not set in `.env` — the frontend entrypoint refuses to start without it |
| certbot "challenge failed" | DNS not propagated or port 80 blocked/occupied |
| `/member/` or `/gate/` 404 on refresh | Rebuild frontend; confirm files landed in `html/member`, `html/gate` |
| No PWA install prompt | Missing 192/512 icons (section 7.4) or not HTTPS |
| OTP email not received | Gmail needs 2FA + App Password; check spam; backend logs "Failed to send OTP email"; OTP also logged to console |
| "No member found with this phone" | Owner `contact_number` must match entered phone — fix in Owners |
| "Payment gateway is not configured" | `RAZORPAY_KEY_ID`/`SECRET` empty — set in `.env`, `docker compose restart backend` |
| Razorpay checkout won't open | HTTPS required in live mode; check `checkout.razorpay.com` loads; Key ID matches |
| Payment succeeds but not recorded | Webhook not configured / wrong secret — set `RAZORPAY_WEBHOOK_SECRET` + webhook URL |
| Guard can't log into `/gate/` | User lacks `SECURITY` role — assign in Admin → Users |
| Owner gets no push (list still updates) | FCM optional/disabled — polling is the fallback (section 8) |
| Uploaded files missing after migrate | Load old uploads into `uploads_data` volume; confirm name with `docker volume ls` |
| Port 80/443 in use | A host web server is running — stop it (`sudo fuser -k 80/tcp`) |
| Stopped EC2 lost its IP | Use the **Elastic IP** (section 1.3) and point DNS at it |

---

## 16. Go-Live Checklist

- [ ] Elastic IP allocated + associated; DNS A records (`@`, `www`) resolve to it
- [ ] Security group: 22 (your IP), 80, 443 open; 3306/8080 closed
- [ ] `t3.small`+ (or swap added); 30 GB disk
- [ ] TLS certs issued for the real domain
- [ ] `.env` complete: `DOMAIN` set to the real domain; strong `MYSQL_*`, `DB_PASSWORD`, `JWT_SECRET`; `FRONTEND_BASE_PATH` empty; `chmod 600`
- [ ] Verified render: `docker compose exec frontend grep server_name /etc/nginx/conf.d/society.conf` shows the new domain
- [ ] Razorpay: live keys + webhook URL + `RAZORPAY_WEBHOOK_SECRET` set; KYC approved
- [ ] SMTP verified (test OTP + password-reset email actually arrive)
- [ ] Member PWA icons present (192 & 512 min); install prompt appears
- [ ] `docker compose ps` all healthy; verification curls return 200
- [ ] Super admin password changed from `Admin@123`; society settings filled in
- [ ] TLS auto-renewal registered with nginx reload deploy-hook
- [ ] Nightly DB + uploads backup scheduled and copied off-box
- [ ] Test payment (test key) then one ₹1 live payment confirmed and recorded
