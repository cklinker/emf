-- V47: Drop legacy security tables that are no longer used
--
-- The legacy security model (roles, policies, profiles, permission sets, sharing)
-- was removed. These tables have no corresponding JPA entities, repositories,
-- services, or controllers — they are completely orphaned.
--
-- Tables removed:
--   From V1:  role, policy, route_policy, field_policy, ui_page_policy, ui_menu_item_policy
--   From V11: profile, object_permission, field_permission, system_permission,
--             permission_set, permset_object_permission, permset_field_permission,
--             permset_system_permission, user_permission_set
--   From V12: org_wide_default, sharing_rule, record_share

-- Drop foreign key constraints first (child tables before parent tables)

-- V12 sharing tables
DROP TABLE IF EXISTS record_share CASCADE;
DROP TABLE IF EXISTS sharing_rule CASCADE;
DROP TABLE IF EXISTS org_wide_default CASCADE;

-- V11 permission set junction tables
DROP TABLE IF EXISTS user_permission_set CASCADE;
DROP TABLE IF EXISTS permset_system_permission CASCADE;
DROP TABLE IF EXISTS permset_field_permission CASCADE;
DROP TABLE IF EXISTS permset_object_permission CASCADE;
DROP TABLE IF EXISTS permission_set CASCADE;

-- V11 profile permission tables
DROP TABLE IF EXISTS system_permission CASCADE;
DROP TABLE IF EXISTS field_permission CASCADE;
DROP TABLE IF EXISTS object_permission CASCADE;
DROP TABLE IF EXISTS profile CASCADE;

-- V1 policy tables (junction tables first)
DROP TABLE IF EXISTS ui_menu_item_policy CASCADE;
DROP TABLE IF EXISTS ui_page_policy CASCADE;
DROP TABLE IF EXISTS field_policy CASCADE;
DROP TABLE IF EXISTS route_policy CASCADE;
DROP TABLE IF EXISTS policy CASCADE;
DROP TABLE IF EXISTS role CASCADE;

-- Remove profile_id foreign key column from platform_user if it exists
ALTER TABLE platform_user DROP COLUMN IF EXISTS profile_id;
