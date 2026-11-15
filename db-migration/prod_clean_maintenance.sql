-- Clean ALL maintenance-module data (keeps units, owners, society_settings).
-- Used before loading the verified local maintenance data into production.
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE suspense_audit_trail;
TRUNCATE TABLE suspense_entries;
TRUNCATE TABLE maintenance_ledger;
TRUNCATE TABLE maintenance_payments;
TRUNCATE TABLE bill_line_items;
TRUNCATE TABLE unit_advance_credits;
TRUNCATE TABLE maintenance_bills;
TRUNCATE TABLE penalties;
TRUNCATE TABLE opening_balances;
TRUNCATE TABLE receipt_sequences;
TRUNCATE TABLE unit_tag_assignments;
TRUNCATE TABLE unit_tags;
TRUNCATE TABLE maintenance_charge_config;
TRUNCATE TABLE water_charge_config;
SET FOREIGN_KEY_CHECKS = 1;
