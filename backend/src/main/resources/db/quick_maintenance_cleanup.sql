-- =============================================================================
-- QUICK MAINTENANCE DATA CLEANUP (DIRECT EXECUTION)
-- =============================================================================
-- This is a simplified version of the production cleanup script that can be 
-- executed directly without modifications. Use this when you're certain
-- about the operation and have already taken backups.
-- =============================================================================

-- Show current counts before deletion
SELECT '=== BEFORE CLEANUP ===' as status;
SELECT 'maintenance_bills' AS table_name, COUNT(*) AS rows FROM maintenance_bills
UNION ALL SELECT 'maintenance_payments', COUNT(*) FROM maintenance_payments
UNION ALL SELECT 'maintenance_ledger', COUNT(*) FROM maintenance_ledger
UNION ALL SELECT 'penalties', COUNT(*) FROM penalties
UNION ALL SELECT 'Total Records to Delete', 
    (SELECT COUNT(*) FROM maintenance_bills) + 
    (SELECT COUNT(*) FROM maintenance_payments) + 
    (SELECT COUNT(*) FROM maintenance_ledger) + 
    (SELECT COUNT(*) FROM penalties) +
    (SELECT COUNT(*) FROM bill_line_items) +
    (SELECT COUNT(*) FROM unit_advance_credits) +
    (SELECT COUNT(*) FROM suspense_entries) +
    (SELECT COUNT(*) FROM suspense_audit_trail) +
    (SELECT COUNT(*) FROM opening_balances) +
    (SELECT COUNT(*) FROM receipt_sequences);

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

-- Show results after cleanup
SELECT '=== AFTER CLEANUP ===' as status;
SELECT 'maintenance_bills' AS table_name, COUNT(*) AS rows_remaining FROM maintenance_bills
UNION ALL SELECT 'bill_line_items', COUNT(*) FROM bill_line_items
UNION ALL SELECT 'maintenance_payments', COUNT(*) FROM maintenance_payments
UNION ALL SELECT 'maintenance_ledger', COUNT(*) FROM maintenance_ledger
UNION ALL SELECT 'unit_advance_credits', COUNT(*) FROM unit_advance_credits
UNION ALL SELECT 'suspense_entries', COUNT(*) FROM suspense_entries
UNION ALL SELECT 'suspense_audit_trail', COUNT(*) FROM suspense_audit_trail
UNION ALL SELECT 'penalties', COUNT(*) FROM penalties
UNION ALL SELECT 'opening_balances', COUNT(*) FROM opening_balances
UNION ALL SELECT 'receipt_sequences', COUNT(*) FROM receipt_sequences;

SELECT 'MAINTENANCE DATA CLEANUP COMPLETED SUCCESSFULLY!' as final_status;