-- ============================================================
-- Seed Data for Society Management
-- Runs automatically on application start (spring.sql.init)
-- Uses INSERT IGNORE to prevent duplicate errors on restart
-- ============================================================

-- ============================================================
-- Initialize Voucher Sequences for FY 2026-27
-- ============================================================
INSERT IGNORE INTO voucher_sequences (sequence_id, voucher_type, financial_year, last_number) VALUES
(1, 'PAYMENT', '2026-27', 0),
(2, 'RECEIPT', '2026-27', 0),
(3, 'JOURNAL', '2026-27', 0),
(4, 'CONTRA', '2026-27', 0);

-- ============================================================
-- Sample Units (Wing A - Floor 1 to 4, 4 flats per floor)
-- You can bulk import all 178 units via the application
-- ============================================================


-- ============================================================
-- Sample Vendors
-- ============================================================
-- Seeded further below, AFTER vendor_categories, because vendors.category_id
-- is a NOT NULL foreign key referencing vendor_categories(category_id).


-- ============================================================
-- Society Settings (default configuration)
-- ============================================================
INSERT IGNORE INTO society_settings (id, society_name, address_line1, address_line2, city, state, pincode, registration_number, registration_date, phone, email, chairman_name, secretary_name, treasurer_name) VALUES
(1, 'ABC Cooperative Housing Society Ltd.', 'Plot No. 123, Sector 5', 'Near City Mall', 'Pune', 'Maharashtra', '411001', 'MH/HSG/12345/2010', '15-03-2010', '020-12345678', 'abc.society@email.com', 'Rajesh Kumar', 'Suresh Mehta', 'Priya Sharma');


-- ============================================================
-- ROLES
-- ============================================================
INSERT IGNORE INTO roles (role_id, role_name, display_name, description) VALUES
(1, 'SUPER_ADMIN', 'Super Admin', 'Full access to all modules and settings'),
(2, 'CHAIRMAN', 'Chairman', 'Approve vouchers, transfers, NOC; view all reports'),
(3, 'SECRETARY', 'Secretary', 'Manage owners, tenants, vendors; create vouchers; issue NOC'),
(4, 'TREASURER', 'Treasurer', 'Record payments, manage vouchers, financial reports'),
(5, 'COMMITTEE_MEMBER', 'Committee Member', 'View reports, approve NOC if assigned'),
(6, 'OWNER', 'Unit Owner', 'View own details, register tenant, pay maintenance'),
(7, 'TENANT', 'Tenant', 'View own details, pay maintenance if authorized'),
(8, 'AUDITOR', 'Auditor', 'Read-only access to all financial data'),
(9, 'MANAGER', 'Manager', 'Create and manage vouchers, submit for approval');

-- ============================================================
-- PERMISSIONS
-- ============================================================
INSERT IGNORE INTO permissions (permission_id, permission_name, module, description) VALUES
-- Owner Module
(1, 'OWNER_VIEW', 'OWNER', 'View owners list and details'),
(2, 'OWNER_CREATE', 'OWNER', 'Add new owner'),
(3, 'OWNER_UPDATE', 'OWNER', 'Update owner details'),
(4, 'OWNER_TRANSFER', 'OWNER', 'Transfer unit ownership'),
(5, 'OWNER_BULK_UPLOAD', 'OWNER', 'Bulk upload owners via CSV'),
-- Unit Module
(6, 'UNIT_VIEW', 'UNIT', 'View units list and details'),
(7, 'UNIT_CREATE', 'UNIT', 'Create new unit'),
(8, 'UNIT_UPDATE', 'UNIT', 'Update unit details'),
(9, 'UNIT_MANAGE_OWNERS', 'UNIT', 'Add/remove co-owners'),
-- Vendor Module
(10, 'VENDOR_VIEW', 'VENDOR', 'View vendors list and details'),
(11, 'VENDOR_CREATE', 'VENDOR', 'Add new vendor'),
(12, 'VENDOR_UPDATE', 'VENDOR', 'Update vendor details'),
-- Tenant Module
(13, 'TENANT_VIEW', 'TENANT', 'View tenants list and details'),
(14, 'TENANT_CREATE', 'TENANT', 'Register new tenant'),
(15, 'TENANT_UPDATE', 'TENANT', 'Update tenant details'),
(16, 'TENANT_NOC_APPROVE', 'TENANT', 'Approve/reject NOC'),
(17, 'TENANT_BULK_UPLOAD', 'TENANT', 'Bulk upload tenants via CSV'),
-- Voucher Module
(18, 'VOUCHER_VIEW', 'VOUCHER', 'View vouchers list and details'),
(19, 'VOUCHER_CREATE', 'VOUCHER', 'Create new voucher'),
(20, 'VOUCHER_UPDATE', 'VOUCHER', 'Update voucher'),
(21, 'VOUCHER_FINALIZE', 'VOUCHER', 'Finalize voucher'),
(22, 'VOUCHER_CANCEL', 'VOUCHER', 'Cancel voucher'),
(23, 'VOUCHER_DOWNLOAD_PDF', 'VOUCHER', 'Download voucher PDF'),
-- Settings
(24, 'SETTINGS_VIEW', 'SETTINGS', 'View society settings'),
(25, 'SETTINGS_UPDATE', 'SETTINGS', 'Update society settings'),
-- User Management
(26, 'USER_VIEW', 'USER', 'View users list'),
(27, 'USER_CREATE', 'USER', 'Create user accounts'),
(28, 'USER_UPDATE', 'USER', 'Update user accounts'),
(29, 'USER_RESET_PASSWORD', 'USER', 'Reset user passwords'),
(30, 'USER_ASSIGN_ROLES', 'USER', 'Assign roles to users'),
-- Reports
(31, 'REPORT_FINANCIAL', 'REPORT', 'View financial reports'),
(32, 'REPORT_OCCUPANCY', 'REPORT', 'View occupancy reports'),
-- Maintenance
(33, 'MAINTENANCE_VIEW', 'MAINTENANCE', 'View maintenance bills and payments'),
(34, 'MAINTENANCE_CREATE', 'MAINTENANCE', 'Generate maintenance bills'),
(35, 'MAINTENANCE_PAYMENT', 'MAINTENANCE', 'Record offline payments'),
-- Member Requests
(36, 'MEMBER_REQUEST_VIEW', 'MEMBER', 'View member registration and profile requests'),
(37, 'MEMBER_REQUEST_APPROVE', 'MEMBER', 'Approve/reject member requests'),
-- Tenant registration approval (member-submitted tenant registrations)
(38, 'TENANT_APPROVE_REGISTRATION', 'TENANT', 'Approve/reject owner-submitted tenant registrations'),
-- Committee Module
(39, 'COMMITTEE_VIEW', 'COMMITTEE', 'View management committee members'),
(40, 'COMMITTEE_MANAGE', 'COMMITTEE', 'Add, update, or remove committee members');

-- ============================================================
-- ROLE-PERMISSION MAPPING
-- ============================================================
-- SUPER_ADMIN gets ALL permissions
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 1, permission_id FROM permissions;

-- CHAIRMAN
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(2, 1), (2, 6), (2, 10), (2, 13), (2, 18), (2, 21), (2, 22), (2, 23),
(2, 4), (2, 16), (2, 24), (2, 31), (2, 32),
(2, 33), (2, 36), (2, 37), (2, 38), (2, 39), (2, 40);

-- SECRETARY
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8), (3, 9),
(3, 10), (3, 11), (3, 12), (3, 13), (3, 14), (3, 15), (3, 16), (3, 17),
(3, 18), (3, 19), (3, 20), (3, 23), (3, 24), (3, 26), (3, 27), (3, 28),
(3, 31), (3, 32),
(3, 33), (3, 34), (3, 35), (3, 36), (3, 37), (3, 38), (3, 39), (3, 40);

-- TREASURER
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(4, 1), (4, 6), (4, 10), (4, 13), (4, 18), (4, 19), (4, 20), (4, 21),
(4, 22), (4, 23), (4, 24), (4, 31),
(4, 33), (4, 36);

-- COMMITTEE_MEMBER
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(5, 1), (5, 6), (5, 10), (5, 13), (5, 16), (5, 18), (5, 31), (5, 32), (5, 39);

-- OWNER
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(6, 1), (6, 6), (6, 13), (6, 14), (6, 18);

-- TENANT
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(7, 13), (7, 18);

-- AUDITOR
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(8, 1), (8, 6), (8, 10), (8, 13), (8, 18), (8, 23), (8, 24), (8, 31), (8, 32), (8, 39);

-- MANAGER (create/update/view vouchers, view vendors)
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(9, 10), (9, 18), (9, 19), (9, 20), (9, 23);

-- ============================================================
-- MAINTENANCE CHARGE CONFIGURATION
-- Default charges matching the society bill format
-- Water charges are set per-unit (varies by BHK/tank config)
-- Parking charges: separate entries for 2-wheeler and 4-wheeler
-- ============================================================
INSERT IGNORE INTO maintenance_charge_config (charge_config_id, charge_code, charge_name, description, calculation_type, rate_per_sqft, flat_amount, applicable_to, display_order, is_active) VALUES
(1, 'MAINTENANCE', 'Maintenance', 'Monthly maintenance charges', 'FLAT', NULL, 1500.00, 'ALL', 1, 1),
(2, 'SINKING_FUND', 'Sinking Fund', 'Sinking fund contribution (area-based)', 'AREA_BASED', 0.50, NULL, 'ALL', 2, 1),
(3, 'REPAIR_FUND', 'Repair Fund', 'Repair and maintenance reserve fund (area-based)', 'AREA_BASED', 0.50, NULL, 'ALL', 3, 1),
(4, 'WATER_CHARGES', 'Water Charges', 'Water supply charges (per-unit based on tank config: 1RK=550, 1BHK/2BHK=850, 3BHK=1150)', 'FLAT', NULL, 850.00, 'ALL', 4, 1),
(5, 'PARKING_2W', 'Parking Charges (Two Wheeler)', 'Two wheeler parking charges', 'FLAT', NULL, 200.00, 'TWO_WHEELER', 5, 1),
(6, 'PARKING_4W', 'Parking Charges (Four Wheeler)', 'Four wheeler parking charges', 'FLAT', NULL, 500.00, 'FOUR_WHEELER', 6, 1),
(7, 'NOC_CHARGES', 'NOC Charges', 'No Objection Certificate charges for rented flats', 'FLAT', NULL, 500.00, 'RENTED', 7, 1),
(8, 'WELFARE_FUND', 'Welfare Fund', 'Society welfare fund contribution', 'FLAT', NULL, 100.00, 'ALL', 8, 1);

-- Fix: Update existing parking charge row if it was seeded with old single entry
UPDATE maintenance_charge_config SET charge_code='PARKING_2W', charge_name='Parking Charges (Two Wheeler)', description='Two wheeler parking charges', flat_amount=200.00, applicable_to='TWO_WHEELER', display_order=5 WHERE charge_config_id=5 AND charge_code='PARKING_CHARGES';
-- Insert 4-wheeler if not exists (for existing DBs that had old single parking row)
INSERT IGNORE INTO maintenance_charge_config (charge_config_id, charge_code, charge_name, description, calculation_type, rate_per_sqft, flat_amount, applicable_to, display_order, is_active) VALUES
(6, 'PARKING_4W', 'Parking Charges (Four Wheeler)', 'Four wheeler parking charges', 'FLAT', NULL, 500.00, 'FOUR_WHEELER', 6, 1);

-- ============================================================
-- DEFAULT ADMIN USER
-- Created programmatically by DataInitializer.java on first run
-- Default credentials: admin / Admin@123
-- ============================================================


-- ============================================================
-- TDS CONFIGURATION
-- Default TDS rates as per Indian Income Tax Act
-- Section 194C: Contracts (1% individual/HUF, 2% others)
-- Section 194J: Professional/Technical services (10%)
-- Threshold: Rs 30,000 per transaction for 194C
-- ============================================================
INSERT IGNORE INTO tds_config (tds_config_id, vendor_category, tds_section, tds_rate, threshold_amount, description, is_active) VALUES
(1, 'SECURITY', '194C', 2.00, 30000.00, 'Security services - TDS on contract payments', 1),
(2, 'HOUSEKEEPING', '194C', 2.00, 30000.00, 'Housekeeping services - TDS on contract payments', 1),
(3, 'GARDENING', '194C', 2.00, 30000.00, 'Gardening/landscaping services - TDS on contract', 1),
(4, 'LIFT_MAINTENANCE', '194C', 2.00, 30000.00, 'Lift AMC - TDS on contract payments', 1),
(5, 'PLUMBING', '194C', 1.00, 30000.00, 'Plumbing works - TDS on contract (individual)', 1),
(6, 'ELECTRICAL', '194C', 1.00, 30000.00, 'Electrical works - TDS on contract (individual)', 1),
(7, 'PEST_CONTROL', '194C', 2.00, 30000.00, 'Pest control services - TDS on contract', 1),
(8, 'FIRE_SAFETY', '194C', 2.00, 30000.00, 'Fire safety AMC - TDS on contract', 1),
(9, 'CCTV_INTERCOM', '194C', 2.00, 30000.00, 'CCTV/Intercom services - TDS on contract', 1),
(10, 'WATER_TANK_CLEANING', '194C', 2.00, 30000.00, 'Water tank cleaning - TDS on contract', 1),
(11, 'PAINTING_CIVIL', '194C', 2.00, 30000.00, 'Painting/Civil works - TDS on contract', 1),
(12, 'LEGAL_AUDIT', '194J', 10.00, 30000.00, 'Legal/Audit professional fees - TDS on professional services', 1),
(13, 'SOFTWARE_IT', '194J', 10.00, 30000.00, 'Software/IT services - TDS on technical services', 1),
(14, 'OTHER', '194C', 2.00, 30000.00, 'Other services - TDS on contract payments', 0);


-- ============================================================
-- VOUCHER CATEGORIES
-- Dynamic expense/income categories for voucher classification
-- Replaces the hard-coded ExpenseCategory enum
-- ============================================================
INSERT IGNORE INTO voucher_categories (category_id, code, name, type, description, display_order, is_active) VALUES
(1, 'SECURITY', 'Security', 'EXPENSE', 'Security services expenses', 1, 1),
(2, 'HOUSEKEEPING', 'Housekeeping', 'EXPENSE', 'Housekeeping services expenses', 2, 1),
(3, 'ELECTRICITY_COMMON', 'Electricity (Common)', 'EXPENSE', 'Common area electricity charges', 3, 1),
(4, 'WATER', 'Water', 'EXPENSE', 'Water supply charges', 4, 1),
(5, 'LIFT_MAINTENANCE', 'Lift Maintenance', 'EXPENSE', 'Lift maintenance and AMC', 5, 1),
(6, 'GARDEN', 'Garden', 'EXPENSE', 'Garden and landscaping expenses', 6, 1),
(7, 'REPAIRS_MAINTENANCE', 'Repairs & Maintenance', 'EXPENSE', 'General repairs and maintenance', 7, 1),
(8, 'PEST_CONTROL', 'Pest Control', 'EXPENSE', 'Pest control services', 8, 1),
(9, 'LEGAL_PROFESSIONAL', 'Legal & Professional', 'EXPENSE', 'Legal and professional fees', 9, 1),
(10, 'STATIONERY_PRINTING', 'Stationery & Printing', 'EXPENSE', 'Stationery and printing expenses', 10, 1),
(11, 'EVENTS_CELEBRATIONS', 'Events & Celebrations', 'EXPENSE', 'Events and celebration expenses', 11, 1),
(12, 'INSURANCE', 'Insurance', 'EXPENSE', 'Insurance premium expenses', 12, 1),
(13, 'SINKING_FUND', 'Sinking Fund', 'EXPENSE', 'Sinking fund contributions', 13, 1),
(14, 'BANK_CHARGES', 'Bank Charges', 'EXPENSE', 'Bank service charges', 14, 1),
(15, 'MISCELLANEOUS', 'Miscellaneous', 'EXPENSE', 'Miscellaneous expenses', 15, 1),
(16, 'MAINTENANCE_INCOME', 'Maintenance Income', 'INCOME', 'Monthly maintenance collection', 1, 1),
(17, 'INTEREST_INCOME', 'Interest Income', 'INCOME', 'Bank interest income', 2, 1),
(18, 'PENALTY_INCOME', 'Penalty Income', 'INCOME', 'Late payment penalties collected', 3, 1);


-- ============================================================
-- VENDOR CATEGORIES
-- Dynamic vendor categories (replaces VendorCategory enum)
-- Used in vendor creation and TDS configuration
-- ============================================================
INSERT IGNORE INTO vendor_categories (category_id, code, name, description, display_order, is_active) VALUES
(1, 'SECURITY', 'Security', 'Security services', 1, 1),
(2, 'HOUSEKEEPING', 'Housekeeping', 'Housekeeping and cleaning services', 2, 1),
(3, 'GARDENING', 'Gardening', 'Garden and landscaping services', 3, 1),
(4, 'LIFT_MAINTENANCE', 'Lift Maintenance', 'Lift/Elevator maintenance and AMC', 4, 1),
(5, 'PLUMBING', 'Plumbing', 'Plumbing works and repairs', 5, 1),
(6, 'ELECTRICAL', 'Electrical', 'Electrical works and repairs', 6, 1),
(7, 'PEST_CONTROL', 'Pest Control', 'Pest control services', 7, 1),
(8, 'FIRE_SAFETY', 'Fire Safety', 'Fire safety equipment and AMC', 8, 1),
(9, 'CCTV_INTERCOM', 'CCTV / Intercom', 'CCTV and intercom services', 9, 1),
(10, 'WATER_TANK_CLEANING', 'Water Tank Cleaning', 'Water tank cleaning services', 10, 1),
(11, 'PAINTING_CIVIL', 'Painting / Civil', 'Painting and civil works', 11, 1),
(12, 'LEGAL_AUDIT', 'Legal / Audit', 'Legal and audit professional services', 12, 1),
(13, 'SOFTWARE_IT', 'Software / IT', 'Software and IT services', 13, 1),
(14, 'OTHER', 'Other', 'Other services', 14, 1);


-- ============================================================
-- Sample Vendors
-- Seeded here (after vendor_categories) because vendors.category_id is a
-- NOT NULL foreign key referencing vendor_categories(category_id).
--   category_id: 1 = SECURITY, 2 = HOUSEKEEPING, 3 = GARDENING
-- ============================================================
INSERT IGNORE INTO vendors (vendor_id, vendor_name, category_id, contact_person, phone, email, agreement_start_date, agreement_end_date, contracted_amount, payment_frequency, status) VALUES
(1, 'SecureGuard Services', 1, 'Ramesh Patil', '9988776655', 'secureguard@email.com', '2026-04-01', '2027-03-31', 85000.00, 'MONTHLY', 'ACTIVE'),
(2, 'CleanSweep Housekeeping', 2, 'Sunil Jadhav', '9988776656', 'cleansweep@email.com', '2026-04-01', '2027-03-31', 45000.00, 'MONTHLY', 'ACTIVE'),
(3, 'GreenTouch Gardens', 3, 'Manoj Mane', '9988776657', 'greentouch@email.com', '2026-04-01', '2027-03-31', 15000.00, 'MONTHLY', 'ACTIVE');

-- Backfill / repair: fix any existing vendor rows left with an invalid category_id
-- (e.g. 0, NULL, or pointing at a non-existent category) after the migration from
-- the old `category` ENUM column to the vendor_categories foreign key.
-- Sets such rows to OTHER (14). Runs every startup and is idempotent.
-- NOTE: intentionally does NOT reference the legacy `category` column, because on
-- environments where the vendors table was created fresh by Hibernate that column
-- does not exist, and referencing it would abort startup (Unknown column).
UPDATE vendors v
LEFT JOIN vendor_categories current
  ON current.category_id = v.category_id
SET v.category_id = 14
WHERE current.category_id IS NULL;


-- ============================================================
-- SCHEMA MIGRATION: widen tenants.status ENUM
-- ============================================================
-- The Java TenantStatus enum added PENDING_APPROVAL and REJECTED (for the
-- member-portal tenant registration + admin approval flow). The tenants.status
-- column was originally created as ENUM('ACTIVE','NOTICE_PERIOD','VACATED'), and
-- Hibernate's ddl-auto=update does NOT alter existing ENUM definitions, so inserts
-- of the new values fail with "Data truncated for column 'status'".
-- This MODIFY is idempotent (re-applying the same definition is a no-op) and only
-- widens the ENUM, so existing rows are preserved.
ALTER TABLE tenants
    MODIFY COLUMN status ENUM('PENDING_APPROVAL','ACTIVE','NOTICE_PERIOD','VACATED','REJECTED')
    NOT NULL DEFAULT 'ACTIVE';


-- ============================================================
-- SCHEMA MIGRATION: normalize charset/collation for vendor tables
-- ============================================================
-- The `vendors` table is created by schema.sql while `vendor_categories` is
-- created by Hibernate (ddl-auto=update). On MySQL 8 these can end up with
-- different collations (e.g. utf8mb4_unicode_ci vs utf8mb4_0900_ai_ci), which
-- causes "Illegal mix of collations" when their string columns are compared /
-- joined. Force both tables to the same charset/collation as the database.
-- CONVERT TO CHARACTER SET is idempotent (no-op when already correct) and
-- preserves existing data.
ALTER TABLE vendor_categories CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE vendors CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
