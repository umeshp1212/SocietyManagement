-- =====================================================
-- Update BHK Type for D Wing and C Wing
-- =====================================================

-- Disable safe update mode
SET SQL_SAFE_UPDATES = 0;

-- D Wing: ALL flats are 1BHK
UPDATE units SET bhk_type = 'BHK_1' WHERE wing = 'D';

-- C Wing: C-101, C-102, C-201, C-202, C-301, C-302, C-401, C-402 etc. (x01, x02 series) are 1BHK
UPDATE units SET bhk_type = 'BHK_1' 
WHERE wing = 'C' AND (
    unit_number LIKE 'C-_01' OR unit_number LIKE 'C-_02'
);

-- C Wing: C-103, C-203, C-303, C-403 etc. (x03 series) are 3BHK
UPDATE units SET bhk_type = 'BHK_3' 
WHERE wing = 'C' AND unit_number LIKE 'C-_03';

-- C Wing: C-105, C-106, C-205, C-206, C-305, C-306, C-405, C-406 etc. (x05, x06 series) are 1BHK
UPDATE units SET bhk_type = 'BHK_1' 
WHERE wing = 'C' AND (
    unit_number LIKE 'C-_05' OR unit_number LIKE 'C-_06'
);

-- Re-enable safe update mode
SET SQL_SAFE_UPDATES = 1;

-- Verify the updates
SELECT unit_number, wing, bhk_type 
FROM units 
WHERE wing IN ('C', 'D') 
ORDER BY wing, unit_number;
