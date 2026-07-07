-- Remove storage_mode column from collection table.
-- All collections now use physical table storage exclusively.

DROP INDEX IF EXISTS idx_collection_storage_mode;
ALTER TABLE collection DROP COLUMN IF EXISTS storage_mode;
