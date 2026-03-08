-- Backfill the path column for any collections that have a null path.
-- The CollectionLifecycleHook should auto-set path = '/api/' || name on create,
-- but some collections created before the hook was deployed may be missing it.
-- Without a path, the gateway cannot register a dynamic route for the collection.

UPDATE collection
SET path = '/api/' || name
WHERE path IS NULL
  AND name IS NOT NULL;
