-- Finish removing the legacy permission-set authorization model.
--
-- The six permission-set tables were dropped in V98 ("authorization now handled
-- entirely by Cerbos policies generated from profiles"), but the runtime still
-- registered them as system collections and the delegated-admin scope carried an
-- assignable_permission_set_ids column that queried them — a broken, unused path.
-- The system-collection definitions and delegated-admin wiring are removed in the
-- same change; this migration drops the residual schema.

-- Drop the now-unused delegated-admin scope column (delegated_admin_scope is empty;
-- assignable permission sets are no longer a concept).
ALTER TABLE delegated_admin_scope DROP COLUMN IF EXISTS assignable_permission_set_ids;

-- Defensive: guarantee the permission-set tables are gone on every environment.
-- These were dropped in V98; re-issuing IF EXISTS is a no-op on already-migrated DBs
-- and makes the removal explicit for anyone reading the head migration.
DROP TABLE IF EXISTS group_permission_set;
DROP TABLE IF EXISTS user_permission_set;
DROP TABLE IF EXISTS permset_field_permission;
DROP TABLE IF EXISTS permset_object_permission;
DROP TABLE IF EXISTS permset_system_permission;
DROP TABLE IF EXISTS permission_set;
