-- Migration: Unit Tags + tag-scoped maintenance charges
-- ============================================================
-- Adds the ability to bill an arbitrary, admin-defined group of units (e.g. owners
-- who installed a solar panel, EV-charger users, terrace-garden users) without any
-- code change. Two new tables hold the tags and their unit assignments, and a new
-- nullable column on maintenance_charge_config points a charge at one tag.
--
-- Hibernate runs with spring.jpa.hibernate.ddl-auto=update, so on startup it will:
--   * CREATE the new tables `unit_tags` and `unit_tag_assignments`, and
--   * ADD the nullable column `required_tag_id` to `maintenance_charge_config`.
-- ddl-auto=update reliably ADDS new tables/columns (it only struggles with MODIFYing
-- existing columns). This script documents and makes those additions explicit for
-- environments that apply schema changes manually. Written for stock MySQL 8.0 and
-- safe to run repeatedly (idempotent).
-- ============================================================

-- 1) unit_tags: the admin-defined labels.
CREATE TABLE IF NOT EXISTS unit_tags (
    unit_tag_id  BIGINT       NOT NULL AUTO_INCREMENT,
    tag_code     VARCHAR(50)  NOT NULL,
    tag_name     VARCHAR(100) NOT NULL,
    description  VARCHAR(255) NULL,
    is_active    BIT(1)       NOT NULL DEFAULT b'1',
    created_by   VARCHAR(255) NULL,
    created_on   DATETIME(6)  NULL,
    modified_by  VARCHAR(255) NULL,
    modified_on  DATETIME(6)  NULL,
    PRIMARY KEY (unit_tag_id),
    UNIQUE KEY uk_unit_tag_code (tag_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) unit_tag_assignments: which units carry which tag (unique per unit+tag).
CREATE TABLE IF NOT EXISTS unit_tag_assignments (
    assignment_id BIGINT      NOT NULL AUTO_INCREMENT,
    unit_id       BIGINT      NOT NULL,
    unit_tag_id   BIGINT      NOT NULL,
    created_by    VARCHAR(255) NULL,
    created_on    DATETIME(6)  NULL,
    modified_by   VARCHAR(255) NULL,
    modified_on   DATETIME(6)  NULL,
    PRIMARY KEY (assignment_id),
    UNIQUE KEY uk_unit_tag (unit_id, unit_tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) maintenance_charge_config.required_tag_id: optional FK-by-id to a unit_tag.
--    Added only if it does not already exist (ddl-auto may have added it already).
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'maintenance_charge_config'
      AND COLUMN_NAME = 'required_tag_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE maintenance_charge_config ADD COLUMN required_tag_id BIGINT NULL COMMENT ''Optional unit_tag this charge is restricted to; NULL = no tag restriction''',
    'SELECT ''required_tag_id exists; skip'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
