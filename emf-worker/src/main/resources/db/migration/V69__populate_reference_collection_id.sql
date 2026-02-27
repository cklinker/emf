-- V69: Populate reference_collection_id from reference_target
-- When fields are created via the UI with a referenceTarget (collection name) but
-- without reference_collection_id (collection UUID), the lookup dropdown in the
-- form page fails because useLookupDisplayMap requires referenceCollectionId.
-- This migration backfills reference_collection_id for all fields that have
-- reference_target set but reference_collection_id NULL.

UPDATE field
SET reference_collection_id = c.id
FROM collection c
WHERE field.reference_target = c.name
  AND field.reference_collection_id IS NULL
  AND field.reference_target IS NOT NULL;
