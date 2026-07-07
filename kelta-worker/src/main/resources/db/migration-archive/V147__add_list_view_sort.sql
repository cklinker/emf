-- Multi-field sort for list views.
--
-- The legacy model stored a single sort_field + sort_direction. The list-view editor now supports
-- multiple sort keys (each with its own direction), persisted as a JSON array of {field, direction}.
-- sort_field/sort_direction remain populated with the first entry for back-compat with any consumer
-- that still reads the single-sort columns.
ALTER TABLE list_view ADD COLUMN IF NOT EXISTS sort jsonb;
