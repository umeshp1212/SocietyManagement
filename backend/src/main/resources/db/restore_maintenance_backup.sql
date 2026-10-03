-- =============================================================================
-- RESTORE MAINTENANCE DATA FROM BACKUP
-- =============================================================================
-- This script restores maintenance data from backup tables.
-- Replace 'YYYYMMDD_HHMMSS' with your actual backup timestamp.
-- =============================================================================

-- Set the backup suffix to restore from (MODIFY THIS!)
SET @backup_suffix = '20241003_143000'; -- Change this to your actual backup timestamp

SELECT '=== RESTORING FROM BACKUP ===' as status;
SELECT CONCAT('Restoring from backup suffix: ', @backup_suffix) as restore_info;

-- First, clear current data (same as cleanup)
SET SQL_SAFE_UPDATES = 0;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE suspense_audit_trail;
TRUNCATE TABLE suspense_entries;
TRUNCATE TABLE unit_advance_credits;
TRUNCATE TABLE maintenance_ledger;
TRUNCATE TABLE bill_line_items;
TRUNCATE TABLE maintenance_payments;
TRUNCATE TABLE penalties;
TRUNCATE TABLE maintenance_bills;
TRUNCATE TABLE opening_balances;
TRUNCATE TABLE receipt_sequences;

-- Restore data from backup tables
SET @sql = CONCAT('INSERT INTO maintenance_bills SELECT * FROM maintenance_bills_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO bill_line_items SELECT * FROM bill_line_items_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO maintenance_payments SELECT * FROM maintenance_payments_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO maintenance_ledger SELECT * FROM maintenance_ledger_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO unit_advance_credits SELECT * FROM unit_advance_credits_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO suspense_entries SELECT * FROM suspense_entries_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO suspense_audit_trail SELECT * FROM suspense_audit_trail_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO penalties SELECT * FROM penalties_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO opening_balances SELECT * FROM opening_balances_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('INSERT INTO receipt_sequences SELECT * FROM receipt_sequences_backup_', @backup_suffix);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
SET SQL_SAFE_UPDATES = 1;

-- Verify restoration
SELECT '=== RESTORATION COMPLETE ===' as status;
SELECT 'maintenance_bills' AS table_name, COUNT(*) AS rows_restored FROM maintenance_bills
UNION ALL SELECT 'maintenance_payments', COUNT(*) FROM maintenance_payments
UNION ALL SELECT 'maintenance_ledger', COUNT(*) FROM maintenance_ledger
UNION ALL SELECT 'penalties', COUNT(*) FROM penalties;

SELECT 'DATA RESTORATION COMPLETED SUCCESSFULLY!' as final_status;