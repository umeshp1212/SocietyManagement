-- Migration: Support FLAT_ONLY / SHOP_ONLY in maintenance_charge_config.applicable_to
-- ============================================================
-- Business change: "Applicable To" for a maintenance charge (e.g. Welfare Fund)
-- can now target only Flat units or only Shop units, in addition to the existing
-- ALL / PARKING / TWO_WHEELER / FOUR_WHEELER / RENTED / OWNER_OCCUPIED options.
--
-- Why this migration is needed:
-- Hibernate's MySQL dialect originally created `applicable_to` as a native
-- ENUM(...) column listing only the old constants. spring.jpa.hibernate.ddl-auto=update
-- ADDS new columns/tables but does NOT widen an existing ENUM, so writing the new
-- value fails at runtime with "Data truncated for column 'applicable_to'".
--
-- Fix: convert the column to a plain VARCHAR(30), matching the entity's
-- @Column(length = 30). This holds every current and future ApplicableTo
-- constant (longest today is OWNER_OCCUPIED = 14 chars) and avoids the ENUM
-- rigidity for good. @Enumerated(EnumType.STRING) reads/writes the name as text,
-- so no data conversion is required for existing rows.
--
-- Compatible with stock MySQL 8.0 and safe to run more than once (idempotent).
-- ============================================================

-- Convert applicable_to to VARCHAR(30) only if it is not already a varchar.
SET @is_varchar := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'maintenance_charge_config'
      AND COLUMN_NAME = 'applicable_to'
      AND DATA_TYPE = 'varchar');
SET @ddl := IF(@is_varchar = 0,
    'ALTER TABLE maintenance_charge_config MODIFY COLUMN applicable_to VARCHAR(30) NOT NULL DEFAULT ''ALL''',
    'SELECT ''applicable_to already VARCHAR; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
