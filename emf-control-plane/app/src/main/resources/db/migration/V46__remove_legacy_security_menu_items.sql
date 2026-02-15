-- V46: Remove legacy security menu items that have no backend or frontend implementation
--
-- The legacy security model (Roles, Policies, Profiles, Permission Sets, Sharing,
-- Role Hierarchy) was removed. These menu items point to non-existent pages and
-- endpoints, so remove them from the Security & Access menu.
-- Remaining items: Users, OIDC Providers.

DELETE FROM ui_menu_item WHERE id IN (
    '00000000-0000-0001-0002-000000000002',  -- Roles
    '00000000-0000-0001-0002-000000000003',  -- Policies
    '00000000-0000-0001-0002-000000000004',  -- Profiles
    '00000000-0000-0001-0002-000000000005',  -- Permission Sets
    '00000000-0000-0001-0002-000000000006',  -- Sharing
    '00000000-0000-0001-0002-000000000007'   -- Role Hierarchy
);

-- Re-order remaining items: Users (1), OIDC Providers (2)
UPDATE ui_menu_item SET display_order = 1 WHERE id = '00000000-0000-0001-0002-000000000001';  -- Users
UPDATE ui_menu_item SET display_order = 2 WHERE id = '00000000-0000-0001-0002-000000000008';  -- OIDC Providers
