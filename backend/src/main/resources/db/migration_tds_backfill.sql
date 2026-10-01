-- ============================================================
-- TDS BACKFILL: Recalculate TDS for existing vouchers
-- ============================================================
-- This script recalculates TDS for vouchers that have a vendor
-- with an active TDS configuration but were created before the
-- vendor category code fix was applied.
--
-- It updates vouchers where:
-- - tds_applicable is FALSE or NULL
-- - vendor has an active TDS configuration
-- - voucher amount exceeds the TDS threshold
-- ============================================================

-- First, let's identify vouchers that should have TDS but don't
-- This query finds vouchers where TDS should have been applied
UPDATE vouchers v
JOIN vendors ven ON ven.vendor_id = v.vendor_id
JOIN vendor_categories vc ON vc.category_id = ven.category_id
JOIN tds_config tc ON tc.vendor_category = vc.code
SET 
    v.tds_applicable = TRUE,
    v.tds_section = tc.tds_section,
    v.tds_rate = tc.tds_rate,
    v.tds_amount = ROUND(v.amount * tc.tds_rate / 100, 2),
    v.net_payable = v.amount - ROUND(v.amount * tc.tds_rate / 100, 2)
WHERE 
    -- Voucher has a vendor with TDS configuration
    v.vendor_id IS NOT NULL
    AND tc.is_active = TRUE
    -- TDS should apply based on threshold
    AND (tc.threshold_amount IS NULL OR v.amount >= tc.threshold_amount)
    -- TDS not currently applied or incorrectly applied
    AND (v.tds_applicable = FALSE OR v.tds_applicable IS NULL OR v.tds_amount IS NULL OR v.tds_amount = 0);

-- Log the count of updated vouchers
SELECT 
    COUNT(*) AS 'TDS Backfill Summary',
    'Vouchers with TDS backfilled' AS Status
FROM vouchers 
WHERE tds_applicable = TRUE 
  AND tds_amount IS NOT NULL 
  AND tds_amount > 0;