-- ============================================================
-- Fix: allow NULL tds_config category codes + normalize blanks to NULL
-- Database: MySQL 8.x
-- ============================================================
-- Problem (two layers)
--   1. tds_config.vendor_category was created NOT NULL by an older entity version.
--      Hibernate `ddl-auto: update` adds columns but never relaxes an existing
--      NOT NULL column, so prod still has vendor_category NOT NULL even though the
--      current JPA entity declares it nullable.
--   2. The unique constraints on vendor_category and voucher_category treat an empty
--      string '' as a real, collidable value, whereas MySQL allows NULL to repeat.
--      With a NOT NULL column, every row lacking a vendor category is forced to
--      store '', and only ONE row can hold ''. So the first blank-category insert
--      succeeds and the next fails with:
--        Duplicate entry '' for key 'tds_config.UK_...'
--
--   The application (TdsConfigService) now normalizes blank category codes to NULL
--   on create/update, but that only works once the column ACCEPTS NULL. This script
--   relaxes both category columns to NULL and repairs the pre-existing '' rows.
--
-- Safety
--   - Converts ONLY empty/whitespace-only strings to NULL; real codes are untouched.
--   - Idempotent: re-running MODIFY/UPDATE changes nothing once already applied.
--
-- Usage (production):
--   mysql -u <user> -p society_management < migration_tds_config_blank_category_to_null.sql
-- ============================================================

-- ------------------------------------------------------------
-- Step 1: Relax both category columns to allow NULL.
-- (length/comment kept consistent with the JPA entity + prior migrations.)
-- ------------------------------------------------------------
ALTER TABLE tds_config
    MODIFY COLUMN vendor_category VARCHAR(50) NULL
        COMMENT 'Vendor category code (deprecated; kept for backward compatibility)';

ALTER TABLE tds_config
    MODIFY COLUMN voucher_category VARCHAR(50) NULL
        COMMENT 'Voucher category code (e.g., SECURITY, HOUSEKEEPING) - PRIMARY METHOD';

-- Inspect what will change (optional; run before the UPDATEs)
-- SELECT tds_config_id, vendor_category, voucher_category, is_active
-- FROM tds_config
-- WHERE TRIM(COALESCE(vendor_category, '')) = ''
--    OR TRIM(COALESCE(voucher_category, '')) = '';

-- ------------------------------------------------------------
-- Step 2: Convert existing blank ('' / whitespace) categories to NULL.
-- MySQL Workbench "safe update mode" rejects an UPDATE whose WHERE clause does not
-- filter on a key column (error 1175). We disable it for this session only, run the
-- fix, then restore the previous setting. (Running via the mysql CLI is unaffected.)
-- ------------------------------------------------------------
SET @old_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

UPDATE tds_config
SET vendor_category = NULL
WHERE vendor_category IS NOT NULL
  AND TRIM(vendor_category) = '';

UPDATE tds_config
SET voucher_category = NULL
WHERE voucher_category IS NOT NULL
  AND TRIM(voucher_category) = '';

SET SQL_SAFE_UPDATES = @old_safe_updates;

-- Verify (optional)
-- SELECT tds_config_id, vendor_category, voucher_category, is_active
-- FROM tds_config
-- ORDER BY tds_config_id;
