SELECT COUNT(*) as total FROM units;
SELECT unit_number FROM units WHERE unit_number LIKE 'C-%' LIMIT 5;
SELECT unit_number FROM units WHERE unit_number LIKE 'S-%' LIMIT 5;
