-- Migration: suspense assignment applied to bills + interest refund
-- ============================================================
-- Adds three columns to suspense_entries so an assignment that was recorded as real
-- payments on the unit's bills (and its interest-refund credit) can be reversed cleanly.
--   applied_payment_ids        comma-separated maintenance_payments ids created on assign
--   interest_refund_credit_id  unit_advance_credits id of the interest refund
--   interest_refund_amount     amount of that refund
-- Stock MySQL 8.0 compatible, safe to run more than once.
-- ============================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suspense_entries' AND COLUMN_NAME = 'applied_payment_ids');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE suspense_entries ADD COLUMN applied_payment_ids VARCHAR(500) NULL',
    'SELECT ''applied_payment_ids exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suspense_entries' AND COLUMN_NAME = 'interest_refund_credit_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE suspense_entries ADD COLUMN interest_refund_credit_id BIGINT NULL',
    'SELECT ''interest_refund_credit_id exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suspense_entries' AND COLUMN_NAME = 'interest_refund_amount');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE suspense_entries ADD COLUMN interest_refund_amount DECIMAL(12,2) NULL',
    'SELECT ''interest_refund_amount exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
