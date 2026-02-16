-- V52: Add display_field_id to collection table
-- Each collection can designate a field as its "display field" — the field whose value
-- is shown when records from this collection appear in lookup dropdowns on other collections.
-- When null, the UI falls back to a field named "name", then the first STRING field, then "id".

ALTER TABLE collection ADD COLUMN display_field_id VARCHAR(36);

ALTER TABLE collection ADD CONSTRAINT fk_collection_display_field
    FOREIGN KEY (display_field_id) REFERENCES field(id) ON DELETE SET NULL;

-- Set display_field_id for Threadline Clothing seed collections (from V50).
-- Only updates rows where the referenced field actually exists (safe for fresh installs).
UPDATE collection SET display_field_id = 'ec000300-0001-0000-0000-000000000001'
WHERE id = 'ec000100-0000-0000-0000-000000000001'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0001-0000-0000-000000000001');

UPDATE collection SET display_field_id = 'ec000300-0002-0000-0000-000000000002'
WHERE id = 'ec000100-0000-0000-0000-000000000002'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0002-0000-0000-000000000002');

UPDATE collection SET display_field_id = 'ec000300-0003-0000-0000-000000000001'
WHERE id = 'ec000100-0000-0000-0000-000000000003'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0003-0000-0000-000000000001');

UPDATE collection SET display_field_id = 'ec000300-0004-0000-0000-000000000001'
WHERE id = 'ec000100-0000-0000-0000-000000000004'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0004-0000-0000-000000000001');

UPDATE collection SET display_field_id = 'ec000300-0005-0000-0000-000000000003'
WHERE id = 'ec000100-0000-0000-0000-000000000005'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0005-0000-0000-000000000003');

UPDATE collection SET display_field_id = 'ec000300-0006-0000-0000-000000000005'
WHERE id = 'ec000100-0000-0000-0000-000000000006'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0006-0000-0000-000000000005');

UPDATE collection SET display_field_id = 'ec000300-0007-0000-0000-000000000009'
WHERE id = 'ec000100-0000-0000-0000-000000000007'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0007-0000-0000-000000000009');

UPDATE collection SET display_field_id = 'ec000300-0008-0000-0000-000000000001'
WHERE id = 'ec000100-0000-0000-0000-000000000008'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0008-0000-0000-000000000001');

UPDATE collection SET display_field_id = 'ec000300-0009-0000-0000-000000000001'
WHERE id = 'ec000100-0000-0000-0000-000000000009'
  AND EXISTS (SELECT 1 FROM field WHERE id = 'ec000300-0009-0000-0000-000000000001');
