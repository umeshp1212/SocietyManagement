-- ============================================================
-- Resync voucher_sequences to a SHARED, FY-wide number series.
--
-- WHY: Voucher numbers are PPV/CD/{FY}/{NNN} and the PPV/CD prefix is identical
-- for every voucher type, while vouchers.voucher_number is GLOBALLY unique.
-- The old generator kept a separate counter per (type, FY), so the first voucher
-- of a type that had never been used (e.g. first RECEIPT of 2026-27) generated
-- PPV/CD/2026-27/001 and collided with an existing PAYMENT PPV/CD/2026-27/001:
--   "Duplicate entry 'PPV/CD/2026-27/001' for key 'vouchers.voucher_number'".
--
-- The generator now uses one shared series per financial year. This script
-- brings the stored counters in line with that model: every type row for a FY
-- is set to the real maximum number already used across ALL types that year, so
-- the next generated number is (global max + 1) and cannot collide.
--
-- Safe to re-run (idempotent). Back up before running in production.
-- ============================================================

-- 1) INSPECT (optional, read-only): stored counters vs the real max in vouchers.
-- SELECT voucher_type, financial_year, last_number
-- FROM voucher_sequences ORDER BY financial_year, voucher_type;
--
-- SELECT financial_year,
--        MAX(CAST(SUBSTRING_INDEX(voucher_number, '/', -1) AS UNSIGNED)) AS max_used
-- FROM vouchers
-- GROUP BY financial_year;

-- 2) Create any missing per-type sequence rows for FYs that have vouchers.
--    (Keeps the (type, FY) uniqueness; the value is set in step 3.)
INSERT INTO voucher_sequences (voucher_type, financial_year, last_number)
SELECT t.voucher_type, fy.financial_year, 0
FROM (SELECT DISTINCT financial_year FROM vouchers) fy
CROSS JOIN (
    SELECT 'PAYMENT' AS voucher_type UNION ALL
    SELECT 'RECEIPT' UNION ALL
    SELECT 'JOURNAL' UNION ALL
    SELECT 'CONTRA'
) t
LEFT JOIN voucher_sequences vs
       ON vs.voucher_type = t.voucher_type
      AND vs.financial_year = fy.financial_year
WHERE vs.sequence_id IS NULL;

-- 3) Set EVERY type row for a FY to the global max used that year (across all
--    types). GREATEST ensures we never lower a counter below its current value.
UPDATE voucher_sequences vs
JOIN (
    SELECT financial_year,
           MAX(CAST(SUBSTRING_INDEX(voucher_number, '/', -1) AS UNSIGNED)) AS max_used
    FROM vouchers
    GROUP BY financial_year
) m
  ON m.financial_year = vs.financial_year
SET vs.last_number = GREATEST(vs.last_number, m.max_used);

-- 4) VERIFY (optional): all type rows for a FY should now equal that FY's max,
--    and be >= the max number present in vouchers.
-- SELECT voucher_type, financial_year, last_number
-- FROM voucher_sequences ORDER BY financial_year, voucher_type;
