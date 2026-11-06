-- Migration: Late fee as a PERCENTAGE of outstanding principal
-- ============================================================
-- Replaces the old flat `late_fee_amount` with `late_fee_percent`.
--
-- Business change: the late fee is now a configurable percentage of a unit's
-- OUTSTANDING PRINCIPAL, charged each month the unit carries arrears (i.e. it
-- defaulted past a previous bill's due date). This single percentage also
-- replaces the previously hardcoded 1%/month interest-on-arrears, so there is
-- exactly one penalty on the outstanding principal (no double-charging).
--
-- The application runs with spring.jpa.hibernate.ddl-auto=update, which will
-- auto-add `late_fee_percent` on startup. This script documents the change and
-- lets it be applied/seeded explicitly in production. Compatible with stock
-- MySQL 8.0 (no ADD/DROP COLUMN IF [NOT] EXISTS) and safe to run more than once.
-- ============================================================

-- 1) Add late_fee_percent (percentage; e.g. 1.00 = 1%). 0 = disabled.
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'late_fee_percent');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN late_fee_percent DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT ''Late fee as % of outstanding principal; 0 = disabled''',
    'SELECT ''late_fee_percent exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) Drop the obsolete flat late_fee_amount column, if it still exists.
--    (Safe to leave if you prefer to keep history; the application no longer reads it.)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'late_fee_amount');
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE society_settings DROP COLUMN late_fee_amount',
    'SELECT ''late_fee_amount already removed; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
