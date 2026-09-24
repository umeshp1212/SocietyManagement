-- ============================================================
-- Migration: Vendor TDS Management module
-- Database: MySQL 8.x
-- ============================================================
-- Additive, purely-new schema for the TDS remittance lifecycle and its
-- read model. This script creates:
--
--   1. tds_remittance          (batch grouping one or more deducted-TDS lines)
--   2. tds_remittance_line     (per-voucher link into a batch; UNIQUE voucher_id)
--   3. tds_line_view           (read model backing TdsLineView)
--   4. Supplementary indexes   (fy+status batch listing; vouchers eligibility scan)
--
-- No change is made to the existing `vouchers` / `vendors` schema — the only
-- touch on `vouchers` is the additive composite index idx_vouchers_tds_status_date.
--
-- The application also runs with Hibernate `spring.jpa.hibernate.ddl-auto: update`
-- (see application.yml), so on a live dev instance the two base tables may already
-- have been auto-created from the JPA entities in `com.society.module.tds.entity`
-- (TdsRemittance, TdsRemittanceLine). This script is written to be idempotent so
-- it can be run either way:
--   * CREATE TABLE IF NOT EXISTS      — safe when Hibernate already made the tables
--   * CREATE OR REPLACE VIEW          — always refreshes the view
--   * index creation guarded by a stored procedure that checks information_schema
--
-- Usage (production / manual ops):
--   mysql -u <user> -p <database> < migration_tds_management.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1) tds_remittance — the batch
-- ------------------------------------------------------------
-- One row per real-world remittance batch. A batch is created directly in
-- PAID_TO_ACCOUNTANT (stage 1 recorded at creation) and later advances to
-- PAID_TO_IT_DEPARTMENT (stage 2). DEDUCTED is never persisted here — a
-- deducted-TDS voucher with no line is implicitly DEDUCTED. Audit columns
-- (created_by/created_on/modified_by/modified_on) mirror BaseEntity and the
-- convention used across schema.sql.
CREATE TABLE IF NOT EXISTS tds_remittance (
    tds_remittance_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_reference         VARCHAR(30) NOT NULL COMMENT 'e.g. TDS-2024-0001',
    status                  VARCHAR(30) NOT NULL COMMENT 'PAID_TO_ACCOUNTANT | PAID_TO_IT_DEPARTMENT',
    financial_year          VARCHAR(10) NOT NULL,
    total_amount            DECIMAL(12,2) NOT NULL COMMENT 'HALF_UP scale-2 sum of linked line tds_amount',

    -- Stage 1: society -> accountant
    accountant_name         VARCHAR(100),
    paid_to_accountant_date DATE,
    accountant_reference    VARCHAR(100) COMMENT 'Internal payment/transfer reference',
    paid_to_accountant_by   VARCHAR(100) COMMENT 'Actor who recorded stage 1',

    -- Stage 2: accountant -> IT Department
    challan_number          VARCHAR(50) COMMENT 'IT Dept challan (CIN)',
    paid_to_it_date         DATE,
    paid_to_it_by           VARCHAR(100) COMMENT 'Actor who recorded stage 2',

    remarks                 TEXT,

    -- BaseEntity audit columns
    created_by              VARCHAR(100),
    created_on              DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_by             VARCHAR(100),
    modified_on             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_tds_remittance_batch_reference UNIQUE (batch_reference),
    INDEX idx_tds_remittance_fy_status (financial_year, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 2) tds_remittance_line — the per-voucher link
-- ------------------------------------------------------------
-- Links exactly one source voucher into a batch. The UNIQUE constraint
-- uk_tds_line_voucher on voucher_id is the schema-level guarantee that a
-- voucher's TDS can be remitted at most once (no double-remittance). tds_amount
-- is a snapshot of the deducted amount at batch time, defending the batch total
-- against later voucher edits.
CREATE TABLE IF NOT EXISTS tds_remittance_line (
    tds_remittance_line_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    remittance_id           BIGINT NOT NULL,
    voucher_id              BIGINT NOT NULL,
    tds_amount              DECIMAL(12,2) NOT NULL COMMENT 'Snapshot of voucher tds_amount at batch time',

    -- BaseEntity audit columns
    created_by              VARCHAR(100),
    created_on              DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_by             VARCHAR(100),
    modified_on             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_tds_line_voucher UNIQUE (voucher_id),
    CONSTRAINT fk_tds_line_remittance FOREIGN KEY (remittance_id)
        REFERENCES tds_remittance (tds_remittance_id),
    CONSTRAINT fk_tds_line_voucher FOREIGN KEY (voucher_id)
        REFERENCES vouchers (voucher_id),
    INDEX idx_tds_line_remittance (remittance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 3) Read model: tds_line_view
-- ------------------------------------------------------------
-- One row per eligible deducted-TDS source voucher. A voucher is eligible when
-- it is FINAL, tds_applicable, has a positive tds_amount, and references a
-- vendor. The LEFT JOINs attach the remittance status when the voucher belongs
-- to a remittance line; COALESCE defaults unremitted vouchers to 'DEDUCTED'.
-- CREATE OR REPLACE keeps this idempotent.
CREATE OR REPLACE VIEW tds_line_view AS
SELECT v.voucher_id              AS voucher_id,
       v.voucher_number          AS voucher_number,
       v.voucher_date            AS deduction_date,
       v.financial_year          AS financial_year,
       v.tds_section             AS tds_section,
       v.tds_rate                AS tds_rate,
       v.tds_amount              AS tds_amount,
       v.vendor_id               AS vendor_id,
       ven.vendor_name           AS vendor_name,
       l.remittance_id           AS remittance_id,
       COALESCE(r.status, 'DEDUCTED') AS remittance_status,
       r.paid_to_accountant_date AS paid_to_accountant_date,
       r.paid_to_it_date         AS paid_to_it_date,
       r.challan_number          AS challan_number
FROM vouchers v
JOIN vendors ven                ON ven.vendor_id = v.vendor_id
LEFT JOIN tds_remittance_line l ON l.voucher_id = v.voucher_id
LEFT JOIN tds_remittance r      ON r.tds_remittance_id = l.remittance_id
WHERE v.tds_applicable = TRUE
  AND v.tds_amount IS NOT NULL
  AND v.tds_amount > 0
  AND v.status = 'FINAL'
  AND v.vendor_id IS NOT NULL;

-- ------------------------------------------------------------
-- 4) Supplementary indexes
-- ------------------------------------------------------------
-- MySQL does not support `CREATE INDEX ... IF NOT EXISTS` on all supported
-- server versions, and `CREATE INDEX` errors if the index already exists. These
-- indexes are therefore created inside a stored procedure that first checks
-- information_schema.statistics, making this section idempotent and safe to
-- re-run. (idx_tds_remittance_fy_status is already created inline above when
-- this script creates the table; the guard below covers the case where the
-- table was instead auto-created by Hibernate without that index.)
DROP PROCEDURE IF EXISTS sp_tds_create_indexes;
DELIMITER //
CREATE PROCEDURE sp_tds_create_indexes()
BEGIN
    -- idx_tds_remittance_fy_status: speeds financial-year + status batch listing.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'tds_remittance'
          AND index_name   = 'idx_tds_remittance_fy_status'
    ) THEN
        CREATE INDEX idx_tds_remittance_fy_status
            ON tds_remittance (financial_year, status);
    END IF;

    -- idx_vouchers_tds_status_date: speeds the tds_line_view eligibility scan
    -- (tds_applicable + status filter) and the deduction-date ordering/range.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name   = 'vouchers'
          AND index_name   = 'idx_vouchers_tds_status_date'
    ) THEN
        CREATE INDEX idx_vouchers_tds_status_date
            ON vouchers (tds_applicable, status, voucher_date);
    END IF;
END //
DELIMITER ;

CALL sp_tds_create_indexes();
DROP PROCEDURE IF EXISTS sp_tds_create_indexes;
