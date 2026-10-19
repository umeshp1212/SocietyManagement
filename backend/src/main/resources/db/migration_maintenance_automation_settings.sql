-- ============================================================
-- Migration: Maintenance automation + bill settings on society_settings
-- ============================================================
-- Adds admin-configurable settings that drive:
--   * late_fee_amount           - flat late fee applied once on arrears (0 = off)
--   * bill_due_day              - due day of month; 0 = last day of the month
--   * auto_bill_generation_enabled + bill_generation_day
--                               - scheduled monthly bill generation + its start/stop switch
--   * auto_reminder_enabled + reminder_day_of_month
--                               - scheduled email payment reminders + its start/stop switch
--
-- The application runs with spring.jpa.hibernate.ddl-auto=update, which also
-- auto-adds these columns on startup. This script is provided so the change is
-- documented and can be applied/seeded explicitly in production (e.g. before a
-- blue/green deploy). Compatible with stock MySQL 8.0 (no ADD COLUMN IF NOT
-- EXISTS) and safe to run more than once.
-- ============================================================

-- Helper pattern: add each column only if missing (idempotent on MySQL 8.0).

-- 1) late_fee_amount
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'late_fee_amount');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN late_fee_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''Flat late fee once on arrears; 0 = disabled''',
    'SELECT ''late_fee_amount exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) bill_due_day  (0 = last day of month)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'bill_due_day');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN bill_due_day INT NOT NULL DEFAULT 0 COMMENT ''Due day of month; 0 = last day''',
    'SELECT ''bill_due_day exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) auto_bill_generation_enabled
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'auto_bill_generation_enabled');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN auto_bill_generation_enabled BIT(1) NOT NULL DEFAULT b''0'' COMMENT ''Scheduled bill generation start/stop''',
    'SELECT ''auto_bill_generation_enabled exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) bill_generation_day
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'bill_generation_day');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN bill_generation_day INT NOT NULL DEFAULT 1 COMMENT ''Day of month to auto-generate bills''',
    'SELECT ''bill_generation_day exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) auto_reminder_enabled
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'auto_reminder_enabled');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN auto_reminder_enabled BIT(1) NOT NULL DEFAULT b''0'' COMMENT ''Scheduled email payment reminders start/stop''',
    'SELECT ''auto_reminder_enabled exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) reminder_day_of_month
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'society_settings' AND COLUMN_NAME = 'reminder_day_of_month');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE society_settings ADD COLUMN reminder_day_of_month INT NOT NULL DEFAULT 5 COMMENT ''Day of month to send reminders''',
    'SELECT ''reminder_day_of_month exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Note: both automation switches default to OFF (b'0') so nothing runs
-- automatically until an admin explicitly starts it from the portal.
