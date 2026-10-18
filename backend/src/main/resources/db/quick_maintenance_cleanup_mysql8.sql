-- =============================================================================
-- QUICK MAINTENANCE DATA CLEANUP (MySQL 8 compatible)
-- =============================================================================
-- Fixes the MySQL 8 syntax error in quick_maintenance_cleanup.sql: `rows` is a
-- reserved word in MySQL 8, so `COUNT(*) AS rows` fails. Here the count columns are
-- back-ticked (`row_count`) to avoid any reserved-word clash.
--
-- Deletes ALL maintenance bills, line items, payments, ledger, penalties, advance
-- credits, suspense entries, opening balances, and resets receipt sequences.
-- Preserves units, owners, users/roles, and all charge configuration.
--
-- TAKE A BACKUP FIRST (mysqldump). This is irreversible.
-- =============================================================================

-- Counts BEFORE deletion
SELECT '=== BEFORE CLEANUP ===' AS status;
SELECT 'maintenance_bills'     AS table_name, COUNT(*) AS `row_count` FROM maintenance_bills
UNION ALL SELECT 'bill_line_items',        COUNT(*) FROM bill_line_items
UNION ALL SELECT 'maintenance_payments',   COUNT(*) FROM maintenance_payments
UNION ALL SELECT 'maintenance_ledger',     COUNT(*) FROM maintenance_ledger
UNION ALL SELECT 'unit_advance_credits',   COUNT(*) FROM unit_advance_credits
UNION ALL SELECT 'suspense_entries',       COUNT(*) FROM suspense_entries
UNION ALL SELECT 'suspense_audit_trail',   COUNT(*) FROM suspense_audit_trail
UNION ALL SELECT 'penalties',              COUNT(*) FROM penalties
UNION ALL SELECT 'opening_balances',       COUNT(*) FROM opening_balances
UNION ALL SELECT 'receipt_sequences',      COUNT(*) FROM receipt_sequences;

-- Perform cleanup
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

SET FOREIGN_KEY_CHECKS = 1;
SET SQL_SAFE_UPDATES = 1;

-- Counts AFTER deletion (all should be 0)
SELECT '=== AFTER CLEANUP ===' AS status;
SELECT 'maintenance_bills'     AS table_name, COUNT(*) AS `row_count` FROM maintenance_bills
UNION ALL SELECT 'bill_line_items',        COUNT(*) FROM bill_line_items
UNION ALL SELECT 'maintenance_payments',   COUNT(*) FROM maintenance_payments
UNION ALL SELECT 'maintenance_ledger',     COUNT(*) FROM maintenance_ledger
UNION ALL SELECT 'unit_advance_credits',   COUNT(*) FROM unit_advance_credits
UNION ALL SELECT 'suspense_entries',       COUNT(*) FROM suspense_entries
UNION ALL SELECT 'suspense_audit_trail',   COUNT(*) FROM suspense_audit_trail
UNION ALL SELECT 'penalties',              COUNT(*) FROM penalties
UNION ALL SELECT 'opening_balances',       COUNT(*) FROM opening_balances
UNION ALL SELECT 'receipt_sequences',      COUNT(*) FROM receipt_sequences;

-- Preserved config sanity check (should stay non-zero)
SELECT 'units'  AS kept_table, COUNT(*) AS `row_count` FROM units
UNION ALL SELECT 'owners', COUNT(*) FROM owners
UNION ALL SELECT 'users',  COUNT(*) FROM users;

SELECT 'MAINTENANCE DATA CLEANUP COMPLETED' AS final_status;
