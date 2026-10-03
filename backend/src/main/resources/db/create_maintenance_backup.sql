-- =============================================================================
-- CREATE MAINTENANCE DATA BACKUP BEFORE CLEANUP
-- =============================================================================
-- This script creates backup tables of all maintenance-related data before
-- deletion, allowing you to restore if needed.
-- =============================================================================

-- Create backup tables with current timestamp
SET @backup_suffix = DATE_FORMAT(NOW(), '%Y%m%d_%H%i%s');

-- Show what we're backing up
SELECT '=== CREATING BACKUP TABLES ===' as status;
SELECT @backup_suffix as backup_timestamp;

-- Create backup of maintenance bills
SET @sql = CONCAT('CREATE TABLE maintenance_bills_backup_', @backup_suffix, ' AS SELECT * FROM maintenance_bills');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of bill line items  
SET @sql = CONCAT('CREATE TABLE bill_line_items_backup_', @backup_suffix, ' AS SELECT * FROM bill_line_items');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of maintenance payments
SET @sql = CONCAT('CREATE TABLE maintenance_payments_backup_', @backup_suffix, ' AS SELECT * FROM maintenance_payments');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of maintenance ledger
SET @sql = CONCAT('CREATE TABLE maintenance_ledger_backup_', @backup_suffix, ' AS SELECT * FROM maintenance_ledger');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of unit advance credits
SET @sql = CONCAT('CREATE TABLE unit_advance_credits_backup_', @backup_suffix, ' AS SELECT * FROM unit_advance_credits');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of suspense entries
SET @sql = CONCAT('CREATE TABLE suspense_entries_backup_', @backup_suffix, ' AS SELECT * FROM suspense_entries');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of suspense audit trail
SET @sql = CONCAT('CREATE TABLE suspense_audit_trail_backup_', @backup_suffix, ' AS SELECT * FROM suspense_audit_trail');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of penalties
SET @sql = CONCAT('CREATE TABLE penalties_backup_', @backup_suffix, ' AS SELECT * FROM penalties');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of opening balances
SET @sql = CONCAT('CREATE TABLE opening_balances_backup_', @backup_suffix, ' AS SELECT * FROM opening_balances');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create backup of receipt sequences
SET @sql = CONCAT('CREATE TABLE receipt_sequences_backup_', @backup_suffix, ' AS SELECT * FROM receipt_sequences');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Show backup summary
SELECT '=== BACKUP SUMMARY ===' as status;
SELECT CONCAT('maintenance_bills_backup_', @backup_suffix) AS table_name, 
       (SELECT COUNT(*) FROM maintenance_bills) AS rows_backed_up
UNION ALL
SELECT CONCAT('maintenance_payments_backup_', @backup_suffix), 
       (SELECT COUNT(*) FROM maintenance_payments)
UNION ALL  
SELECT CONCAT('maintenance_ledger_backup_', @backup_suffix), 
       (SELECT COUNT(*) FROM maintenance_ledger)
UNION ALL
SELECT CONCAT('penalties_backup_', @backup_suffix), 
       (SELECT COUNT(*) FROM penalties)
UNION ALL
SELECT CONCAT('Other backup tables created: bill_line_items, unit_advance_credits, suspense_entries, suspense_audit_trail, opening_balances, receipt_sequences'), 
       NULL;

SELECT CONCAT('BACKUP COMPLETED! Tables created with suffix: _backup_', @backup_suffix) as final_status;
SELECT 'You can now safely run the cleanup script.' as next_step;