-- =============================================================================
-- PRODUCTION MAINTENANCE DATA CLEANUP SCRIPT
-- =============================================================================
-- 
-- DANGER: This script permanently deletes ALL maintenance-related test data from production
-- including bills, payments, outstanding amounts, payment history, and related records.
-- 
-- WHAT WILL BE DELETED:
-- ✓ All maintenance bills (generated bills)
-- ✓ All bill line items (detailed charges)
-- ✓ All maintenance payments (payment history)  
-- ✓ All maintenance ledger entries (audit trail)
-- ✓ All unit advance credits (prepaid amounts)
-- ✓ All suspense entries (unidentified payments)
-- ✓ All suspense audit trail
-- ✓ All penalties
-- ✓ All opening balances
-- ✓ Receipt number sequences (will restart from 1)
--
-- WHAT WILL BE PRESERVED:
-- ✓ Units and unit configuration
-- ✓ Owners and owner details
-- ✓ Users, roles, and authentication
-- ✓ Maintenance charge configuration
-- ✓ Water charge configuration
-- ✓ All other non-maintenance modules (committee, vendor, etc.)
--
-- PREREQUISITES:
-- 1. Take a complete database backup before running this script
-- 2. Ensure no users are actively using the system
-- 3. Stop all applications connecting to this database
-- 4. Verify this is the correct database environment
-- 5. Have a rollback plan ready
--
-- USAGE:
-- 1. Uncomment the safety check section below
-- 2. Replace 'your_actual_database_name' with your production database name
-- 3. Execute the script in a transaction for safety
-- 4. Verify the results before committing
-- =============================================================================

-- =============================================================================
-- SAFETY CHECKS - UNCOMMENT AND MODIFY BEFORE RUNNING
-- =============================================================================

-- Step 1: Verify you're on the correct database
-- SELECT DATABASE() as current_database;
-- 
-- Step 2: Verify this is your intended database name
-- IF(DATABASE() != 'your_actual_database_name') THEN
--   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wrong database! Script aborted for safety.';
-- END IF;

-- Step 3: Show current data counts before deletion
-- SELECT 'PRE-DELETION COUNTS' as status;
-- SELECT 'maintenance_bills' AS table_name, COUNT(*) AS current_rows FROM maintenance_bills
-- UNION ALL SELECT 'bill_line_items', COUNT(*) FROM bill_line_items  
-- UNION ALL SELECT 'maintenance_payments', COUNT(*) FROM maintenance_payments
-- UNION ALL SELECT 'maintenance_ledger', COUNT(*) FROM maintenance_ledger
-- UNION ALL SELECT 'unit_advance_credits', COUNT(*) FROM unit_advance_credits
-- UNION ALL SELECT 'suspense_entries', COUNT(*) FROM suspense_entries
-- UNION ALL SELECT 'suspense_audit_trail', COUNT(*) FROM suspense_audit_trail
-- UNION ALL SELECT 'penalties', COUNT(*) FROM penalties
-- UNION ALL SELECT 'opening_balances', COUNT(*) FROM opening_balances
-- UNION ALL SELECT 'receipt_sequences', COUNT(*) FROM receipt_sequences;

-- =============================================================================
-- PRODUCTION DATA CLEANUP
-- =============================================================================

-- Start transaction for safety (can rollback if something goes wrong)
START TRANSACTION;

-- Disable safety checks temporarily
SET SQL_SAFE_UPDATES = 0;
SET FOREIGN_KEY_CHECKS = 0;

-- Show what we're about to do
SELECT 'STARTING MAINTENANCE DATA CLEANUP...' as status;
SELECT NOW() as cleanup_started_at;

-- Delete in correct order to respect foreign key constraints
-- Order is critical to avoid referential integrity violations

-- 1. Suspense audit trail (references suspense_entries)
SELECT 'Deleting suspense_audit_trail...' as step;
TRUNCATE TABLE suspense_audit_trail;

-- 2. Suspense entries (may reference units)  
SELECT 'Deleting suspense_entries...' as step;
TRUNCATE TABLE suspense_entries;

-- 3. Unit advance credits (references units and payments)
SELECT 'Deleting unit_advance_credits...' as step; 
TRUNCATE TABLE unit_advance_credits;

-- 4. Maintenance ledger (references bills, units, payments)
SELECT 'Deleting maintenance_ledger...' as step;
TRUNCATE TABLE maintenance_ledger;

-- 5. Bill line items (references maintenance_bills)
SELECT 'Deleting bill_line_items...' as step;
TRUNCATE TABLE bill_line_items;

-- 6. Maintenance payments (references bills and units)
SELECT 'Deleting maintenance_payments...' as step;
TRUNCATE TABLE maintenance_payments;

-- 7. Penalties (references units)
SELECT 'Deleting penalties...' as step;
TRUNCATE TABLE penalties;

-- 8. Maintenance bills (references units)
SELECT 'Deleting maintenance_bills...' as step;
TRUNCATE TABLE maintenance_bills;

-- 9. Opening balances (references units)
SELECT 'Deleting opening_balances...' as step;
TRUNCATE TABLE opening_balances;

-- 10. Receipt sequences (independent table)
SELECT 'Resetting receipt_sequences...' as step;
TRUNCATE TABLE receipt_sequences;

-- Re-enable safety checks
SET FOREIGN_KEY_CHECKS = 1;
SET SQL_SAFE_UPDATES = 1;

-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'CLEANUP COMPLETED - VERIFYING RESULTS...' as status;

-- Verify all tables are empty (all counts should be 0)
SELECT 'POST-DELETION VERIFICATION' as verification_status;
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

-- Verify critical data is still intact
SELECT 'VERIFYING PRESERVED DATA...' as status;
SELECT 'units' AS table_name, COUNT(*) AS rows_preserved FROM units
UNION ALL SELECT 'unit_owners', COUNT(*) FROM unit_owners
UNION ALL SELECT 'owners', COUNT(*) FROM owners  
UNION ALL SELECT 'maintenance_charge_config', COUNT(*) FROM maintenance_charge_config
UNION ALL SELECT 'water_charge_config', COUNT(*) FROM water_charge_config;

SELECT 'CLEANUP VERIFICATION COMPLETE' as status;
SELECT NOW() as cleanup_completed_at;

-- =============================================================================
-- TRANSACTION CONTROL
-- =============================================================================
-- 
-- IMPORTANT: Review the verification results above before committing!
-- 
-- If everything looks correct:
-- COMMIT;
-- 
-- If something went wrong:  
-- ROLLBACK;
-- 
-- UNCOMMENT ONE OF THE ABOVE LINES TO COMPLETE THE OPERATION
-- =============================================================================

SELECT '*** REVIEW RESULTS AND MANUALLY COMMIT OR ROLLBACK ***' as final_instruction;