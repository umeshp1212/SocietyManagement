-- Clean-slate reset of payment / maintenance-collection / suspense data.
-- Bills themselves are KEPT but reset to unpaid so you can re-test from scratch.
-- Run against the society_management database.

SET FOREIGN_KEY_CHECKS = 0;

-- Collection + audit data
DELETE FROM maintenance_ledger;
DELETE FROM maintenance_payments;
DELETE FROM unit_advance_credits;
DELETE FROM suspense_audit_trail;
DELETE FROM suspense_entries;

-- Receipt counter back to zero so numbering starts at RCP-YYYYMM-00001
DELETE FROM receipt_sequences;

SET FOREIGN_KEY_CHECKS = 1;

-- Reset every bill back to fully-unpaid state
UPDATE maintenance_bills
   SET paid_amount    = 0,
       balance_amount = total_amount,
       status         = 'UNPAID';

-- Verify
SELECT CONCAT('payments=', (SELECT COUNT(*) FROM maintenance_payments),
              ' ledger=', (SELECT COUNT(*) FROM maintenance_ledger),
              ' advance=', (SELECT COUNT(*) FROM unit_advance_credits),
              ' suspense=', (SELECT COUNT(*) FROM suspense_entries),
              ' suspense_audit=', (SELECT COUNT(*) FROM suspense_audit_trail),
              ' seq_rows=', (SELECT COUNT(*) FROM receipt_sequences)) AS result;
