-- =====================================================
-- Update Water Charges and BHK Type for all units
-- =====================================================

SET SQL_SAFE_UPDATES = 0;

-- ===== BHK TYPE UPDATES =====

-- C Wing: 1BHK flats (x01, x02, x04, x05, x06 series)
UPDATE units SET bhk_type = 'BHK_1' 
WHERE wing = 'C' AND (
    unit_number LIKE 'C-%01' OR unit_number LIKE 'C-%02' OR 
    unit_number LIKE 'C-%04' OR unit_number LIKE 'C-%05' OR unit_number LIKE 'C-%06'
);

-- C Wing: 2BHK flats (x03 series)
UPDATE units SET bhk_type = 'BHK_2' 
WHERE wing = 'C' AND unit_number LIKE 'C-%03';

-- D Wing: All 1BHK
UPDATE units SET bhk_type = 'BHK_1' WHERE wing = 'D';

-- Shops
UPDATE units SET bhk_type = 'SHOP' WHERE unit_type = 'SHOP';

-- ===== WATER CHARGES UPDATES =====

-- C Wing: 1BHK flats = ₹1400
UPDATE units SET water_charges = 1400 WHERE wing = 'C' AND bhk_type = 'BHK_1';

-- C Wing: 2BHK flats = ₹1700
UPDATE units SET water_charges = 1700 WHERE wing = 'C' AND bhk_type = 'BHK_2';

-- D Wing: All 1BHK = ₹1400
UPDATE units SET water_charges = 1400 WHERE wing = 'D';

-- Shops = ₹1100
UPDATE units SET water_charges = 1100 WHERE unit_type = 'SHOP';

SET SQL_SAFE_UPDATES = 1;

-- Verify
SELECT unit_number, wing, bhk_type, water_charges
FROM units
WHERE wing IN ('C', 'D') OR unit_type = 'SHOP'
ORDER BY wing, unit_number;
