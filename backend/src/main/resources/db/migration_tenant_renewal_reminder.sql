-- =====================================================================
-- Migration: Tenant agreement renewal reminder
-- Adds the tracking column used by the renewal-reminder scheduler so the
-- owner is reminded once per cycle (not every day) while an agreement is
-- inside the 15-day expiry window. The column is cleared on renewal
-- (rent_end_date change) to re-arm the reminder for the next expiry.
--
-- Safe to run on an existing database. Idempotent: skips if the column
-- already exists (MySQL 8+ does not support "ADD COLUMN IF NOT EXISTS",
-- so we guard with information_schema).
-- =====================================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tenants'
      AND COLUMN_NAME = 'renewal_reminder_sent_on'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE tenants ADD COLUMN renewal_reminder_sent_on DATE NULL COMMENT ''Last date the owner was emailed an agreement-renewal reminder; cleared on renewal'' AFTER move_out_reason',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
