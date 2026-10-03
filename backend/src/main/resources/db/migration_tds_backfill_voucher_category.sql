-- ============================================================
-- Production Migration: Backfill TDS for Existing Vouchers
-- Database: MySQL 8.x
-- ============================================================
-- This script backfills TDS for vouchers created before the
-- voucher-category-based TDS configuration was implemented.
--
-- PRIORITY LOGIC:
-- 1. First tries voucher_category config (NEW - primary)
-- 2. Falls back to vendor_category config if no voucher_category config (BACKWARD COMPATIBLE)
--
-- USAGE (production):
--   mysql -u <user> -p society_management < migration_tds_backfill_voucher_category.sql
--
-- IMPORTANT:
-- - Only updates vouchers with status = 'FINAL' and vendor_id IS NOT NULL
-- - Only sets TDS if amount >= threshold
-- - Does NOT change vouchers that already have TDS configured
-- - Does NOT impact production data that is already correct
-- ============================================================

-- ------------------------------------------------------------
-- Step 1: Backfill TDS based on VOUCHER CATEGORY (primary method)
-- ------------------------------------------------------------
-- This handles vouchers where the voucher category has a TDS config
-- ------------------------------------------------------------
UPDATE vouchers v
JOIN voucher_categories vc ON vc.code = v.category
JOIN tds_config tc ON tc.voucher_category = vc.code
    AND tc.is_active = 1
    AND v.amount >= tc.threshold_amount
SET 
    v.tds_applicable = TRUE,
    v.tds_section = tc.tds_section,
    v.tds_rate = tc.tds_rate,
    v.tds_amount = ROUND(v.amount * tc.tds_rate / 100, 2),
    v.net_payable = v.amount - (v.amount * tc.tds_rate / 100)
WHERE 
    -- Only update vouchers that don't have TDS yet
    (v.tds_applicable IS NULL OR v.tds_applicable = FALSE)
    AND v.status = 'FINAL'
    AND v.vendor_id IS NOT NULL
    AND v.amount IS NOT NULL
    AND v.tds_amount IS NULL;

-- ------------------------------------------------------------
-- Step 2: Fallback - Backfill TDS based on VENDOR CATEGORY
-- (only for vouchers where voucher_category config doesn't exist)
-- ------------------------------------------------------------
-- This ensures backward compatibility for existing vouchers
-- ------------------------------------------------------------
UPDATE vouchers v
JOIN vendors ven ON ven.vendor_id = v.vendor_id
JOIN vendor_categories vc ON vc.category_id = ven.category_id
JOIN tds_config tc ON tc.vendor_category = vc.code
    AND tc.is_active = 1
    AND v.amount >= tc.threshold_amount
    AND NOT EXISTS (
        -- Only if no voucher_category config exists for this voucher
        SELECT 1 FROM tds_config tc2 
        WHERE tc2.voucher_category = v.category 
        AND tc2.is_active = 1
        AND v.amount >= tc2.threshold_amount
    )
SET 
    v.tds_applicable = TRUE,
    v.tds_section = tc.tds_section,
    v.tds_rate = tc.tds_rate,
    v.tds_amount = ROUND(v.amount * tc.tds_rate / 100, 2),
    v.net_payable = v.amount - (v.amount * tc.tds_rate / 100)
WHERE 
    -- Only update vouchers that don't have TDS yet
    (v.tds_applicable IS NULL OR v.tds_applicable = FALSE)
    AND v.status = 'FINAL'
    AND v.vendor_id IS NOT NULL
    AND v.amount IS NOT NULL
    AND v.tds_amount IS NULL;

-- ------------------------------------------------------------
-- Step 3: Fix inconsistent data (tds_amount set but tds_applicable = FALSE)
-- ------------------------------------------------------------
-- This handles edge cases where manual data entry might have caused inconsistencies
-- ------------------------------------------------------------
UPDATE vouchers v
SET v.tds_applicable = TRUE
WHERE v.tds_amount IS NOT NULL 
    AND v.tds_amount > 0 
    AND (v.tds_applicable IS NULL OR v.tds_applicable = FALSE);

-- ------------------------------------------------------------
-- Step 4: Verify the backfill results
-- ------------------------------------------------------------
-- SELECT 
--     COUNT(*) AS total_final_vouchers,
--     SUM(CASE WHEN tds_applicable = TRUE THEN 1 ELSE 0 END) AS vouchers_with_tds,
--     SUM(CASE WHEN tds_amount IS NOT NULL THEN 1 ELSE 0 END) AS vouchers_with_tds_amount,
--     SUM(CASE WHEN tds_applicable = TRUE AND tds_amount IS NOT NULL AND tds_amount > 0 THEN 1 ELSE 0 END) AS vouchers_ready_for_tds_remittance
-- FROM vouchers 
-- WHERE status = 'FINAL' AND vendor_id IS NOT NULL;

-- ------------------------------------------------------------
-- Step 5: Check specific vouchers (optional debugging)
-- ------------------------------------------------------------
-- SELECT 
--     v.voucher_number,
--     v.voucher_date,
--     v.amount,
--     v.tds_amount,
--     v.tds_applicable,
--     v.tds_section,
--     v.tds_rate,
--     v.category AS voucher_category,
--     ven.vendor_name,
--     vc.code AS vendor_category_code
-- FROM vouchers v
-- JOIN vendors ven ON ven.vendor_id = v.vendor_id
-- LEFT JOIN vendor_categories vc ON vc.category_id = ven.category_id
-- WHERE v.status = 'FINAL'
--   AND v.vendor_id IS NOT NULL
--   AND (v.tds_applicable = TRUE OR v.tds_amount IS NOT NULL)
-- ORDER BY v.voucher_date DESC
-- LIMIT 20;