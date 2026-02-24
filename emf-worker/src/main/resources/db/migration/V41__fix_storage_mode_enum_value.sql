-- Fix storage_mode enum mismatch: control-plane stored "PHYSICAL_TABLE" (singular)
-- but the runtime StorageMode enum expects "PHYSICAL_TABLES" (plural).
UPDATE collection SET storage_mode = 'PHYSICAL_TABLES' WHERE storage_mode = 'PHYSICAL_TABLE';

-- Update the column default for new rows
ALTER TABLE collection ALTER COLUMN storage_mode SET DEFAULT 'PHYSICAL_TABLES';
