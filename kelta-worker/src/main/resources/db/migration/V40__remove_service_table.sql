-- Remove the service abstraction: drop service_id from collection and drop service table
ALTER TABLE collection DROP CONSTRAINT IF EXISTS fk_collection_service;
DROP INDEX IF EXISTS idx_collection_service_id;
DROP INDEX IF EXISTS idx_collection_service_name;
ALTER TABLE collection DROP COLUMN IF EXISTS service_id;

DROP TABLE IF EXISTS service;
