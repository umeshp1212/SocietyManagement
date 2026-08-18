# Deployment Guide - August 12, 2026

Step-by-step guide to deploy all changes made today to production (EC2).
Designed to preserve existing data safely.

---

## Summary of Changes

| Feature | Impact |
|---------|--------|
| Roles & Permissions page fix | Frontend only |
| Password Change/Reset module | Backend + Frontend |
| Forgot Password (email flow) | Backend + Frontend (needs mail dep) |
| Voucher approval workflow | Backend + Frontend + DB (new columns) |
| Manager role | DB seed data |
| DataInitializer fix (no more password reset on restart) | Backend |
| All APIs secured (token required) | Backend + Frontend |
| Mobile-friendly layout | Frontend only |
| Voucher number format change (PPV/CD/FY/xxx) | Backend + DB migration |
| Cheque Number column on voucher list | Frontend only |
| Voucher filters fix (search + status combined) | Backend + Frontend |
| Bulk PDF with filters | Backend + Frontend |
| TDS feature | Backend + Frontend + DB (new table) |

---

## Pre-Deployment: Backup Production Database

**DO THIS FIRST — before any other step.**

```bash
ssh -i your-key.pem ubuntu@13.206.148.223

# Create backup
mysqldump -u society_app -p society_management > /opt/society-management/backups/pre_deploy_aug12_$(date +%Y%m%d_%H%M%S).sql
```

Verify backup file size is reasonable:
```bash
ls -la /opt/society-management/backups/
```

---

## Step 1: Pull Latest Code

```bash
cd /opt/society-management/repo
git pull origin main
```

If you get merge conflicts (unlikely since changes are only on your machine):
```bash
git stash
git pull origin main
git stash pop
```

---

## Step 2: Run Database Migration Script (Voucher Numbers)

This renames existing voucher numbers from `PV-2026-001` to `PPV/CD/2026-27/001`.

**Preview first (safe — read-only):**
```bash
mysql -u society_app -p society_management -e "
SELECT voucher_id, voucher_number AS old_number,
CONCAT('PPV/CD/', financial_year, '/', LPAD(CAST(SUBSTRING_INDEX(voucher_number, '-', -1) AS UNSIGNED), 3, '0')) AS new_number
FROM vouchers
WHERE voucher_number LIKE 'PV-%' OR voucher_number LIKE 'RV-%' OR voucher_number LIKE 'JV-%' OR voucher_number LIKE 'CV-%';"
```

**If preview looks correct, run the migration:**
```bash
mysql -u society_app -p society_management < /opt/society-management/repo/migrate_voucher_numbers.sql
```

Or run the SQL directly:
```sql
-- Rename all voucher numbers to new format
UPDATE vouchers 
SET voucher_number = CONCAT('PPV/CD/', financial_year, '/', LPAD(
    CAST(SUBSTRING_INDEX(voucher_number, '-', -1) AS UNSIGNED), 3, '0'
))
WHERE voucher_number LIKE 'PV-%' 
   OR voucher_number LIKE 'RV-%' 
   OR voucher_number LIKE 'JV-%' 
   OR voucher_number LIKE 'CV-%';

-- Update sequence counter to continue from latest number
UPDATE voucher_sequences 
SET last_number = (
    SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(voucher_number, '/', -1) AS UNSIGNED)), 0)
    FROM vouchers 
    WHERE voucher_number LIKE 'PPV/CD/%'
    AND financial_year = voucher_sequences.financial_year
)
WHERE voucher_type = 'PAYMENT';
```

**Verify:**
```bash
mysql -u society_app -p society_management -e "SELECT voucher_id, voucher_number, financial_year FROM vouchers LIMIT 10;"
```

---

## Step 3: Add MANAGER Role to Database

The backend's `data.sql` uses `INSERT IGNORE` so it won't duplicate, but the new role and TDS table need to exist. The backend will create them automatically via JPA `ddl-auto: update` on startup. However, seed data runs via `data.sql`.

**No manual SQL needed** — the backend will handle this on restart because:
- `ddl-auto: update` creates new tables (`tds_config`, `password_reset_tokens`) and new columns on `vouchers`
- `data.sql` with `INSERT IGNORE` adds the MANAGER role and TDS configs without affecting existing data

---

## Step 4: Build Backend

```bash
cd /opt/society-management/repo/backend
mvn clean package -DskipTests
```

Wait for `BUILD SUCCESS`.

```bash
cp target/society-management-1.0.0-SNAPSHOT.jar /opt/society-management/backend/app.jar
```

---

## Step 5: Update External application-prod.yml

The external config needs the new mail and password-reset settings. Edit on EC2:

```bash
nano /opt/society-management/backend/application-prod.yml
```

**Add these sections if they don't already exist** (add under `spring:` section):

```yaml
  # Mail configuration (add under 'spring:' section)
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enabled: true
```

**Add under `app:` section (if not present):**

```yaml
  frontend-url: http://13.206.148.223
  password-reset:
    token-expiry-minutes: 30
```

**Verify open-in-view is true:**
```yaml
  jpa:
    open-in-view: true
```

Save and exit (`Ctrl+X`, `Y`, `Enter`).

---

## Step 6: Restart Backend

```bash
sudo systemctl restart society-backend
```

**Wait 30 seconds, then verify:**
```bash
sudo systemctl status society-backend
sudo journalctl -u society-backend --lines=30 --no-pager
```

Look for:
- `Started SocietyManagementApplication`
- No errors about missing beans or tables
- `Admin user exists. Username: admin`

**Test API:**
```bash
curl -s http://localhost:8080/api/api-docs | head -5
```

---

## Step 7: Verify Database Changes (New Tables & Columns)

```bash
mysql -u society_app -p society_management -e "SHOW TABLES LIKE '%tds%';"
mysql -u society_app -p society_management -e "SELECT * FROM tds_config LIMIT 5;"
mysql -u society_app -p society_management -e "SHOW TABLES LIKE '%password_reset%';"
mysql -u society_app -p society_management -e "SELECT role_id, role_name FROM roles WHERE role_name = 'MANAGER';"
mysql -u society_app -p society_management -e "SHOW COLUMNS FROM vouchers LIKE 'tds%';"
mysql -u society_app -p society_management -e "SHOW COLUMNS FROM vouchers LIKE '%treasurer%';"
```

Expected:
- `tds_config` table exists with 14 rows
- `password_reset_tokens` table exists
- MANAGER role (ID 9) exists
- Vouchers table has new columns: `tds_applicable`, `tds_rate`, `tds_amount`, `net_payable`, `tds_section`, `viewed_by_treasurer`, `verified_by_secretary`, `approved_by_chairman`, etc.

---

## Step 8: Build and Deploy Frontend

```bash
cd /opt/society-management/repo/frontend
npm install
npx ng build --configuration production
```

Wait for build to complete (2-3 minutes).

**Deploy:**
```bash
sudo rm -rf /var/www/society-management/*
sudo cp -r dist/society-management/browser/* /var/www/society-management/
sudo chown -R www-data:www-data /var/www/society-management
sudo systemctl reload nginx
```

---

## Step 9: Verify Everything Works

Open in browser: `http://13.206.148.223`

**Test checklist:**

| Test | Expected |
|------|----------|
| Login with admin / Admin@123 | Should work (password not reset on restart anymore) |
| Voucher list loads | Vouchers show with new PPV/CD/FY format |
| Search + Status filter together | Results filter correctly |
| Click a voucher detail | Shows TDS section (if applicable) |
| Voucher detail → Submit for Approval | Status changes to PENDING_APPROVAL |
| Sidebar on mobile (resize browser) | Collapses to overlay, hamburger toggle works |
| Settings → TDS Config | Shows 14 vendor categories with rates |
| Forgot Password link on login page | Shows forgot password form |
| Users → Reset Password button | Opens dialog |
| Bulk PDF download with filter | Downloads only filtered vouchers |

---

## Step 10: Transfer Uploaded Files (if not done already)

If you haven't transferred your local uploads folder to EC2:

From your **local Windows PowerShell**:
```powershell
scp -i "path\to\your-key.pem" -r "D:\Tutorial\SocietyManagement\backend\uploads" ubuntu@13.206.148.223:/opt/society-management/
```

On EC2:
```bash
sudo chown -R ubuntu:ubuntu /opt/society-management/uploads
```

---

## Rollback Plan (if something goes wrong)

### Restore Database
```bash
mysql -u society_app -p society_management < /opt/society-management/backups/pre_deploy_aug12_TIMESTAMP.sql
```

### Restore Backend JAR
If you had a previous JAR backed up:
```bash
cp /opt/society-management/backend/app.jar.bak /opt/society-management/backend/app.jar
sudo systemctl restart society-backend
```

### Restore Frontend
```bash
# If you had a previous build:
# The old files are gone after rm -rf, so rely on git
cd /opt/society-management/repo/frontend
git checkout HEAD~1
npm install
npx ng build --configuration production
sudo cp -r dist/society-management/browser/* /var/www/society-management/
sudo systemctl reload nginx
```

---

## Post-Deployment Notes

1. **Existing vouchers** keep their data — new columns (`tds_*`, approval fields) default to NULL/false
2. **New vouchers** will automatically get TDS calculated if vendor category matches
3. **MANAGER role** needs to be assigned to users manually via User Management
4. **Email (forgot password)** won't work until you configure `MAIL_USERNAME` and `MAIL_PASSWORD` — the app still runs fine without it
5. **Voucher number migration** only affects display — no data loss, just format change
6. **All APIs are now protected** — no one can access data without logging in

---

## Quick Reference: New API Endpoints

| Endpoint | Method | Role | Purpose |
|----------|--------|------|---------|
| `/auth/forgot-password` | POST | Public | Request password reset email |
| `/auth/reset-password` | POST | Public | Reset password with token |
| `/auth/reset-password/validate` | GET | Public | Validate reset token |
| `/vouchers/{id}/submit-for-approval` | PATCH | MANAGER/SECRETARY/TREASURER | Submit voucher for approval |
| `/vouchers/{id}/treasurer-view` | PATCH | TREASURER | Mark as viewed |
| `/vouchers/{id}/secretary-verify` | PATCH | SECRETARY | Mark as verified |
| `/vouchers/{id}/chairman-approve` | PATCH | CHAIRMAN | Approve voucher |
| `/tds-config` | GET | Any authenticated | List TDS configs |
| `/tds-config/{id}` | PUT | SUPER_ADMIN/SECRETARY/TREASURER | Update TDS config |
