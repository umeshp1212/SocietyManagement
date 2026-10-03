-- ============================================================
-- Debug: Check why a newly created voucher is not showing in TDS management
-- ============================================================
-- Run this query to see the current state of your newly created voucher
-- ============================================================

-- Check the voucher details
SELECT 
    v.voucher_id,
    v.voucher_number,
    v.voucher_date,
    v.voucher_type,
    v.category,
    v.vendor_id,
    v.amount,
    v.tds_applicable,
    v.tds_section,
    v.tds_rate,
    v.tds_amount,
    v.net_payable,
    v.status,
    v.financial_year,
    ven.vendor_name,
    vc.code AS vendor_category_code
FROM vouchers v
LEFT JOIN vendors ven ON ven.vendor_id = v.vendor_id
LEFT JOIN vendor_categories vc ON vc.category_id = ven.category_id
WHERE v.voucher_number = 'PV/CD/2026-27/001'  -- Replace with your voucher number
   OR (v.vendor_id = 1 AND v.amount = 85000.00 AND v.voucher_date = '2026-09-25')  -- Replace with your voucher details
ORDER BY v.voucher_id DESC
LIMIT 5;

-- Check if TDS line view includes this voucher
SELECT 
    v.voucher_id,
    v.voucher_number,
    v.amount,
    v.tds_amount,
    v.tds_applicable,
    v.status,
    v.vendor_id
FROM vouchers v
WHERE v.vendor_id IS NOT NULL
  AND v.status = 'FINAL'
  AND v.tds_amount IS NOT NULL
  AND v.tds_amount > 0
  AND v.tds_applicable = TRUE
ORDER BY v.voucher_id DESC
LIMIT 10;

-- Check if the TDS line view exists and what it returns
-- First, check if the view exists
SELECT table_name, table_type 
FROM information_schema.views 
WHERE table_schema = 'society_management' 
  AND table_name = 'tds_line_view';

-- If the view exists, check what it returns for your voucher
-- SELECT * FROM tds_line_view WHERE voucher_number = 'PV/CD/2026-27/001';

-- Check all vouchers with TDS fields populated
SELECT 
    v.voucher_number,
    v.amount,
    v.tds_amount,
    v.tds_rate,
    v.tds_applicable,
    v.status,
    ven.vendor_name,
    ven.category_id
FROM vouchers v
JOIN vendors ven ON ven.vendor_id = v.vendor_id
WHERE v.vendor_id IS NOT NULL
  AND v.status = 'FINAL'
ORDER BY v.voucher_id DESC
LIMIT 20;