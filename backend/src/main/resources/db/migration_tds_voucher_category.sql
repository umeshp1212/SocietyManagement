-- ============================================================
-- Migration: Add voucher_category to tds_config table
-- Database: MySQL 8.x
-- ============================================================
-- This migration adds a voucher_category column to tds_config
-- while keeping vendor_category for backward compatibility.
-- New TDS configs should use voucher_category.
--
-- Usage (production):
--   mysql -u <user> -p society_management < migration_tds_voucher_category.sql
-- ============================================================

-- Add voucher_category column (nullable for backward compatibility)
ALTER TABLE tds_config 
ADD COLUMN voucher_category VARCHAR(50) NULL COMMENT 'Voucher category code (e.g., SECURITY, HOUSEKEEPING)';

-- Add unique constraint allowing NULL values
-- MySQL allows multiple NULL values in a unique column
ALTER TABLE tds_config 
ADD CONSTRAINT uk_tds_config_voucher_category UNIQUE (voucher_category);

-- Add index for performance
CREATE INDEX idx_tds_config_voucher_category ON tds_config (voucher_category);

-- Backfill existing configs: map vendor_category to voucher_category
-- Since vendor_category codes match voucher_category codes (SECURITY, HOUSEKEEPING, etc.),
-- we can copy them directly
UPDATE tds_config 
SET voucher_category = vendor_category 
WHERE vendor_category IS NOT NULL 
  AND voucher_category IS NULL;

-- Verify the migration
-- SELECT 
--     tds_config_id,
--     vendor_category,
--     voucher_category,
--     tds_section,
--     tds_rate,
--     threshold_amount,
--     is_active
-- FROM tds_config
-- ORDER BY tds_config_id;