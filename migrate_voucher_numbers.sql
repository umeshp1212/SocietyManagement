-- ============================================================
-- Migration Script: Update voucher numbers to new format
-- Old format: PV-2026-001, RV-2026-001, JV-2026-001, CV-2026-001
-- New format: PPV/CD/2026-27/001
-- ============================================================

-- Preview changes first (run this SELECT to verify before UPDATE)
SELECT 
    voucher_id,
    voucher_number AS old_number,
    CONCAT('PPV/CD/', financial_year, '/', LPAD(
        CAST(SUBSTRING_INDEX(voucher_number, '-', -1) AS UNSIGNED), 3, '0'
    )) AS new_number,
    financial_year
FROM vouchers
WHERE voucher_number LIKE 'PV-%' 
   OR voucher_number LIKE 'RV-%' 
   OR voucher_number LIKE 'JV-%' 
   OR voucher_number LIKE 'CV-%';

-- ============================================================
-- Execute the migration (uncomment below after verifying SELECT above)
-- ============================================================

UPDATE vouchers 
SET voucher_number = CONCAT('PPV/CD/', financial_year, '/', LPAD(
    CAST(SUBSTRING_INDEX(voucher_number, '-', -1) AS UNSIGNED), 3, '0'
))
WHERE voucher_number LIKE 'PV-%' 
   OR voucher_number LIKE 'RV-%' 
   OR voucher_number LIKE 'JV-%' 
   OR voucher_number LIKE 'CV-%';

-- ============================================================
-- Update the voucher sequence counter to continue from latest
-- ============================================================

UPDATE voucher_sequences 
SET last_number = (
    SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(voucher_number, '/', -1) AS UNSIGNED)), 0)
    FROM vouchers 
    WHERE voucher_number LIKE 'PPV/CD/%'
    AND financial_year = voucher_sequences.financial_year
)
WHERE voucher_type = 'PAYMENT';

-- Verify the results
SELECT voucher_id, voucher_number, financial_year, status FROM vouchers ORDER BY voucher_id;
SELECT * FROM voucher_sequences;
