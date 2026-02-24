-- Add path column to collection table
ALTER TABLE collection ADD COLUMN path VARCHAR(255);

-- Update existing collections to have a path based on service base_path and collection name
UPDATE collection c
SET path = CONCAT(
    CASE 
        WHEN s.base_path IS NULL OR s.base_path = '' THEN '/api'
        WHEN s.base_path LIKE '%/' THEN SUBSTRING(s.base_path, 1, LENGTH(s.base_path) - 1)
        WHEN s.base_path NOT LIKE '/%' THEN CONCAT('/', s.base_path)
        ELSE s.base_path
    END,
    '/',
    c.name
)
FROM service s
WHERE c.service_id = s.id;
