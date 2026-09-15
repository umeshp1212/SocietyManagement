-- ============================================================
-- Migration: voucher approval-workflow + TDS permissions
-- ============================================================
-- Fixes an earlier seeding bug where these permissions were inserted with
-- hardcoded ids that collided with MAINTENANCE_PAYMENT_REASSIGN (created by
-- DataInitializer with an auto-assigned id). The primary-key clash caused
-- INSERT IGNORE to silently drop the rows, so VOUCHER_SUBMIT / _TREASURER_REVIEW
-- / _SECRETARY_VERIFY / _CHAIRMAN_APPROVE / _TDS_CONFIG never landed in the DB.
--
-- This script is keyed only on the unique permission_name and role_name, so it
-- is id-independent and safe to run against any environment. Idempotent:
-- re-running it makes no further changes (INSERT IGNORE).
--
-- Usage (production):
--   mysql -u <user> -p <database> < migration_voucher_workflow_permissions.sql
-- ============================================================

-- 1) Ensure the permissions exist (auto-assigned ids; no collision possible).
INSERT IGNORE INTO permissions (permission_name, module, description) VALUES
('VOUCHER_SUBMIT', 'VOUCHER', 'Submit a voucher for approval'),
('VOUCHER_TREASURER_REVIEW', 'VOUCHER', 'Treasurer review step in voucher approval'),
('VOUCHER_SECRETARY_VERIFY', 'VOUCHER', 'Secretary verify step in voucher approval'),
('VOUCHER_CHAIRMAN_APPROVE', 'VOUCHER', 'Chairman approve step in voucher approval'),
('VOUCHER_TDS_CONFIG', 'VOUCHER', 'Manage TDS configuration for vouchers');

-- 2) Grant them to the appropriate roles, mapped BY NAME.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON (
       (r.role_name IN ('MANAGER', 'SECRETARY', 'TREASURER')  AND p.permission_name = 'VOUCHER_SUBMIT')
    OR (r.role_name = 'TREASURER'                             AND p.permission_name = 'VOUCHER_TREASURER_REVIEW')
    OR (r.role_name = 'SECRETARY'                             AND p.permission_name = 'VOUCHER_SECRETARY_VERIFY')
    OR (r.role_name = 'CHAIRMAN'                              AND p.permission_name = 'VOUCHER_CHAIRMAN_APPROVE')
    OR (r.role_name IN ('SECRETARY', 'TREASURER')             AND p.permission_name = 'VOUCHER_TDS_CONFIG')
);

-- 3) SUPER_ADMIN gets everything (role_id 1).
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 1, permission_id FROM permissions;

-- 4) Verify (optional): should list the five permissions mapped per role.
-- SELECT r.role_name, p.permission_name
-- FROM role_permissions rp
-- JOIN roles r ON r.role_id = rp.role_id
-- JOIN permissions p ON p.permission_id = rp.permission_id
-- WHERE p.permission_name IN ('VOUCHER_SUBMIT','VOUCHER_TREASURER_REVIEW',
--   'VOUCHER_SECRETARY_VERIFY','VOUCHER_CHAIRMAN_APPROVE','VOUCHER_TDS_CONFIG')
-- ORDER BY p.permission_name, r.role_name;
