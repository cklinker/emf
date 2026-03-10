-- V53: Populate reference_target from reference_collection_id
-- The V50 seed data created LOOKUP / MASTER_DETAIL fields with reference_collection_id
-- (the UUID of the target collection) but did NOT set reference_target (the collection name).
-- The UI uses reference_target to fetch records from /api/{collectionName}, so lookup
-- dropdowns were broken for any field missing this value.

UPDATE field
SET reference_target = c.name
FROM collection c
WHERE field.reference_collection_id = c.id
  AND field.reference_target IS NULL
  AND field.reference_collection_id IS NOT NULL;
