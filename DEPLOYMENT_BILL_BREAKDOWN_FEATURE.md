# Deployment & Production Test Plan — Itemized Bill Breakdown

**Feature:** Opening Balance split into Principal + Interest Arrears, and the full
transparent bill ladder (Current Charges, Add: Interest Arrears, Principal
Arrears, Add: Late Fee, Less: Adjustment, Total Due, Payable) across admin UI,
member portal, and both bill PDFs.

**Data safety:** All schema changes are **additive** (new columns, defaults 0) and
the `data.sql` migration is **idempotent + guarded** — it only `ADD COLUMN`s when
missing and only backfills rows still at the default. No existing column is
dropped or altered destructively. Existing bills keep their numbers (legacy
`previous_arrears`/`interest_on_arrears` are copied into the new itemized columns
for display).

> Adjust paths/commands to your deployment (Docker vs. systemd). Both variants are
> shown. Replace `ppvcd.in` / server IP with your actual host.

---

## 0. Pre-flight (do this first, every time)

```bash
ssh ubuntu@YOUR_SERVER_IP
cd /path/to/society-management        # repo root with docker-compose.yml

# 1) BACK UP THE DATABASE (non-negotiable)
# Docker:
docker compose exec -T mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" \
  society_management > ~/backups/pre_bill_breakdown_$(date +%Y%m%d_%H%M%S).sql
# systemd/manual:
mysqldump -u society_app -p society_management \
  > ~/backups/pre_bill_breakdown_$(date +%Y%m%d_%H%M%S).sql

# 2) Confirm the backup is non-empty
ls -lh ~/backups/ | tail -3
```

Note the latest backup filename — it is your rollback (section 7).

---

## 1. Deploy the code

```bash
git pull origin main        # or your release branch
```

### Docker
```bash
docker compose build backend frontend
docker compose up -d backend frontend
docker compose ps           # backend + frontend healthy
```

### systemd / manual
```bash
# Backend
cd backend && mvn clean package -DskipTests
cp target/society-management-1.0.0-SNAPSHOT.jar /opt/society-management/backend/app.jar
sudo systemctl restart society-backend

# Frontend (both admin + member)
cd ../frontend-new && npm ci
npm run build:admin && npm run build:member
# deploy dist/admin-portal/browser and dist/member-portal/browser to your web root
sudo systemctl reload nginx
```

---

## 2. Verify the migration ran (new columns exist + backfilled)

The columns are created by Hibernate `ddl-auto=update` AND guarded in `data.sql`.
Confirm on the live DB:

```sql
-- opening_balances: new split columns
SHOW COLUMNS FROM opening_balances LIKE 'principal_arrears';
SHOW COLUMNS FROM opening_balances LIKE 'interest_arrears';

-- maintenance_bills: new columns
SHOW COLUMNS FROM maintenance_bills LIKE 'principal_arrears';
SHOW COLUMNS FROM maintenance_bills LIKE 'interest_arrears';
SHOW COLUMNS FROM maintenance_bills LIKE 'adjustment';

-- Backfill check: existing opening balances moved their amount into principal
SELECT opening_balance_id, amount, principal_arrears, interest_arrears
FROM opening_balances LIMIT 10;
-- Expect: principal_arrears = amount, interest_arrears = 0 for pre-existing rows.

-- Backfill check: existing bills carried legacy arrears into the new columns
SELECT bill_id, previous_arrears, principal_arrears,
       interest_on_arrears, interest_arrears, adjustment, total_amount
FROM maintenance_bills
WHERE previous_arrears > 0 OR interest_on_arrears > 0
LIMIT 10;
-- Expect: principal_arrears = previous_arrears, interest_arrears = interest_on_arrears, adjustment = 0.
```

Check the backend started cleanly:
```bash
# Docker
docker compose logs backend | tail -40
# systemd
sudo journalctl -u society-backend --lines=40 --no-pager
```
Look for `Started SocietyManagementApplication` and no "Illegal mix of
collations" / "Unknown column" errors.

---

## 3. Smoke test the APIs (reachability + shape)

```bash
# Opening balance list returns the new fields
curl -s https://ppvcd.in/api/opening-balances \
  -H "Authorization: Bearer <ADMIN_JWT>" | head
# Expect each row to include principalArrears, interestArrears, amount.

# A bill detail returns the new fields
curl -s https://ppvcd.in/api/bills/<SOME_BILL_ID> \
  -H "Authorization: Bearer <ADMIN_JWT>" | head
# Expect principalArrears, interestArrears, adjustment in the JSON.
```
(Get a JWT by logging into the admin UI and copying the token from DevTools →
Network → any `/api` call → Request Headers → Authorization.)

---

## 4. Functional test in the UI (staging data first if possible)

### 4.1 Opening Balance (admin)
1. Admin → Maintenance → **Opening Balances**.
2. Existing entries: the **Total** column equals the old amount; **Principal
   Arrears** = that amount, **Interest Arrears** = 0 (from the backfill).
3. **Add Opening Balance** → pick a test unit → enter Principal = 1000,
   Interest = 150 → the **Total Opening Balance** preview shows 1150 → Save.
4. Reopen (Edit) that entry → the two fields repopulate correctly.

### 4.2 Generate a CLEAN bill for a test month (see section 5 for options)
After generating, open **Bill Detail** for the test unit and confirm the ladder:
```
Current Charges
Add: Interest Arrears      (carried interest + this month's 1% on principal)
Principal Arrears          (prior unpaid principal + opening-balance principal)
Add: Late Fee              (only if configured and arrears exist)
Less: Adjustment           (only if you entered one at generation)
Total Due
Paid Amount                (only if paid)
Payable
```

### 4.3 Adjustment at generation (Q1 = per-bill)
1. Admin → Maintenance → **Generate Bills**.
2. Expand **Adjustments (optional)** → Add adjustment → pick the test unit →
   enter e.g. 200 → Generate (or Regenerate).
3. Open that unit's bill → **Less: Adjustment** shows 200 and **Total Due** is
   reduced by exactly 200 vs. the same bill without the adjustment.
4. Sanity: a huge adjustment can't make Total Due negative (it floors at 0).

### 4.4 Member portal + PDFs
1. Member portal → login as a test owner → expand the bill → confirm the same
   ladder (Add: Interest Arrears, Principal Arrears, Less: Adjustment, Total Due,
   Payable).
2. **Download Bill** (member) → PDF shows the full ladder.
3. Admin bill detail → download/print bill → admin PDF shows the same ladder.

### 4.5 Online payment allocation (critical — do a ₹1 live or sandbox test)
1. As the test member, pay a bill that has both principal and interest arrears.
2. Confirm principal is cleared before interest, the bill `balanceAmount` drops
   correctly, and (if a bill had an adjustment) `principal + interest` reconciles
   to the adjusted total — no leftover 1-rupee balance and no phantom overpayment.
3. Check the payment landed: Admin → bill → Payments; and DB
   `maintenance_payments` has the row.

---

## 5. "Clean maintenance bill generated" — three options

Pick the one matching what you mean. **Always back up first (section 0).**

### Option A — Regenerate one month (SAFEST; built-in, no SQL)
Deletes only **UNPAID** bills for the selected month and regenerates them with
current charges + the new breakdown. Paid / partially-paid bills are untouched.
- Admin → Maintenance → **Generate Bills** → select month/year →
  tick **"Regenerate existing unpaid bills"** → (optionally add adjustments) →
  **Regenerate Bills**.
- Use this for the normal monthly run and to re-render existing unpaid bills in
  the new format.

### Option B — Reset collections, keep bills (clean slate for RE-TESTING)
Wipes payments/ledger/advance/suspense and resets every bill to UNPAID (bills are
kept, receipt counter reset). Use only on a test/staging DB or a society you
intend to re-collect from scratch.
```bash
# Docker
docker compose exec -T mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" \
  society_management < backend/src/main/resources/db/_reset_payments.sql
# manual
mysql -u society_app -p society_management < backend/src/main/resources/db/_reset_payments.sql
```
The script prints a verification line (payments=0 ledger=0 … seq_rows=0).

### Option C — Fully clean bills for a month, then regenerate (TRUE fresh bills)
When you want brand-new bills (not just reset), delete the month's bills and
regenerate. Do this only after confirming no real payments exist for that month
(or after Option B).
```sql
-- INSPECT FIRST — how many bills and are any paid?
SELECT status, COUNT(*) FROM maintenance_bills
WHERE bill_month = 10 AND bill_year = 2026 GROUP BY status;

-- If safe (all UNPAID or you've reset via Option B), delete that month's bills:
DELETE FROM maintenance_bills WHERE bill_month = 10 AND bill_year = 2026;
```
Then Admin → **Generate Bills** for that month → bills are created fresh with the
new itemized breakdown. (Line items + arrears split recompute from scratch.)

> Why not just Option C always? Deleting bills that have payments would orphan
> receipts/ledger. Option A (regenerate) is the safe default; use B/C only for a
> deliberate clean slate.

---

## 6. Post-deploy verification checklist

- [ ] DB backup taken and verified non-empty
- [ ] Backend started clean (no collation / Unknown column errors)
- [ ] `opening_balances` has `principal_arrears` + `interest_arrears`; backfill correct
- [ ] `maintenance_bills` has `principal_arrears` + `interest_arrears` + `adjustment`; backfill correct
- [ ] Opening Balance screen: add with Principal+Interest, total preview correct, edit repopulates
- [ ] Generate Bills: adjustment rows apply, Total Due reduced by the adjustment
- [ ] Bill detail (admin) shows the full ladder with correct numbers
- [ ] Member portal bill breakdown shows the full ladder
- [ ] Member "Download Bill" PDF and admin bill PDF both show the ladder
- [ ] Online payment (₹1 live or sandbox) allocates principal→interest and reconciles
- [ ] Existing (historical) bills still display correct totals in the new labels

---

## 7. Rollback

The change is additive, so a code rollback is clean — the extra columns just stop
being written and can stay (harmless).

```bash
# Code rollback
git checkout <previous-release-commit>
# Docker
docker compose build backend frontend && docker compose up -d backend frontend
# manual
cd backend && mvn clean package -DskipTests \
  && cp target/*.jar /opt/society-management/backend/app.jar \
  && sudo systemctl restart society-backend
cd ../frontend-new && npm ci && npm run build:admin && npm run build:member   # redeploy

# Data rollback (only if a bad bill run must be undone)
mysql -u society_app -p society_management < ~/backups/pre_bill_breakdown_TIMESTAMP.sql
```

> The new columns are safe to leave in place after a code rollback. Only restore
> the DB backup if a bill-generation run produced wrong data you must undo.

---

## 8. What to watch for (known gotchas, with fixes)

| Symptom | Cause / fix |
|---|---|
| "Unknown column 'principal_arrears'" at startup | `data.sql` didn't run or `ddl-auto` off — restart backend; the guarded ADD COLUMNs are idempotent |
| "Illegal mix of collations" | Pre-existing collation drift — handled at top of `data.sql`; restart backend once |
| Bill shows arrears but no split | Pre-existing bill generated before this feature — displays via legacy fallback (principal label shows previous_arrears). Regenerate (Option A) to get a true split |
| Online payment leaves ₹x balance after "full" pay | Should not happen now — adjustment settles against principal first so buckets reconcile; if seen, capture the bill id + payment and check `adjustment` vs `total_amount` |
| Adjustment makes Total Due look wrong | Total Due = charges + principal + interest + late fee − adjustment, floored at 0 by design |
| Member PDF differs from web | Both use the new ladder now; if stale, hard-refresh / confirm frontend + backend both redeployed |
