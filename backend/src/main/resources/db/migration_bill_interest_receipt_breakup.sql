-- Migration: Bill breakup columns for the accountant-style layout
-- ============================================================
-- Adds two columns to maintenance_bills so a bill can present:
--   Net Charges
--   + Previous Balance (Principal + carried Interest)   [GROSS, before receipt]
--   - Less: Receipt (paid against the previous bill)     = last_receipt_amount
--   + Interest Balance (this month's new interest)       = new_interest
--   = Gross Total
--
-- These are DISPLAY-breakdown fields only; the stored total_amount is unchanged,
-- so the Gross Total continues to reconcile exactly.
--   carried interest = interest_arrears - new_interest
--
-- The app runs with spring.jpa.hibernate.ddl-auto=update, which auto-adds these
-- on startup. This script documents/seeds them explicitly for production.
-- Compatible with stock MySQL 8.0 and safe to run more than once.
-- ============================================================

-- 1) new_interest (this month's interest on outstanding principal)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maintenance_bills' AND COLUMN_NAME = 'new_interest');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE maintenance_bills ADD COLUMN new_interest DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''This month''''s interest on outstanding principal (Interest Balance)''',
    'SELECT ''new_interest exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) last_receipt_amount (amount received against the previous month's bill)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maintenance_bills' AND COLUMN_NAME = 'last_receipt_amount');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE maintenance_bills ADD COLUMN last_receipt_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''Receipt against previous month''''s bill (Less: Receipt)''',
    'SELECT ''last_receipt_amount exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
