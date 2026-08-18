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
INSERT IGNORE INTO vendors (vendor_id, vendor_name, category, contact_person, phone, email, agreement_start_date, agreement_end_date, contracted_amount, payment_frequency, status) VALUES
(1, 'SecureGuard Services', 'SECURITY', 'Ramesh Patil', '9988776655', 'secureguard@email.com', '2026-04-01', '2027-03-31', 85000.00, 'MONTHLY', 'ACTIVE'),
(2, 'CleanSweep Housekeeping', 'HOUSEKEEPING', 'Sunil Jadhav', '9988776656', 'cleansweep@email.com', '2026-04-01', '2027-03-31', 45000.00, 'MONTHLY', 'ACTIVE'),
(3, 'GreenTouch Gardens', 'GARDENING', 'Manoj Mane', '9988776657', 'greentouch@email.com', '2026-04-01', '2027-03-31', 15000.00, 'MONTHLY', 'ACTIVE');


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
(32, 'REPORT_OCCUPANCY', 'REPORT', 'View occupancy reports');

-- ============================================================
-- ROLE-PERMISSION MAPPING
-- ============================================================
-- SUPER_ADMIN gets ALL permissions
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 1, permission_id FROM permissions;

-- CHAIRMAN
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(2, 1), (2, 6), (2, 10), (2, 13), (2, 18), (2, 21), (2, 22), (2, 23),
(2, 4), (2, 16), (2, 24), (2, 31), (2, 32);

-- SECRETARY
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8), (3, 9),
(3, 10), (3, 11), (3, 12), (3, 13), (3, 14), (3, 15), (3, 16), (3, 17),
(3, 18), (3, 19), (3, 20), (3, 23), (3, 24), (3, 26), (3, 27), (3, 28),
(3, 31), (3, 32);

-- TREASURER
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(4, 1), (4, 6), (4, 10), (4, 13), (4, 18), (4, 19), (4, 20), (4, 21),
(4, 22), (4, 23), (4, 24), (4, 31);

-- COMMITTEE_MEMBER
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(5, 1), (5, 6), (5, 10), (5, 13), (5, 16), (5, 18), (5, 31), (5, 32);

-- OWNER
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(6, 1), (6, 6), (6, 13), (6, 14), (6, 18);

-- TENANT
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(7, 13), (7, 18);

-- AUDITOR
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(8, 1), (8, 6), (8, 10), (8, 13), (8, 18), (8, 23), (8, 24), (8, 31), (8, 32);

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
