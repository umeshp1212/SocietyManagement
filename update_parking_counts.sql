-- =====================================================
-- Update Parking Counts (two_wheeler_count, four_wheeler_count)
-- Based on Aug-2026 bills
-- Rate: Two Wheeler = ₹30/slot, Four Wheeler = ₹60/slot
-- =====================================================

SET SQL_SAFE_UPDATES = 0;

-- Add columns if not exist (run once)
-- ALTER TABLE units ADD COLUMN two_wheeler_count INT DEFAULT 0;
-- ALTER TABLE units ADD COLUMN four_wheeler_count INT DEFAULT 0;

-- Reset all to 0 first
UPDATE units SET two_wheeler_count = 0, four_wheeler_count = 0;

-- C Wing parking data from bills
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-101';  -- ₹60 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-102';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-103';  -- ₹60 TWO WHEELER
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-106';  -- ₹30 (wait, bill shows ₹30 = 1 slot)
-- Correction: Let me re-read. C-106 shows TWO WHEELER PARKING CHARGES = 30, so 1 slot.
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-106';
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-201';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-204';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-205';  -- ₹60 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-206';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-302';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 3 WHERE unit_number = 'C-303';  -- ₹90 TWO WHEELER
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-305';  -- ₹60 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-402';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-403';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-404';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-502';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 1, four_wheeler_count = 1 WHERE unit_number = 'C-504';  -- ₹30 + ₹60
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-506';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-601';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-602';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 2, four_wheeler_count = 1 WHERE unit_number = 'C-603';  -- ₹60 + ₹60
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-604';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-701';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 2, four_wheeler_count = 1 WHERE unit_number = 'C-703';  -- ₹60 + ₹60
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-704';  -- ₹60 FOUR WHEELER (PODIUM) + NOC = ₹155
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-706';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-802';  -- ₹30 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-803';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-901';  -- ₹60 TWO WHEELER
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-1001';  -- ₹60 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1002';  -- ₹30 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1003';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1004';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1005';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1006';  -- ₹30 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1103';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1105';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1106';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1203';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1204';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET two_wheeler_count = 3 WHERE unit_number = 'C-1205';  -- ₹90 TWO WHEELER
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-1303';  -- ₹60 TWO WHEELER
UPDATE units SET four_wheeler_count = 2 WHERE unit_number = 'C-1304';  -- ₹120 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1305';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1401';  -- ₹30 TWO WHEELER
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'C-1403';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'C-1404';  -- ₹60 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1405';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'C-1406';  -- ₹30 TWO WHEELER
UPDATE units SET two_wheeler_count = 1, four_wheeler_count = 1 WHERE unit_number = 'C-906';  -- ₹30 + ₹60

-- D Wing parking data from bills
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-102';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-103';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-105';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-106';  -- ₹30
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'D-201';  -- ₹60
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'D-202';  -- ₹60
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-206';  -- ₹30
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'D-301';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-304';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-306';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-401';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-402';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-404';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-504';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-505';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-601';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-602';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-603';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-605';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-701';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-703';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-704';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-803';  -- ₹30
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'D-903';  -- ₹60
UPDATE units SET two_wheeler_count = 2 WHERE unit_number = 'D-906';  -- ₹60
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'D-1006';  -- ₹60 FOUR WHEELER (STILT)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-1103';  -- ₹30
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'D-1201';  -- ₹60 FOUR WHEELER (PODIUM)
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-1301';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-1306';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-1401';  -- ₹30
UPDATE units SET two_wheeler_count = 1 WHERE unit_number = 'D-1405';  -- ₹30
UPDATE units SET four_wheeler_count = 1 WHERE unit_number = 'D-1406';  -- ₹60 FOUR WHEELER (STILT)

-- Update parking_type based on counts (for backward compatibility)
UPDATE units SET parking_type = 'NONE' WHERE two_wheeler_count = 0 AND four_wheeler_count = 0;
UPDATE units SET parking_type = 'TWO_WHEELER' WHERE two_wheeler_count > 0 AND four_wheeler_count = 0;
UPDATE units SET parking_type = 'FOUR_WHEELER' WHERE two_wheeler_count = 0 AND four_wheeler_count > 0;
UPDATE units SET parking_type = 'BOTH' WHERE two_wheeler_count > 0 AND four_wheeler_count > 0;

SET SQL_SAFE_UPDATES = 1;

-- Verify
SELECT unit_number, two_wheeler_count, four_wheeler_count, parking_type,
       (two_wheeler_count * 30 + four_wheeler_count * 60) AS parking_charges
FROM units
WHERE two_wheeler_count > 0 OR four_wheeler_count > 0
ORDER BY wing, unit_number;
