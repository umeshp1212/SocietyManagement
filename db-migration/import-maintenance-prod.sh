#!/usr/bin/env bash
# =============================================================
# RUN THIS ON PRODUCTION (after `git pull`).
# Backs up, verifies schema, cleans existing maintenance data,
# and loads the verified local dump (db-migration/maintenance_data.sql).
#
# Safe to stop at any failed step (set -e). A full backup is taken first.
# =============================================================
set -euo pipefail

# ---- EDIT THESE if production differs ----
PROD_DB_CONTAINER="${PROD_DB_CONTAINER:-society-mysql}"
DB_USER="${DB_USER:-society_app}"
DB_PASS="${DB_PASS:-CDwing@2019}"
DB_NAME="${DB_NAME:-society_management}"

DIR="$(dirname "$0")"
BACKEND_DB="$DIR/../backend/src/main/resources/db"
DATA_FILE="$DIR/maintenance_data.sql"
CLEAN_FILE="$DIR/prod_clean_maintenance.sql"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$DIR/prod_full_backup_${STAMP}.sql"

mysql_run() { docker exec -e MYSQL_PWD="$DB_PASS" -i "$PROD_DB_CONTAINER" mysql -u "$DB_USER" "$DB_NAME" "$@"; }

echo "=== STEP 0: Full production backup -> $BACKUP_FILE ==="
docker exec -e MYSQL_PWD="$DB_PASS" "$PROD_DB_CONTAINER" \
  mysqldump -u "$DB_USER" --no-tablespaces "$DB_NAME" > "$BACKUP_FILE"
if [ ! -s "$BACKUP_FILE" ]; then echo "!! Backup empty — aborting."; exit 1; fi
echo ">> Backup OK ($(wc -c < "$BACKUP_FILE") bytes)"

echo "=== STEP 1: Ensure schema columns exist (idempotent migrations) ==="
mysql_run < "$BACKEND_DB/migration_late_fee_percent.sql" || true
mysql_run < "$BACKEND_DB/migration_bill_interest_receipt_breakup.sql" || true

echo ">> Verifying required columns..."
COLS=$(mysql_run -N -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='society_settings' AND COLUMN_NAME='interest_rate_percent') OR (TABLE_NAME='maintenance_bills' AND COLUMN_NAME IN ('new_interest','last_receipt_amount')) OR (TABLE_NAME='units' AND COLUMN_NAME IN ('two_wheeler_count','four_wheeler_count')));")
echo ">> Found $COLS of 5 expected columns."
if [ "$COLS" -lt 5 ]; then
  echo "!! Not all required columns present. Deploy the new backend (ddl-auto) or add columns, then re-run. Aborting."
  exit 1
fi

if [ ! -s "$DATA_FILE" ]; then echo "!! $DATA_FILE missing/empty. Did you run export on local and git push? Aborting."; exit 1; fi

echo "=== STEP 2: Clean existing maintenance data on production ==="
mysql_run < "$CLEAN_FILE"

echo "=== STEP 3: Load verified maintenance data ==="
{ echo "SET FOREIGN_KEY_CHECKS=0;"; cat "$DATA_FILE"; echo "SET FOREIGN_KEY_CHECKS=1;"; } | \
  docker exec -e MYSQL_PWD="$DB_PASS" -i "$PROD_DB_CONTAINER" mysql -u "$DB_USER" "$DB_NAME"

echo "=== STEP 4: Verify counts ==="
mysql_run -e "SELECT (SELECT COUNT(*) FROM maintenance_bills) AS bills, (SELECT COUNT(*) FROM bill_line_items) AS line_items, (SELECT COUNT(*) FROM opening_balances) AS opening, (SELECT COUNT(*) FROM maintenance_charge_config) AS charges, (SELECT COUNT(*) FROM water_charge_config) AS water;"

echo ""
echo "=== DONE ==="
echo "Backup saved at: $BACKUP_FILE"
echo "Next: open a few unit bill PDFs on the admin portal (C-102, C-104, D-105, a shop)"
echo "and confirm Gross Totals match the accountant BEFORE opening the member portal."
