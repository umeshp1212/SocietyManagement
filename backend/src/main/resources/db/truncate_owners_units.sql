-- ============================================================
-- Truncate owners, units, and dependent tables
-- Run this BEFORE uploading the actual CSV data
-- Execute in MySQL: mysql -u root -p society_management < truncate_owners_units.sql
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE ownership_history;
TRUNCATE TABLE unit_owners;
TRUNCATE TABLE tenant_family_members;
TRUNCATE TABLE tenant_vehicles;
TRUNCATE TABLE tenant_documents;
TRUNCATE TABLE tenants;
TRUNCATE TABLE owners;
TRUNCATE TABLE units;

SET FOREIGN_KEY_CHECKS = 1;

-- Verify
SELECT 'owners' AS table_name, COUNT(*) AS row_count FROM owners
UNION ALL SELECT 'units', COUNT(*) FROM units
UNION ALL SELECT 'unit_owners', COUNT(*) FROM unit_owners
UNION ALL SELECT 'ownership_history', COUNT(*) FROM ownership_history
UNION ALL SELECT 'tenants', COUNT(*) FROM tenants;
