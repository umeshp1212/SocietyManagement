#!/usr/bin/env bash
# =============================================================
# Nightly MySQL backup for the Society Management stack.
# Dumps the society_management DB from the running MySQL container,
# gzips it with a dated filename, and prunes backups older than N days.
#
# Install (see bottom of file for cron setup):
#   sudo cp scripts/db-backup.sh /usr/local/bin/society-db-backup.sh
#   sudo chmod +x /usr/local/bin/society-db-backup.sh
# =============================================================

set -euo pipefail

# ---- Config (override via environment if needed) ----
CONTAINER="${DB_CONTAINER:-society-mysql}"
DB_NAME="${DB_NAME:-society_management}"
BACKUP_DIR="${BACKUP_DIR:-/opt/society-management/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

# Filename prefix matches the pre-existing backups (society_mgmt_*), with .gz
# added since we now compress. Keeps old and new backups sorting together.
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUTFILE="${BACKUP_DIR}/society_mgmt_${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"

echo "[$(date '+%F %T')] Starting backup of '${DB_NAME}' from container '${CONTAINER}'..."

# Dump using the container's own root password (read from its environment),
# so no password is stored in this script. --single-transaction gives a
# consistent snapshot without locking tables.
docker exec "$CONTAINER" sh -c \
  'exec mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" \
     --single-transaction --routines --triggers --events '"$DB_NAME" \
  | gzip > "$OUTFILE"

# Fail loudly if the dump produced an empty/tiny file.
MINSIZE=1024  # bytes
ACTUALSIZE="$(stat -c%s "$OUTFILE" 2>/dev/null || echo 0)"
if [ "$ACTUALSIZE" -lt "$MINSIZE" ]; then
  echo "[$(date '+%F %T')] ERROR: backup file is suspiciously small (${ACTUALSIZE} bytes). Removing." >&2
  rm -f "$OUTFILE"
  exit 1
fi

echo "[$(date '+%F %T')] Backup written: ${OUTFILE} (${ACTUALSIZE} bytes)"

# ---- Rotation: delete OUR compressed backups older than RETENTION_DAYS ----
# Only matches *.sql.gz, so pre-existing uncompressed *.sql backups are left
# untouched (clean those up manually when you're confident in the new ones).
find "$BACKUP_DIR" -name 'society_mgmt_*.sql.gz' -type f -mtime +"$RETENTION_DAYS" -print -delete

echo "[$(date '+%F %T')] Backup complete. Retained last ${RETENTION_DAYS} days."

# =============================================================
# CRON SETUP (run these once on the server):
#
#   sudo cp scripts/db-backup.sh /usr/local/bin/society-db-backup.sh
#   sudo chmod +x /usr/local/bin/society-db-backup.sh
#   sudo mkdir -p /opt/society-management/backups
#
# Edit root's crontab:
#   sudo crontab -e
#
# Add this line to run every night at 2:30 AM and log output:
#   30 2 * * * /usr/local/bin/society-db-backup.sh >> /var/log/society-db-backup.log 2>&1
#
# Restore a compressed (.sql.gz) backup later:
#   gunzip < /opt/society-management/backups/society_mgmt_YYYYMMDD_HHMMSS.sql.gz \
#     | docker exec -i society-mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" society_management'
#
# Restore a legacy uncompressed (.sql) backup:
#   docker exec -i society-mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" society_management' \
#     < /opt/society-management/backups/society_mgmt_YYYYMMDD_HHMMSS.sql
# =============================================================
