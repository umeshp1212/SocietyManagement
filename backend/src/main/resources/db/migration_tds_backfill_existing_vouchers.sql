-- ============================================================
-- Migration: Backfill TDS data for existing vouchers
-- Database: MySQL 8.x
-- ============================================================
-- This script identifies vouchers created before TDS module implementation
-- and backfills TDS fields based on vendor category TDS configuration.
--
-- Usage (production):
--   mysql -u <user> -p society_management < migration_tds_backfill_existing_vouchers.sql
-- ============================================================

-- ------------------------------------------------------------
-- Step 1: Update vouchers with TDS data from vendor category config
-- ------------------------------------------------------------
-- This updates vouchers that have:
--   - A vendor with a category that has an active TDS config
--   - Amount >= TDS threshold for that category
--   - tds_applicable is NULL or FALSE (needs backfill)
--
-- The update is conditional - only sets TDS fields if:
--   1. Vendor exists with a category
--   2. Category has an active TDS config
--   3. Amount exceeds the TDS threshold
-- ------------------------------------------------------------
UPDATE vouchers v
JOIN vendors ven ON ven.vendor_id = v.vendor_id
JOIN vendor_categories vc ON vc.category_id = ven.category_id
JOIN tds_config tc ON tc.vendor_category = vc.code
    AND tc.is_active = 1
    AND v.amount >= tc.threshold_amount
SET 
    v.tds_applicable = TRUE,
    v.tds_section = tc.tds_section,
    v.tds_rate = tc.tds_rate,
    v.tds_amount = ROUND(v.amount * tc.tds_rate / 100, 2),
    v.net_payable = v.amount - (v.amount * tc.tds_rate / 100)
WHERE 
    v.tds_applicable IS NULL 
    OR v.tds_applicable = FALSE
    AND v.status = 'FINAL'
    AND v.vendor_id IS NOT NULL
    AND v.amount IS NOT NULL
    AND v.tds_amount IS NULL;

-- ------------------------------------------------------------
-- Step 2: Backfill TDS for vouchers where tds_amount is set but
-- tds_applicable is FALSE/NULL (inconsistent data)
-- ------------------------------------------------------------
-- This handles cases where tds_amount was manually set but 
-- tds_applicable flag wasn't updated
-- ------------------------------------------------------------
UPDATE vouchers v
SET v.tds_applicable = TRUE
WHERE v.tds_amount IS NOT NULL 
    AND v.tds_amount > 0 
    AND (v.tds_applicable IS NULL OR v.tds_applicable = FALSE);

-- ------------------------------------------------------------
-- Step 3: Verify the backfill results
-- ------------------------------------------------------------
-- Run this query to see how many vouchers now have TDS data
-- ------------------------------------------------------------
-- SELECT 
--     COUNT(*) AS total_vouchers,
--     SUM(CASE WHEN tds_applicable = TRUE THEN 1 ELSE 0 END) AS vouchers_with_tds,
--     SUM(CASE WHEN tds_amount IS NOT NULL THEN 1 ELSE 0 END) AS vouchers_with_tds_amount,
--     SUM(CASE WHEN tds_applicable = TRUE AND tds_amount IS NOT NULL AND tds_amount > 0 THEN 1 ELSE 0 END) AS vouchers_ready_for_tds_remittance
-- FROM vouchers 
-- WHERE status = 'FINAL' AND vendor_id IS NOT NULL;

-- ------------------------------------------------------------
-- Step 4: Check specific vouchers (optional debugging)
-- ------------------------------------------------------------
-- SELECT 
--     v.voucher_number,
--     v.voucher_date,
--     v.amount,
--     v.tds_amount,
--     v.tds_applicable,
--     v.tds_section,
--     v.tds_rate,
--     ven.vendor_name,
--     vc.code AS vendor_category
-- FROM vouchers v
-- JOIN vendors ven ON ven.vendor_id = v.vendor_id
-- JOIN vendor_categories vc ON vc.category_id = ven.category_id
-- WHERE v.status = 'FINAL'
--   AND v.vendor_id IS NOT NULL
--   AND (v.tds_applicable = TRUE OR v.tds_amount IS NOT NULL)
-- ORDER BY v.voucher_date DESC
-- LIMIT 20;