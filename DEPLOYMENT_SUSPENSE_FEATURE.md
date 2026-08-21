# Deployment Guide - Opening Balance & Suspense Account Feature

## Data Safety

This deployment is **completely safe** for existing data:

| What happens | Impact on existing data |
|-------------|----------------------|
| 3 NEW tables created | No effect - new tables only |
| Reading from `units` table | Read-only - no changes |
| Reading from `maintenance_bills` | Read-only - no changes |
| New columns? | NONE - no changes to existing tables |
| Existing bills/payments affected? | NO - only new bill generation includes opening balance |

**JPA `ddl-auto: update` ONLY adds new tables/columns. It NEVER drops or modifies existing ones.**

---

## Step-by-Step Deployment

### Step 1: Backup (Always do this first)

```bash
ssh -i your-key.pem ubuntu@13.206.148.223

mysqldump -u society_app -p society_management > /opt/society-management/backups/pre_suspense_$(date +%Y%m%d).sql
```

### Step 2: Pull latest code

```bash
cd /opt/society-management/repo
git pull origin main
```

### Step 3: Build backend

```bash
cd backend
mvn clean package -DskipTests
cp target/society-management-1.0.0-SNAPSHOT.jar /opt/society-management/backend/app.jar
```

### Step 4: Restart backend

```bash
sudo systemctl restart society-backend
```

On restart, JPA will automatically create these 3 new tables:
- `opening_balances`
- `suspense_entries`
- `suspense_audit_trail`

### Step 5: Verify new tables were created

```bash
mysql -u society_app -p society_management -e "SHOW TABLES LIKE '%opening%'; SHOW TABLES LIKE '%suspense%';"
```

Expected output:
```
+----------------------------------------+
| Tables_in_society_management           |
+----------------------------------------+
| opening_balances                       |
+----------------------------------------+
+----------------------------------------+
| Tables_in_society_management           |
+----------------------------------------+
| suspense_audit_trail                   |
| suspense_entries                       |
+----------------------------------------+
```

### Step 6: Verify backend is running

```bash
sudo systemctl status society-backend
curl -s http://localhost:8080/api/opening-balances/summary
curl -s http://localhost:8080/api/suspense/summary
```

### Step 7: Build and deploy frontend

```bash
cd /opt/society-management/repo/frontend
npm install
npx ng build --configuration production
sudo rm -rf /var/www/society-management/*
sudo cp -r dist/society-management/browser/* /var/www/society-management/
sudo systemctl reload nginx
```

### Step 8: Verify in browser

1. Open `http://13.206.148.223/maintenance`
2. You should see new links: "Opening Balances" and "Suspense"
3. Click "Opening Balances" — should show empty list
4. Click "Suspense" — should show empty list with summary cards (all zeros)
5. Verify existing maintenance bills still load correctly

---

## What DOES NOT change

| Feature | Status |
|---------|--------|
| Existing maintenance bills | Unchanged - still show same data |
| Existing payments | Unchanged |
| Existing vouchers | Unchanged |
| Existing owners/units/tenants | Unchanged |
| Login/Users | Unchanged |
| Bill PDF generation | Unchanged (opening balance only added if you enter one) |

## What IS new

| Feature | Location |
|---------|----------|
| Opening Balances page | /maintenance/opening-balances |
| Suspense Account page | /maintenance/suspense |
| Opening balance in bill arrears | Only for FUTURE bill generation, and only if opening balance is entered |
| Total outstanding API | Now includes opening balance (if any exists for that unit) |

---

## Rollback (if needed)

The new tables are independent. If something goes wrong:

```bash
# Restore previous JAR
cp /opt/society-management/backend/app.jar.bak /opt/society-management/backend/app.jar
sudo systemctl restart society-backend
```

The new empty tables remain in the database (harmless) but won't be used by the old code. No data loss possible.

To completely remove the tables (optional):
```sql
DROP TABLE IF EXISTS suspense_audit_trail;
DROP TABLE IF EXISTS suspense_entries;
DROP TABLE IF EXISTS opening_balances;
```
