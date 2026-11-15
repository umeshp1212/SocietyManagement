#!/usr/bin/env bash
# =============================================================
# RUN THIS ON YOUR LOCAL MACHINE (where the verified data lives).
# Produces db-migration/maintenance_data.sql — commit it, then on
# production run import-maintenance-prod.sh after `git pull`.
#
# Dumps DATA ONLY (no CREATE TABLE) for the maintenance module.
# EXCLUDES payments / ledger / advance-credits / receipt_sequences so
# production launches with clean, unpaid bills (members pay fresh).
# EXCLUDES society_settings so production's gateway keys are untouched.
# =============================================================
set -euo pipefail

# ---- EDIT THESE if your local differs ----
LOCAL_DB_CONTAINER="${LOCAL_DB_CONTAINER:-society-mysql}"   # your LOCAL mysql container name (docker ps)
DB_USER="${DB_USER:-society_app}"
DB_PASS="${DB_PASS:-CDwing@2019}"
DB_NAME="${DB_NAME:-society_management}"
OUT_FILE="$(dirname "$0")/maintenance_data.sql"

TABLES="maintenance_charge_config water_charge_config unit_tags unit_tag_assignments opening_balances maintenance_bills bill_line_items penalties"

echo ">> Exporting maintenance data from local container '$LOCAL_DB_CONTAINER'..."
docker exec -e MYSQL_PWD="$DB_PASS" "$LOCAL_DB_CONTAINER" \
  mysqldump -u "$DB_USER" \
  --no-create-info --complete-insert --skip-triggers --no-tablespaces \
  "$DB_NAME" $TABLES > "$OUT_FILE"

echo ">> Wrote $OUT_FILE"
echo ">> Line count: $(wc -l < "$OUT_FILE")"
echo ">> Now: git add $OUT_FILE && git commit && git push, then run import on production."
