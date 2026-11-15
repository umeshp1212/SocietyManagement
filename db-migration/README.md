# Maintenance module go-live: migrate verified LOCAL data to PRODUCTION

Copies the verified maintenance data (charge config, water config, tags, opening
balances, bills, line items, penalties) from local to production, keyed on
`unit_id` (identical on both DBs). Owners are resolved live from each unit, so the
owner-count difference between local and prod does not matter.

**Excluded on purpose:** payments, ledger, advance-credits, receipt_sequences
(production launches with clean, unpaid bills — members pay fresh) and
`society_settings` (prod keeps its own gateway keys; set Late Fee % / due day in the
admin UI on prod).

## Order of operations

### 1. On your LOCAL machine
```bash
bash db-migration/export-maintenance-local.sh
git add db-migration/maintenance_data.sql
git commit -m "maintenance data for prod go-live"
git push
```
(If your local mysql container is not `society-mysql`, run with:
`LOCAL_DB_CONTAINER=<name> bash db-migration/export-maintenance-local.sh`)

### 2. Deploy the new backend to PRODUCTION first
The copied bills use new columns (`new_interest`, `last_receipt_amount`,
`interest_rate_percent`). Make sure the new build is running:
```bash
git pull
docker compose build backend && docker compose up -d backend
```

### 3. On PRODUCTION
```bash
bash db-migration/import-maintenance-prod.sh
```
This backs up the whole DB, verifies the schema columns, cleans existing
maintenance data, and loads the dump. It prints row counts at the end.

### 4. Verify BEFORE opening payments
Open a few unit bill PDFs on the admin portal (C-102, C-104, D-105, a shop) and
confirm the Gross Total matches the accountant. Then enable the member portal.

## Rollback
A full backup is written to `db-migration/prod_full_backup_<timestamp>.sql` by the
import script (Step 0). To restore:
```bash
docker exec -e MYSQL_PWD='<pass>' -i society-mysql mysql -u society_app society_management < db-migration/prod_full_backup_<timestamp>.sql
```

## Notes
- Default DB creds come from docker-compose (`society_app` / `CDwing@2019`,
  db `society_management`, container `society-mysql`). Override via env vars
  `PROD_DB_CONTAINER`, `DB_USER`, `DB_PASS`, `DB_NAME` if yours differ.
- Scripts are safe to re-run: the clean step truncates, the load re-inserts.
- If you DO want the local payments carried over, remove `--no...` payment
  exclusions — i.e. add `maintenance_payments maintenance_ledger
  unit_advance_credits receipt_sequences` to the TABLES list in
  export-maintenance-local.sh. Not recommended for go-live.
