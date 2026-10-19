-- ============================================================
-- Migration: Add "Pay To" / payee name to vouchers
-- ============================================================
-- Scenario: A vendor is registered under their company name and the bill is in
-- that company name, but at payment time the vendor asks the cheque to be made
-- out to a different name (proprietor's personal name, sister concern, or a
-- third party). The pay_to_name column records the actual payee (the name the
-- cheque is made out to) while keeping the vendor relationship (the billed
-- party) intact for the audit trail.
--
-- This field is MANDATORY for new vouchers. For existing rows we backfill a
-- value so nothing is left blank:
--   - use the linked vendor's registered name when the voucher has a vendor
--   - otherwise fall back to the placeholder 'N/A'
--
-- Compatible with stock MySQL 8.0, which does NOT support
-- "ALTER TABLE ... ADD COLUMN IF NOT EXISTS". Instead we check
-- information_schema and add the column only when it's missing, so this
-- script is safe to run more than once.
-- ============================================================

-- 1) Add the column only if it doesn't already exist (idempotent on MySQL 8.0)
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'vouchers'
      AND COLUMN_NAME = 'pay_to_name'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE vouchers ADD COLUMN pay_to_name VARCHAR(200) NULL COMMENT ''Payee / cheque in favour of (name the cheque is made out to)'' AFTER bill_invoice_number',
    'SELECT ''Column pay_to_name already exists; skipping ADD COLUMN'' AS info'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) Backfill existing rows from the linked vendor's registered name
UPDATE vouchers v
    JOIN vendors ven ON ven.vendor_id = v.vendor_id
SET v.pay_to_name = ven.vendor_name
WHERE v.pay_to_name IS NULL OR v.pay_to_name = '';

-- 3) Backfill any remaining rows (vouchers with no vendor) with a placeholder
UPDATE vouchers
SET pay_to_name = 'N/A'
WHERE pay_to_name IS NULL OR pay_to_name = '';

-- 4) OPTIONAL: enforce NOT NULL at the DB level once every row has a value.
-- Run after confirming no blanks remain:
--   SELECT COUNT(*) FROM vouchers WHERE pay_to_name IS NULL OR pay_to_name = '';
-- (should return 0), then run:
--   ALTER TABLE vouchers
--       MODIFY COLUMN pay_to_name VARCHAR(200) NOT NULL
--       COMMENT 'Payee / cheque in favour of (name the cheque is made out to)';
