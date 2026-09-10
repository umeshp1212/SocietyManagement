-- =============================================================================
-- FLUSH MAINTENANCE / PAYMENT TRANSACTIONAL DATA
-- =============================================================================
-- Wipes all maintenance bills, payments, ledger, advance credits, suspense, penalties,
-- opening balances, and resets the receipt-number counter — so you can start clean.
--
-- KEEPS (does NOT touch): units, owners, users/roles, and the maintenance config tables
--   (maintenance_charge_config, water_charge_config).
--
-- USE ON A DEV/TEST DATABASE. This permanently deletes financial records.
--
-- Uses TRUNCATE (not DELETE) so it is not blocked by MySQL Workbench "safe update mode",
-- and it also resets AUTO_INCREMENT automatically. SET SQL_SAFE_UPDATES = 0 is included
-- as well in case you switch any line back to DELETE.
-- =============================================================================

-- Optional: target the right schema explicitly.
-- USE society_management;

SET SQL_SAFE_UPDATES = 0;
SET FOREIGN_KEY_CHECKS = 0;

-- TRUNCATE empties the table AND resets AUTO_INCREMENT to 1.
TRUNCATE TABLE suspense_audit_trail;
TRUNCATE TABLE suspense_entries;
TRUNCATE TABLE unit_advance_credits;
TRUNCATE TABLE maintenance_ledger;
TRUNCATE TABLE bill_line_items;
TRUNCATE TABLE maintenance_payments;
TRUNCATE TABLE penalties;
TRUNCATE TABLE maintenance_bills;
TRUNCATE TABLE opening_balances;

-- Reset the receipt-number counter so numbering restarts at RCP-YYYYMM-00001.
TRUNCATE TABLE receipt_sequences;

SET FOREIGN_KEY_CHECKS = 1;
SET SQL_SAFE_UPDATES = 1;

-- Verify everything is empty (all counts should be 0):
SELECT 'maintenance_bills' AS table_name, COUNT(*) AS rows_left FROM maintenance_bills
UNION ALL SELECT 'bill_line_items', COUNT(*) FROM bill_line_items
UNION ALL SELECT 'maintenance_payments', COUNT(*) FROM maintenance_payments
UNION ALL SELECT 'maintenance_ledger', COUNT(*) FROM maintenance_ledger
UNION ALL SELECT 'unit_advance_credits', COUNT(*) FROM unit_advance_credits
UNION ALL SELECT 'suspense_entries', COUNT(*) FROM suspense_entries
UNION ALL SELECT 'suspense_audit_trail', COUNT(*) FROM suspense_audit_trail
UNION ALL SELECT 'penalties', COUNT(*) FROM penalties
UNION ALL SELECT 'opening_balances', COUNT(*) FROM opening_balances
UNION ALL SELECT 'receipt_sequences', COUNT(*) FROM receipt_sequences;
