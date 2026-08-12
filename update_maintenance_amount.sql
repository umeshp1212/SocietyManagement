-- Set maintenance amount for all flats (Rs 3500) and shops (Rs 5000)
UPDATE units SET monthly_maintenance_amount = 3500 WHERE unit_type = 'FLAT';
UPDATE units SET monthly_maintenance_amount = 5000 WHERE unit_type = 'SHOP';
SELECT unit_type, COUNT(*) as count, monthly_maintenance_amount FROM units GROUP BY unit_type, monthly_maintenance_amount;
