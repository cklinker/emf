-- Remove permission set tables.
-- Authorization is now handled entirely by Cerbos policies generated
-- from profiles (including custom ABAC rules).

DROP TABLE IF EXISTS group_permission_set;
DROP TABLE IF EXISTS user_permission_set;
DROP TABLE IF EXISTS permset_field_permission;
DROP TABLE IF EXISTS permset_object_permission;
DROP TABLE IF EXISTS permset_system_permission;
DROP TABLE IF EXISTS permission_set;
