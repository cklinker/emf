-- Remove unused canViewAll/canModifyAll columns from object permission tables.
-- These fields were never enforced at runtime and create false security expectations.

ALTER TABLE profile_object_permission DROP COLUMN IF EXISTS can_view_all;
ALTER TABLE profile_object_permission DROP COLUMN IF EXISTS can_modify_all;

ALTER TABLE permset_object_permission DROP COLUMN IF EXISTS can_view_all;
ALTER TABLE permset_object_permission DROP COLUMN IF EXISTS can_modify_all;
