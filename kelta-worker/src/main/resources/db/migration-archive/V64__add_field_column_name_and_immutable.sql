-- V64: Add column_name and immutable columns to field table
-- Supports SystemCollectionSeeder persisting full FieldDefinition metadata
--
-- column_name: maps API field name to physical DB column (e.g., firstName -> first_name)
-- immutable: marks fields that cannot be updated after creation

ALTER TABLE field ADD COLUMN IF NOT EXISTS column_name VARCHAR(100);
ALTER TABLE field ADD COLUMN IF NOT EXISTS immutable BOOLEAN DEFAULT false NOT NULL;

-- Cleanup orphan rows from PR #263 that set tenant_id = 'SYSTEM'
-- (the transaction likely rolled back due to FK violation, but clean up just in case)
UPDATE collection SET tenant_id = '00000000-0000-0000-0000-000000000001'
WHERE tenant_id = 'SYSTEM';
