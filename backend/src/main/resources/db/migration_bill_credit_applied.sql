-- Migration: "Less: Advance Credit Applied" column on maintenance_bills
-- ============================================================
-- Adds credit_applied: prepaid/overpaid advance credit (from unit_advance_credits)
-- auto-applied to a bill at generation, reducing the payable. The remaining credit
-- carries forward to the next bill.
--
-- Display-breakdown + money field; the stored total_amount already has this subtracted
-- (total = gross - credit_applied), so the Gross Total still reconciles.
--
-- App runs with ddl-auto=update (auto-adds on startup); this documents/seeds it for prod.
-- Stock MySQL 8.0 compatible, safe to run more than once.
-- ============================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maintenance_bills' AND COLUMN_NAME = 'credit_applied');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE maintenance_bills ADD COLUMN credit_applied DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''Advance credit auto-applied to this bill''',
    'SELECT ''credit_applied exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
