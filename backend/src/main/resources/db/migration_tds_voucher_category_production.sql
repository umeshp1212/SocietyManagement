-- ============================================================
-- Production Migration: TDS Configuration by Voucher Category
-- Database: MySQL 8.x
-- ============================================================
-- This script implements the NEW TDS logic where TDS is configured
-- by VOUCHER CATEGORY (not vendor category). 
--
-- CHANGES MADE:
-- 1. Adds voucher_category column to tds_config table
-- 2. Backfills existing TDS configs to map vendor_category → voucher_category
-- 3. Ensures backward compatibility - existing data NOT impacted
--
-- USAGE (production):
--   mysql -u <user> -p society_management < migration_tds_voucher_category_production.sql
--
-- BACKWARD COMPATIBILITY:
-- - Old vendor_category column is kept and remains populated
-- - TDS lookup now prioritizes voucher_category, falls back to vendor_category
-- - Existing vouchers with TDS remain unchanged
-- - New vouchers use voucher category for TDS calculation
-- ============================================================

-- ------------------------------------------------------------
-- Step 1: Add voucher_category column (nullable for backward compatibility)
-- ------------------------------------------------------------
ALTER TABLE tds_config 
ADD COLUMN voucher_category VARCHAR(50) NULL COMMENT 'Voucher category code (e.g., SECURITY, HOUSEKEEPING) - PRIMARY METHOD';

-- ------------------------------------------------------------
-- Step 2: Add unique constraint on voucher_category (allows NULLs)
-- ------------------------------------------------------------
ALTER TABLE tds_config 
ADD CONSTRAINT uk_tds_config_voucher_category UNIQUE (voucher_category);

-- ------------------------------------------------------------
-- Step 3: Add index for performance
-- ------------------------------------------------------------
CREATE INDEX idx_tds_config_voucher_category ON tds_config (voucher_category);

-- ------------------------------------------------------------
-- Step 4: Backfill existing configs - copy vendor_category to voucher_category
-- Since vendor_category codes match voucher_category codes (SECURITY, HOUSEKEEPING, etc.),
-- this ensures all existing TDS configs work with the new logic
-- ------------------------------------------------------------
UPDATE tds_config 
SET voucher_category = vendor_category 
WHERE voucher_category IS NULL 
  AND vendor_category IS NOT NULL;

-- ------------------------------------------------------------
-- Step 5: Verify the migration
-- ------------------------------------------------------------
-- SELECT 
--     tds_config_id,
--     vendor_category AS 'vendor_category (deprecated)',
--     voucher_category AS 'voucher_category (primary)',
--     tds_section,
--     tds_rate,
--     threshold_amount,
--     description,
--     is_active
-- FROM tds_config
-- ORDER BY tds_config_id;

-- ------------------------------------------------------------
-- Step 6: After application restart, verify new vouchers use voucher category
-- ------------------------------------------------------------
-- Create a test voucher with voucher category 'SECURITY' and check if TDS is calculated
-- based on the voucher_category config, not vendor_category