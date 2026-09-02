-- ============================================================
-- Society Management Database Schema
-- Database: MySQL 8.x
-- Phase 1: Owner, Vendor, Rented Flat, Voucher Management
-- ============================================================

CREATE DATABASE IF NOT EXISTS society_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE society_management;

-- Ensure the database default collation is utf8mb4_0900_ai_ci even if the database
-- already existed (e.g. created earlier by createDatabaseIfNotExist with a different
-- server default). New tables created by Hibernate (ddl-auto=update) inherit this
-- default, so pinning it here keeps every table on the same collation and prevents
-- "Illegal mix of collations" errors when joining/comparing string columns.
ALTER DATABASE society_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- MODULE 1: OWNER MANAGEMENT
-- ============================================================

-- Owner Master (permanent - never deleted, only updated/transferred)
CREATE TABLE owners (
    owner_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    alternate_number VARCHAR(15),
    email VARCHAR(100),
    aadhar_number VARCHAR(255) COMMENT 'Encrypted',
    pan_number VARCHAR(20),
    permanent_address TEXT,
    occupation VARCHAR(100),
    photo_path VARCHAR(500),
    emergency_contact_name VARCHAR(150),
    emergency_contact_phone VARCHAR(15),
    status ENUM('ACTIVE', 'TRANSFERRED') NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(100),
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100),
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_owner_status (status),
    INDEX idx_owner_name (full_name),
    INDEX idx_owner_contact (contact_number)
) ENGINE=InnoDB;

-- Unit Master (flats and shops)
CREATE TABLE units (
    unit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_number VARCHAR(20) NOT NULL UNIQUE,
    wing VARCHAR(10),
    floor VARCHAR(10),
    unit_type ENUM('FLAT', 'SHOP') NOT NULL,
    area_sqft DECIMAL(10,2),
    monthly_maintenance_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    occupancy_status ENUM('SELF_OCCUPIED', 'RENTED', 'VACANT') NOT NULL DEFAULT 'VACANT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(100),
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100),
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_unit_number (unit_number),
    INDEX idx_unit_wing (wing),
    INDEX idx_unit_type (unit_type),
    INDEX idx_unit_occupancy (occupancy_status)
) ENGINE=InnoDB;

-- Unit Owners (supports 1 to 4 co-owners per unit)
CREATE TABLE unit_owners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    ownership_percentage DECIMAL(5,2) DEFAULT 100.00,
    added_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(100),

    CONSTRAINT fk_unit_owner_unit FOREIGN KEY (unit_id) REFERENCES units(unit_id),
    CONSTRAINT fk_unit_owner_owner FOREIGN KEY (owner_id) REFERENCES owners(owner_id),
    UNIQUE KEY uk_unit_owner (unit_id, owner_id),
    INDEX idx_uo_unit (unit_id),
    INDEX idx_uo_owner (owner_id)
) ENGINE=InnoDB;

-- Ownership History (tracks all transfers, preserves full chain)
CREATE TABLE ownership_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    ownership_start_date DATE NOT NULL,
    ownership_end_date DATE COMMENT 'NULL means current owner',
    transfer_type ENUM('PURCHASE', 'INHERITANCE', 'GIFT', 'COURT_ORDER') NOT NULL,
    transfer_document_path VARCHAR(500),
    remarks TEXT,
    recorded_by VARCHAR(100),
    recorded_on DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_history_unit FOREIGN KEY (unit_id) REFERENCES units(unit_id),
    CONSTRAINT fk_history_owner FOREIGN KEY (owner_id) REFERENCES owners(owner_id),
    INDEX idx_history_unit (unit_id),
    INDEX idx_history_owner (owner_id),
    INDEX idx_history_dates (ownership_start_date, ownership_end_date)
) ENGINE=InnoDB;

-- ============================================================
-- MODULE 2: VENDOR MANAGEMENT
-- ============================================================

-- Vendor Master
CREATE TABLE vendors (
    vendor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_name VARCHAR(200) NOT NULL,
    category ENUM('SECURITY','HOUSEKEEPING','GARDENING','LIFT_MAINTENANCE','PLUMBING',
                  'ELECTRICAL','PEST_CONTROL','FIRE_SAFETY','CCTV_INTERCOM',
                  'WATER_TANK_CLEANING','PAINTING_CIVIL','LEGAL_AUDIT','SOFTWARE_IT','OTHER') NOT NULL,
    contact_person VARCHAR(150),
    phone VARCHAR(15) NOT NULL,
    email VARCHAR(100),
    address TEXT,
    pan_number VARCHAR(20),
    gst_number VARCHAR(20),
    bank_account_number VARCHAR(30),
    bank_ifsc VARCHAR(15),
    bank_name VARCHAR(100),
    agreement_start_date DATE,
    agreement_end_date DATE,
    contracted_amount DECIMAL(12,2),
    payment_frequency ENUM('MONTHLY','QUARTERLY','HALF_YEARLY','ANNUAL','ONE_TIME') DEFAULT 'MONTHLY',
    status ENUM('ACTIVE', 'INACTIVE', 'BLACKLISTED') NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(100),
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100),
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_vendor_name (vendor_name),
    INDEX idx_vendor_category (category),
    INDEX idx_vendor_status (status),
    INDEX idx_vendor_agreement_end (agreement_end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Vendor Documents
CREATE TABLE vendor_documents (
    document_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    document_name VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) COMMENT 'CONTRACT, INVOICE, ID_PROOF, OTHER',
    file_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(100),
    uploaded_on DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_doc FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id),
    INDEX idx_vendor_doc_vendor (vendor_id)
) ENGINE=InnoDB;

-- ============================================================
-- MODULE 3: RENTED FLAT (TENANT) MANAGEMENT
-- ============================================================

-- Tenant Master
CREATE TABLE tenants (
    tenant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    tenant_name VARCHAR(150) NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    email VARCHAR(100),
    aadhar_number VARCHAR(255) COMMENT 'Encrypted',
    pan_number VARCHAR(20),
    permanent_address TEXT,
    photo_path VARCHAR(500),
    rent_start_date DATE NOT NULL,
    rent_end_date DATE,
    monthly_rent_amount DECIMAL(10,2),
    security_deposit DECIMAL(10,2),
    agreement_document_path VARCHAR(500),
    police_verification_status ENUM('NOT_INITIATED','SUBMITTED','VERIFIED','REJECTED','EXPIRED')
        NOT NULL DEFAULT 'NOT_INITIATED',
    police_verification_document_path VARCHAR(500),
    noc_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    noc_document_path VARCHAR(500),
    noc_approved_by VARCHAR(100),
    noc_approved_on DATETIME,
    status ENUM('PENDING_APPROVAL','ACTIVE','NOTICE_PERIOD','VACATED','REJECTED') NOT NULL DEFAULT 'ACTIVE',
    move_out_date DATE,
    move_out_reason VARCHAR(500),
    created_by VARCHAR(100),
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100),
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_tenant_unit FOREIGN KEY (unit_id) REFERENCES units(unit_id),
    INDEX idx_tenant_unit (unit_id),
    INDEX idx_tenant_status (status),
    INDEX idx_tenant_noc (noc_status),
    INDEX idx_tenant_police (police_verification_status),
    INDEX idx_tenant_rent_end (rent_end_date)
) ENGINE=InnoDB;

-- Tenant Family Members
CREATE TABLE tenant_family_members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_name VARCHAR(150) NOT NULL,
    age INT,
    relation VARCHAR(50) NOT NULL,
    aadhar_number VARCHAR(255) COMMENT 'Encrypted, optional',
    contact_number VARCHAR(15),

    CONSTRAINT fk_family_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_family_tenant (tenant_id)
) ENGINE=InnoDB;

-- Tenant Vehicles
CREATE TABLE tenant_vehicles (
    vehicle_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL COMMENT 'Two Wheeler, Four Wheeler, etc.',
    vehicle_number VARCHAR(20) NOT NULL,
    parking_slot VARCHAR(20),

    CONSTRAINT fk_vehicle_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_vehicle_tenant (tenant_id)
) ENGINE=InnoDB;

-- Tenant Documents
CREATE TABLE tenant_documents (
    document_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    document_name VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) NOT NULL COMMENT 'AGREEMENT, AADHAR, PHOTO, POLICE_VERIFICATION, NOC, ADDRESS_PROOF, OTHER',
    file_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(100),
    uploaded_on DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tenant_doc FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_tenant_doc (tenant_id)
) ENGINE=InnoDB;

-- ============================================================
-- MODULE 4: VOUCHER MANAGEMENT
-- ============================================================

-- Voucher Master
CREATE TABLE vouchers (
    voucher_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_number VARCHAR(20) NOT NULL UNIQUE COMMENT 'Auto-generated: PV-2026-001, RV-2026-001',
    voucher_date DATE NOT NULL,
    voucher_type ENUM('PAYMENT','RECEIPT','JOURNAL','CONTRA') NOT NULL,
    category ENUM('SECURITY','HOUSEKEEPING','ELECTRICITY_COMMON','WATER','LIFT_MAINTENANCE',
                  'GARDEN','REPAIRS_MAINTENANCE','PEST_CONTROL','LEGAL_PROFESSIONAL',
                  'STATIONERY_PRINTING','EVENTS_CELEBRATIONS','INSURANCE','SINKING_FUND',
                  'BANK_CHARGES','MAINTENANCE_INCOME','INTEREST_INCOME','PENALTY_INCOME',
                  'MISCELLANEOUS') NOT NULL,
    vendor_id BIGINT COMMENT 'Nullable - not all vouchers are vendor payments',
    description TEXT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_mode ENUM('CASH','CHEQUE','UPI','NEFT','RTGS','IMPS','BANK_TRANSFER','ONLINE'),
    reference_number VARCHAR(100) COMMENT 'Cheque no, UTR, Transaction ID',
    bill_invoice_number VARCHAR(100),
    bill_date DATE,
    status ENUM('DRAFT','FINAL','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    cancellation_reason TEXT,
    cancelled_by VARCHAR(100),
    cancelled_on DATETIME,
    financial_year VARCHAR(10) NOT NULL COMMENT 'e.g., 2026-27',
    created_by VARCHAR(100) NOT NULL,
    created_on DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100),
    modified_on DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_voucher_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id),
    INDEX idx_voucher_number (voucher_number),
    INDEX idx_voucher_date (voucher_date),
    INDEX idx_voucher_type (voucher_type),
    INDEX idx_voucher_category (category),
    INDEX idx_voucher_status (status),
    INDEX idx_voucher_vendor (vendor_id),
    INDEX idx_voucher_fy (financial_year)
) ENGINE=InnoDB;

-- Voucher Documents/Attachments
CREATE TABLE voucher_documents (
    document_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    document_name VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) COMMENT 'BILL, INVOICE, RECEIPT, SUPPORTING_DOC',
    file_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(100),
    uploaded_on DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_voucher_doc FOREIGN KEY (voucher_id) REFERENCES vouchers(voucher_id),
    INDEX idx_voucher_doc (voucher_id)
) ENGINE=InnoDB;

-- Voucher Audit Trail (every change is logged)
CREATE TABLE voucher_audit_trail (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    field_changed VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    change_reason TEXT COMMENT 'Mandatory for amount/status changes',
    changed_by VARCHAR(100) NOT NULL,
    changed_on DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),

    CONSTRAINT fk_audit_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(voucher_id),
    INDEX idx_audit_voucher (voucher_id),
    INDEX idx_audit_changed_on (changed_on),
    INDEX idx_audit_changed_by (changed_by)
) ENGINE=InnoDB;

-- ============================================================
-- VOUCHER NUMBER SEQUENCE TRACKER
-- ============================================================

-- Tracks last used voucher number per type per financial year
CREATE TABLE voucher_sequences (
    sequence_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_type ENUM('PAYMENT','RECEIPT','JOURNAL','CONTRA') NOT NULL,
    financial_year VARCHAR(10) NOT NULL,
    last_number INT NOT NULL DEFAULT 0,

    UNIQUE KEY uk_type_fy (voucher_type, financial_year)
) ENGINE=InnoDB;
