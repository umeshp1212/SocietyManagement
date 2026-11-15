-- Migration: Monthly interest (%) on outstanding principal ("Add: Interest")
-- ============================================================
-- Final state: society_settings has a single `interest_rate_percent` column.
--
-- Business model: each month a unit carries unpaid principal, a configurable
-- percentage of that OUTSTANDING PRINCIPAL is charged as interest (the
-- accountant's "Add: Interest" line), on principal only (no compounding on
-- interest). This supersedes the earlier flat `late_fee_amount` and the
-- interim `late_fee_percent` experiment, so there is exactly one percentage
-- charge on outstanding principal.
--
-- The app runs with spring.jpa.hibernate.ddl-auto=update, which will auto-add
-- `interest_rate_percent` on startup. This script documents the change and lets
-- it be applied/seeded explicitly in production. Compatible with stock MySQL 8.0
-- (no ADD/DROP COLUMN IF [NOT] EXISTS) and safe to run more than once.
-- ============================================================

-- 1) Add interest_rate_percent (percent; e.g. 1.00 = 1%). 0 = disabled.
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'interest_rate_percent');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN interest_rate_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT ''Monthly interest % on outstanding principal; 0 = disabled''',
    'SELECT ''interest_rate_percent exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1a) If an earlier deploy created late_fee_percent, carry its value across so a
--     configured rate is not lost, then drop it.
SET @old_pct := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'late_fee_percent');
SET @ddl := IF(@old_pct = 1,
    'UPDATE society_settings SET interest_rate_percent = late_fee_percent WHERE interest_rate_percent = 0 AND late_fee_percent <> 0',
    'SELECT ''no late_fee_percent to copy'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@old_pct = 1,
    'ALTER TABLE society_settings DROP COLUMN late_fee_percent',
    'SELECT ''late_fee_percent already removed; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) Drop the obsolete flat late_fee_amount column, if it still exists.
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'late_fee_amount');
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE society_settings DROP COLUMN late_fee_amount',
    'SELECT ''late_fee_amount already removed; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
