-- =====================================================
-- Update Occupancy Status based on NOC Charges
-- If NOC = 155, unit is RENTED; otherwise SELF_OCCUPIED
-- =====================================================

SET SQL_SAFE_UPDATES = 0;

-- First set all to SELF_OCCUPIED
UPDATE units SET occupancy_status = 'SELF_OCCUPIED' WHERE wing IN ('C', 'D') OR unit_type = 'SHOP';

-- C Wing RENTED units (NOC = 155)
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-102';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-105';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-306';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-401';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-405';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-406';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-501';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-505';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-604';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-704';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-806';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1003';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1101';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1103';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1104';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1105';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1106';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1204';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1306';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'C-1402';

-- D Wing RENTED units (NOC = 155)
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-205';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-304';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-403';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-501';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-503';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-506';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-702';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-705';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-706';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-801';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-802';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-901';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1005';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1006';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1102';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1105';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1106';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1203';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1204';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1305';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1401';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1405';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'D-1406';

-- Shops RENTED (NOC = 155)
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0028';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0029';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0031';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0032';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0033';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0034';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0035';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0036';
UPDATE units SET occupancy_status = 'RENTED' WHERE unit_number = 'S-0037';

SET SQL_SAFE_UPDATES = 1;

-- Verify
SELECT unit_number, occupancy_status
FROM units
WHERE occupancy_status = 'RENTED'
ORDER BY wing, unit_number;
