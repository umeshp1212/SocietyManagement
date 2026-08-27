# Production Deployment Guide — Member OTP Login & Razorpay Payment

**Feature:** Member Portal with OTP-based Login, Maintenance Dashboard, and Razorpay Payment Gateway  
**Version:** 1.0.0  
**Date:** August 2026  

---

## Table of Contents

1. [Feature Overview](#1-feature-overview)  
2. [Prerequisites](#2-prerequisites)  
3. [Third-Party Account Setup](#3-third-party-account-setup)  
4. [Database Changes](#4-database-changes)  
5. [Backend Configuration](#5-backend-configuration)  
6. [Frontend Configuration](#6-frontend-configuration)  
7. [Docker Deployment](#7-docker-deployment)  
8. [Manual (Non-Docker) Deployment](#8-manual-non-docker-deployment)  
9. [Nginx / Reverse Proxy Configuration](#9-nginx--reverse-proxy-configuration)  
10. [Razorpay Webhook Setup](#10-razorpay-webhook-setup)  
11. [SSL / HTTPS Configuration](#11-ssl--https-configuration)  
12. [Environment Variables Reference](#12-environment-variables-reference)  
13. [Post-Deployment Verification](#13-post-deployment-verification)  
14. [Rollback Plan](#14-rollback-plan)  
15. [Monitoring & Logs](#15-monitoring--logs)  
16. [Troubleshooting](#16-troubleshooting)  
17. [Security Checklist](#17-security-checklist)  

---

## 1. Feature Overview

This release adds a **Member Portal** that allows society members (flat owners) to:

- **Login via OTP** — Enter registered mobile number → receive OTP on mobile (SMS) and email → verify OTP → access portal
- **View Maintenance Dashboard** — See total outstanding, total paid, pending bills, and payment history
- **Pay Online** — Pay full outstanding or partial amount via Razorpay (UPI apps, debit card, credit card, net banking)
- **Payment Recording** — Successful payments automatically update the maintenance bill records against the member's flat number

### New API Endpoints

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/member/auth/send-otp` | POST | Public | Send OTP to registered phone |
| `/api/member/auth/verify-otp` | POST | Public | Verify OTP and get JWT token |
| `/api/member/maintenance/dashboard/{unitId}` | GET | Member JWT | Dashboard data |
| `/api/member/maintenance/bills/{unitId}` | GET | Member JWT | All bills for unit |
| `/api/member/maintenance/outstanding/{unitId}` | GET | Member JWT | Outstanding bills |
| `/api/member/maintenance/payments/{unitId}` | GET | Member JWT | Payment history |
| `/api/member/payments/create-order` | POST | Member JWT | Create Razorpay order |
| `/api/member/payments/verify` | POST | Member JWT | Verify payment & record |
| `/api/member/payments/webhook` | POST | Public | Razorpay server callback |

### New Frontend Routes

| URL | Purpose |
|---|---|
| `/member-login` | Member OTP login page |
| `/member/dashboard` | Member maintenance dashboard |

---

## 2. Prerequisites

Before deployment, ensure the following are available:

- **Java 17** (Eclipse Temurin / OpenJDK)
- **Node.js 18+** and npm (for frontend build)
- **MySQL 8.0** (existing database)
- **Docker & Docker Compose** (if using containerized deployment)
- **Razorpay account** with API keys (see Section 3)
- **SMTP email account** configured (Gmail App Password or other SMTP provider)
- **SSL certificate** for the production domain (required for Razorpay)
- **Domain name** pointing to your server

---

## 3. Third-Party Account Setup

### 3.1 Razorpay Account

1. **Create Account** — Go to [https://razorpay.com](https://razorpay.com) and sign up for a business account
2. **Complete KYC** — Submit business documents for verification (PAN, GST, bank details)
3. **Generate API Keys**:
   - Login to Razorpay Dashboard → Settings → API Keys → Generate Key
   - You will get:
     - `Key ID` — starts with `rzp_live_` (production) or `rzp_test_` (test)
     - `Key Secret` — shown once, save it securely
4. **Test Mode First** — Use test keys (`rzp_test_*`) for staging verification before going live

> **Important:** Razorpay requires your website to be served over **HTTPS** for live mode payments.

### 3.2 Email SMTP (for OTP delivery)

The OTP is sent to the member's registered email address. Configure one of these:

**Option A — Gmail App Password:**
1. Enable 2-Step Verification on the Gmail account
2. Go to Google Account → Security → App Passwords
3. Generate a 16-character app password
4. Use the Gmail address as `MAIL_USERNAME` and the app password as `MAIL_PASSWORD`

**Option B — Transactional Email Service (recommended for production):**
- Use SendGrid, Amazon SES, or Mailgun
- Update `spring.mail.*` properties accordingly in application.yml

### 3.3 SMS Gateway (Optional — for mobile OTP)

Currently, the OTP is:
- **Sent via email** to the member's registered email
- **Logged to the backend console** for SMS integration

To add real SMS delivery, integrate a provider like MSG91, Twilio, or TextLocal:
1. Sign up with an SMS provider
2. Get API key
3. Update `OtpService.java` to call the SMS API alongside the email send

---

## 4. Database Changes

This feature introduces **1 new table** and **modifies 2 existing tables**. Since `ddl-auto: update` is configured, Hibernate will auto-create these changes on startup. However, for production safety, review these DDL statements first.

### 4.1 New Table: `otp_tokens`

```sql
CREATE TABLE otp_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(15) NOT NULL,
    otp VARCHAR(6) NOT NULL,
    email VARCHAR(100),
    expires_at DATETIME NOT NULL,
    verified TINYINT(1) NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    INDEX idx_otp_phone (phone),
    INDEX idx_otp_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.2 Modified Table: `maintenance_bills`

```sql
ALTER TABLE maintenance_bills
    ADD COLUMN razorpay_order_id VARCHAR(100) NULL;

CREATE INDEX idx_bills_razorpay_order ON maintenance_bills(razorpay_order_id);
```

### 4.3 Modified Table: `maintenance_payments`

```sql
ALTER TABLE maintenance_payments
    ADD COLUMN razorpay_payment_id VARCHAR(100) NULL,
    ADD COLUMN razorpay_order_id VARCHAR(100) NULL,
    ADD COLUMN razorpay_signature VARCHAR(255) NULL;

-- Update PaymentMode enum to include RAZORPAY
-- (handled by JPA @Enumerated, no manual DDL needed)

CREATE INDEX idx_payments_razorpay_pid ON maintenance_payments(razorpay_payment_id);
CREATE INDEX idx_payments_razorpay_oid ON maintenance_payments(razorpay_order_id);
```

### 4.4 Pre-Deployment Database Steps

```bash
# 1. Take a full database backup BEFORE deployment
mysqldump -u root -p society_management > backup_before_member_portal_$(date +%Y%m%d).sql

# 2. (Optional) Run the DDL manually if you prefer not to rely on ddl-auto
mysql -u root -p society_management < migration_member_portal.sql

# 3. Verify the owner data has phone numbers populated
SELECT COUNT(*) FROM owners WHERE contact_number IS NOT NULL AND contact_number != '';
-- This count should match the number of owners who will use the member portal
```

> **Note:** Members can only login if their `contact_number` in the `owners` table matches the phone number they enter. Ensure owner records have correct phone numbers before going live.

---

## 5. Backend Configuration

### 5.1 application.yml Changes

Add the following to your production application.yml or override via environment variables:

```yaml
app:
  razorpay:
    key-id: ${RAZORPAY_KEY_ID}        # rzp_live_xxxxxxxxxxxx
    key-secret: ${RAZORPAY_KEY_SECRET}  # Your Razorpay key secret
  otp:
    expiry-minutes: 5        # OTP valid for 5 minutes
    max-attempts: 5          # Max wrong OTP attempts before lockout
    rate-limit-per-hour: 5   # Max OTP requests per phone per hour
```

### 5.2 Production Logging

For production, reduce log verbosity. In application.yml:

```yaml
logging:
  level:
    com.society: INFO              # Change from DEBUG to INFO
    com.society.module.member: INFO
    org.hibernate.SQL: WARN        # Change from DEBUG to WARN
```

> **Important:** The OTP is logged at INFO level in `OtpService.java` for debugging. In production with real SMS integration, change this to DEBUG or remove it entirely to prevent OTP leakage in logs.

### 5.3 Production Security Recommendations

Update `application.yml` for production:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # Change from 'update' to 'validate' after initial deployment
    show-sql: false          # Disable SQL logging in production

# Disable Swagger in production
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

---

## 6. Frontend Configuration

### 6.1 Razorpay Checkout Script

The Razorpay checkout JS is already added to `frontend/src/index.html`:

```html
<script src="https://checkout.razorpay.com/v1/checkout.js"></script>
```

This loads from Razorpay's CDN at runtime. No build-time configuration needed.

### 6.2 Production Environment

The file `frontend/src/environments/environment.prod.ts` is already configured:

```typescript
export const environment = {
  production: true,
  apiUrl: '/api'    // Proxied via Nginx to backend
};
```

No changes needed — the `/api` prefix is handled by the Nginx reverse proxy.

### 6.3 Build the Frontend

```bash
cd frontend
npm ci
npx ng build --configuration production
```

Output will be in `frontend/dist/society-management-frontend/browser/`.

---

## 7. Docker Deployment

### 7.1 Update `.env` File

Create or update the `.env` file in the project root directory:

```env
# MySQL
MYSQL_ROOT_PASSWORD=YourStrongRootPassword
MYSQL_PASSWORD=YourStrongAppPassword

# JWT
JWT_SECRET=YourProductionJwtSecretAtLeast64CharactersLongAndRandomlyGenerated

# Razorpay (NEW)
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=your_razorpay_live_key_secret

# Email SMTP
MAIL_USERNAME=society-noreply@yourdomain.com
MAIL_PASSWORD=your_smtp_password

# Cashfree (existing — keep as-is if still in use)
CASHFREE_APP_ID=your_cashfree_app_id
CASHFREE_SECRET_KEY=your_cashfree_secret_key
CASHFREE_ENVIRONMENT=production
CASHFREE_RETURN_URL=https://yourdomain.com/maintenance/payment-status
CASHFREE_NOTIFY_URL=https://yourdomain.com/api/maintenance/payments/webhook

# Application URLs
APP_BASE_URL=https://yourdomain.com/api
FRONTEND_URL=https://yourdomain.com
```

### 7.2 Update `docker-compose.yml`

Add the Razorpay environment variables to the `backend` service:

```yaml
backend:
  environment:
    # ... existing variables ...
    RAZORPAY_KEY_ID: ${RAZORPAY_KEY_ID:-}
    RAZORPAY_KEY_SECRET: ${RAZORPAY_KEY_SECRET:-}
```

### 7.3 Build and Deploy

```bash
# Pull latest code
git pull origin main

# Take database backup
docker exec society-mysql mysqldump -u root -pYourRootPassword society_management > backup_$(date +%Y%m%d).sql

# Rebuild and restart containers
docker compose build --no-cache
docker compose up -d

# Verify all containers are running
docker compose ps

# Check backend logs for startup errors
docker logs society-backend --tail 100 -f
```

### 7.4 Verify Deployment

```bash
# Check backend health
curl -s https://yourdomain.com/api/member/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phone":"9999999999"}' | jq .

# Expected: error response about "No member found" (confirms endpoint is accessible)
```

---

## 8. Manual (Non-Docker) Deployment

If deploying without Docker:

### 8.1 Backend Deployment

```bash
# 1. Build the JAR
cd backend
mvn clean package -DskipTests

# 2. Copy JAR to production server
scp target/society-management-1.0.0-SNAPSHOT.jar user@server:/opt/society-management/

# 3. Create environment file on server
cat > /opt/society-management/.env << 'EOF'
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=your_razorpay_live_key_secret
MAIL_USERNAME=society-noreply@yourdomain.com
MAIL_PASSWORD=your_smtp_password
EOF

# 4. Start the application
cd /opt/society-management
java -jar \
  -Xms512m -Xmx1024m \
  -DRAZORPAY_KEY_ID=$RAZORPAY_KEY_ID \
  -DRAZORPAY_KEY_SECRET=$RAZORPAY_KEY_SECRET \
  -DMAIL_USERNAME=$MAIL_USERNAME \
  -DMAIL_PASSWORD=$MAIL_PASSWORD \
  society-management-1.0.0-SNAPSHOT.jar
```

### 8.2 Systemd Service (Recommended)

Create `/etc/systemd/system/society-management.service`:

```ini
[Unit]
Description=Society Management Backend
After=mysql.service
Requires=mysql.service

[Service]
Type=simple
User=appuser
Group=appuser
WorkingDirectory=/opt/society-management
EnvironmentFile=/opt/society-management/.env
ExecStart=/usr/bin/java -jar -Xms512m -Xmx1024m society-management-1.0.0-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable society-management
sudo systemctl start society-management
sudo systemctl status society-management
```

### 8.3 Frontend Deployment

```bash
# 1. Build
cd frontend
npm ci
npx ng build --configuration production

# 2. Copy to Nginx web root
sudo cp -r dist/society-management-frontend/browser/* /usr/share/nginx/html/
```

---

## 9. Nginx / Reverse Proxy Configuration

The existing `nginx.conf` already handles API proxying. Verify the member endpoints are proxied correctly:

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # Redirect HTTP to HTTPS (required for Razorpay live mode)
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name yourdomain.com;

    ssl_certificate /etc/ssl/certs/yourdomain.crt;
    ssl_certificate_key /etc/ssl/private/yourdomain.key;

    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;

    # API proxy — covers ALL /api/* including /api/member/*
    location /api/ {
        proxy_pass http://backend:8080/api/;    # or http://localhost:8080/api/ for non-Docker
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
        proxy_connect_timeout 90s;
        client_max_body_size 10M;
    }

    # Angular SPA — fallback to index.html for /member-login, /member/dashboard, etc.
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

> **Key Point:** No special Nginx rules are needed for member routes. The `/api/` proxy handles all backend calls, and the Angular SPA fallback handles `/member-login` and `/member/dashboard`.

---

## 10. Razorpay Webhook Setup

Razorpay webhooks provide a server-to-server backup payment confirmation. This is critical for cases where the user's browser closes after payment but before verification.

### 10.1 Configure in Razorpay Dashboard

1. Login to [Razorpay Dashboard](https://dashboard.razorpay.com)
2. Go to **Settings → Webhooks → Add New Webhook**
3. Configure:
   - **Webhook URL:** `https://yourdomain.com/api/member/payments/webhook`
   - **Secret:** (optional but recommended — generate a random string)
   - **Alert Email:** your admin email for webhook failures
   - **Active Events:** Select these events:
     - `payment.authorized`
     - `payment.captured`
     - `payment.failed`
4. Click **Create Webhook**

### 10.2 Verify Webhook Connectivity

After setting up, use Razorpay's "Test Webhook" feature to send a test event. Check backend logs:

```bash
docker logs society-backend --tail 50 | grep "Razorpay webhook"
```

### 10.3 Webhook IP Whitelisting (Optional)

For extra security, whitelist Razorpay's webhook IPs in your firewall. Razorpay publishes their IP ranges at [https://razorpay.com/docs/webhooks/ip-whitelist/](https://razorpay.com/docs/webhooks/).

---

## 11. SSL / HTTPS Configuration

**Razorpay live mode requires HTTPS.** Test mode works on HTTP, but live payments will fail without SSL.

### Option A — Let's Encrypt (Free)

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
sudo certbot renew --dry-run    # Verify auto-renewal
```

### Option B — Commercial SSL

Install your certificate files and update the Nginx config with the paths to your `.crt` and `.key` files.

---

## 12. Environment Variables Reference

Complete list of environment variables for this feature:

| Variable | Required | Description | Example |
|---|---|---|---|
| `RAZORPAY_KEY_ID` | Yes | Razorpay API Key ID | `rzp_live_xxxxxxxxxxxx` |
| `RAZORPAY_KEY_SECRET` | Yes | Razorpay API Key Secret | `xxxxxxxxxxxxxxxx` |
| `MAIL_USERNAME` | Yes | SMTP email address for OTP delivery | `noreply@society.com` |
| `MAIL_PASSWORD` | Yes | SMTP email password / app password | `xxxx xxxx xxxx xxxx` |
| `JWT_SECRET` | Yes (existing) | JWT signing secret (min 64 chars) | Random string |
| `MYSQL_ROOT_PASSWORD` | Yes (existing) | MySQL root password | Strong password |
| `MYSQL_PASSWORD` | Yes (existing) | MySQL app user password | Strong password |
| `APP_BASE_URL` | Yes (existing) | Backend API base URL | `https://yourdomain.com/api` |
| `FRONTEND_URL` | Yes (existing) | Frontend URL | `https://yourdomain.com` |

### OTP Configuration (in application.yml, not env vars)

| Property | Default | Description |
|---|---|---|
| `app.otp.expiry-minutes` | 5 | OTP validity duration |
| `app.otp.max-attempts` | 5 | Max wrong attempts before OTP invalidated |
| `app.otp.rate-limit-per-hour` | 5 | Max OTP requests per phone per hour |

---

## 13. Post-Deployment Verification

Run through this checklist after deployment:

### 13.1 Backend Health Checks

```bash
# 1. Verify backend is running
curl -s https://yourdomain.com/api/auth/login -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrong"}' | jq .success
# Expected: false (confirms API is reachable)

# 2. Test OTP endpoint is accessible (public)
curl -s https://yourdomain.com/api/member/auth/send-otp -X POST \
  -H "Content-Type: application/json" \
  -d '{"phone":"0000000000"}' | jq .
# Expected: error about "invalid phone" or "no member found"

# 3. Test webhook endpoint is accessible (public)
curl -s https://yourdomain.com/api/member/payments/webhook -X POST \
  -H "Content-Type: application/json" \
  -d '{}' | head
# Expected: 200 OK

# 4. Verify database tables were created
mysql -u root -p society_management -e "DESCRIBE otp_tokens;"
mysql -u root -p society_management -e "SHOW COLUMNS FROM maintenance_bills LIKE 'razorpay%';"
mysql -u root -p society_management -e "SHOW COLUMNS FROM maintenance_payments LIKE 'razorpay%';"
```

### 13.2 End-to-End Test Flow

1. Open `https://yourdomain.com/login`
2. Click **"Member Login (OTP)"** link at the bottom
3. Enter a registered owner's phone number → click **Send OTP**
4. Check the owner's email inbox for the OTP (also check backend logs)
5. Enter the OTP → click **Verify & Login**
6. Dashboard should load with outstanding bills and payment history
7. Click **Pay Online** or the pay button on a specific bill
8. Choose Full or Partial amount → click **Proceed to Pay**
9. Razorpay checkout should open with UPI, Card, and Netbanking options
10. **For first test, use Razorpay test mode** with test card: `4111 1111 1111 1111`
11. After successful payment, verify:
    - Success receipt is shown in the dialog
    - Dashboard refreshes with updated outstanding amount
    - Payment appears in the Payment History tab
    - Database: check `maintenance_payments` table for the new record
    - Database: check `maintenance_bills` table for updated `paid_amount` and `status`

### 13.3 Razorpay Test Card Details

Use these in **test mode** (with `rzp_test_*` keys):

| Card Network | Number | Expiry | CVV |
|---|---|---|---|
| Visa | 4111 1111 1111 1111 | Any future date | Any 3 digits |
| Mastercard | 5267 3181 8797 5449 | Any future date | Any 3 digits |

UPI Test ID: `success@razorpay` (for UPI test payments)

---

## 14. Rollback Plan

If issues are found after deployment:

### 14.1 Quick Rollback (Code)

```bash
# Revert to previous version
git checkout <previous-commit-hash>

# Rebuild and redeploy
docker compose build --no-cache
docker compose up -d
```

### 14.2 Database Rollback

The new columns and table are additive — they won't break existing functionality even if you roll back the code. However, if needed:

```bash
# Restore from backup
mysql -u root -p society_management < backup_before_member_portal_YYYYMMDD.sql
```

### 14.3 Partial Rollback (Disable Feature Only)

If you want to keep the deployment but disable the member portal temporarily:

1. Remove the Razorpay environment variables (backend will log a warning but won't crash)
2. In `SecurityConfig.java`, comment out the `/member/auth/**` permitAll line → member endpoints return 401
3. Or simply remove the `/member-login` link from the admin login page

---

## 15. Monitoring & Logs

### 15.1 Key Log Messages to Monitor

```bash
# Backend logs — successful OTP send
grep "OTP sent for member phone" /app/logs/society.log

# Backend logs — successful member login
grep "Member login successful" /app/logs/society.log

# Backend logs — Razorpay order creation
grep "Razorpay order created" /app/logs/society.log

# Backend logs — successful payment
grep "Payment verified and recorded" /app/logs/society.log

# Backend logs — webhook events
grep "Razorpay webhook" /app/logs/society.log

# Backend logs — errors
grep "ERROR" /app/logs/society.log | grep -i "razorpay\|otp\|member"
```

### 15.2 Docker Log Commands

```bash
# Follow backend logs
docker logs society-backend -f --tail 200

# Search for payment-related events
docker logs society-backend 2>&1 | grep -i "payment"

# Search for OTP events
docker logs society-backend 2>&1 | grep -i "OTP"
```

### 15.3 Database Monitoring Queries

```sql
-- Daily OTP requests (monitor for abuse)
SELECT DATE(created_at) as day, COUNT(*) as otp_count
FROM otp_tokens
GROUP BY DATE(created_at)
ORDER BY day DESC
LIMIT 7;

-- Recent Razorpay payments
SELECT p.payment_id, p.amount, p.razorpay_payment_id, p.status, p.payment_date,
       u.unit_number
FROM maintenance_payments p
JOIN units u ON p.unit_id = u.unit_id
WHERE p.payment_mode = 'RAZORPAY'
ORDER BY p.payment_date DESC
LIMIT 20;

-- Payment success rate
SELECT status, COUNT(*) as count, SUM(amount) as total_amount
FROM maintenance_payments
WHERE payment_mode = 'RAZORPAY'
GROUP BY status;

-- Outstanding cleanup — expired OTPs (auto-cleaned, but check)
SELECT COUNT(*) as expired_otps FROM otp_tokens WHERE expires_at < NOW() AND verified = 0;
```

### 15.4 Razorpay Dashboard Monitoring

- Login to [Razorpay Dashboard](https://dashboard.razorpay.com) → Transactions
- Monitor payment success/failure rates
- Check webhook delivery status under Settings → Webhooks → Recent Deliveries
- Set up Razorpay's built-in email alerts for failed payments

---

## 16. Troubleshooting

### Issue: "No member found with this phone number"

**Cause:** Owner's `contact_number` in the database doesn't match the entered phone number.  
**Fix:**
```sql
-- Check what phone number is stored for the owner
SELECT owner_id, full_name, contact_number, alternate_number FROM owners WHERE full_name LIKE '%OwnerName%';

-- Update if incorrect
UPDATE owners SET contact_number = '9876543210' WHERE owner_id = <id>;
```

### Issue: "Payment gateway is not configured"

**Cause:** `RAZORPAY_KEY_ID` or `RAZORPAY_KEY_SECRET` environment variables are missing or empty.  
**Fix:** Verify env vars are set and restart the backend:
```bash
docker exec society-backend env | grep RAZORPAY
# If empty, update .env file and restart
docker compose restart backend
```

### Issue: OTP email not received

**Cause:** SMTP configuration issue.  
**Fix:**
1. Check backend logs: `docker logs society-backend | grep "OTP email"`
2. If "Failed to send OTP email" appears, verify SMTP credentials
3. Check spam/junk folder
4. For Gmail: ensure App Password is used (not regular password)
5. OTP is also logged to console — check there as a fallback

### Issue: Razorpay checkout not opening

**Cause:** Razorpay JS not loaded, or Key ID mismatch.  
**Fix:**
1. Open browser DevTools → Console → check for Razorpay script errors
2. Open Network tab → verify `checkout.razorpay.com/v1/checkout.js` loaded successfully
3. Verify the Key ID returned from `/create-order` matches your Razorpay dashboard
4. Ensure HTTPS in production (Razorpay live mode requires it)

### Issue: Payment successful in Razorpay but not recorded

**Cause:** Verify endpoint failed, or webhook not configured.  
**Fix:**
1. Check backend logs for verify errors
2. Check Razorpay Dashboard → Transactions → find the payment → check status
3. Verify webhook is configured and receiving events (Razorpay Dashboard → Webhooks)
4. Manual recovery:
```sql
-- Find the Razorpay payment
SELECT * FROM maintenance_payments WHERE razorpay_payment_id = 'pay_xxxxx';
-- If not found, the payment wasn't recorded — may need manual insertion
```

### Issue: "Maximum OTP attempts exceeded"

**Cause:** Member entered wrong OTP 5 times.  
**Fix:** Wait a few minutes and request a new OTP, or clear the expired token:
```sql
DELETE FROM otp_tokens WHERE phone = '9876543210' AND verified = 0;
```

---

## 17. Security Checklist

Before going live, verify all items:

- [ ] **HTTPS enabled** — Razorpay live mode requires SSL
- [ ] **Razorpay live keys** — Switch from `rzp_test_*` to `rzp_live_*`
- [ ] **JWT secret changed** — Use a strong random 64+ character string (not the default)
- [ ] **Database password changed** — Not using default `root`/`root`
- [ ] **SMTP credentials secured** — Not hardcoded, using environment variables
- [ ] **Swagger disabled** — Set `springdoc.api-docs.enabled: false` in production
- [ ] **ddl-auto set to validate** — Change from `update` to `validate` after first deployment
- [ ] **SQL logging disabled** — `show-sql: false` and `org.hibernate.SQL: WARN`
- [ ] **OTP console logging** — Remove or set to DEBUG level in production (prevents OTP leakage)
- [ ] **Razorpay webhook configured** — Webhook URL set in Razorpay dashboard
- [ ] **Webhook secret** — Configured for signature verification (optional but recommended)
- [ ] **CORS configured** — Only allow requests from your production domain
- [ ] **Rate limiting** — OTP rate limit is set (default 5/hour)
- [ ] **Database backup** — Taken before deployment
- [ ] **Owner phone numbers verified** — Owners have correct `contact_number` in database
- [ ] **Test payment completed** — Full flow tested with test credentials
- [ ] **Live payment tested** — Small amount test with live credentials (₹1 test)
- [ ] **Firewall rules** — Port 8080 not directly exposed (only via Nginx proxy)
- [ ] **Environment file permissions** — `.env` file readable only by deploy user (`chmod 600`)

---

## Appendix: File Inventory

### New Backend Files (15 files)

```
backend/src/main/java/com/society/module/member/
├── controller/
│   ├── MemberAuthController.java
│   ├── MemberMaintenanceController.java
│   └── MemberPaymentController.java
├── dto/
│   ├── CreatePaymentOrderRequest.java
│   ├── MemberDashboardResponse.java
│   ├── MemberLoginResponse.java
│   ├── PaymentOrderResponse.java
│   ├── SendOtpRequest.java
│   ├── VerifyOtpRequest.java
│   └── VerifyPaymentRequest.java
├── entity/
│   └── OtpToken.java
├── repository/
│   └── OtpTokenRepository.java
└── service/
    ├── MemberAuthService.java
    ├── MemberMaintenanceService.java
    ├── OtpService.java
    └── RazorpayService.java
```

### Modified Backend Files (7 files)

```
backend/pom.xml                                          — Added razorpay-java dependency
backend/src/main/resources/application.yml               — Added razorpay + otp config
backend/src/main/java/.../config/SecurityConfig.java     — Added member public endpoints
backend/src/main/java/.../security/JwtAuthenticationFilter.java — Member token handling
backend/src/main/java/.../entity/MaintenanceBill.java    — Added razorpayOrderId field
backend/src/main/java/.../entity/MaintenancePayment.java — Added Razorpay fields + enum
backend/src/main/java/.../repository/MaintenanceBillRepository.java — Added Razorpay query
backend/src/main/java/.../repository/MaintenancePaymentRepository.java — Added Razorpay queries
backend/src/main/java/.../repository/OwnerRepository.java — Added phone lookup query
```

### New Frontend Files (6 files)

```
frontend/src/app/
├── core/
│   ├── guards/member-auth.guard.ts
│   └── services/member-auth.service.ts
└── modules/member/
    ├── member.routes.ts
    ├── member-login/member-login.component.ts
    ├── member-dashboard/member-dashboard.component.ts
    └── member-payment-dialog/member-payment-dialog.component.ts
```

### Modified Frontend Files (4 files)

```
frontend/src/index.html                                  — Added Razorpay checkout.js
frontend/src/app/app.routes.ts                           — Added member routes
frontend/src/app/core/interceptors/auth.interceptor.ts   — Member token handling
frontend/src/app/modules/auth/login/login.component.ts   — Added Member Login link
```

### Modified Root Files (1 file)

```
.env.example — Added RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
```
