-- V55: Add permission model (profiles, permission sets, system/object/field permissions)
-- Part of Security Unification Plan - Phase 1

-- Profiles: Named permission bundles assigned to users (one per user)
CREATE TABLE profile (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

-- System permissions per profile
CREATE TABLE profile_system_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (profile_id, permission_name)
);

-- Object permissions per profile per collection
CREATE TABLE profile_object_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    can_create BOOLEAN NOT NULL DEFAULT FALSE,
    can_read BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_all BOOLEAN NOT NULL DEFAULT FALSE,
    can_modify_all BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (profile_id, collection_id)
);

-- Field permissions per profile per field
CREATE TABLE profile_field_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    field_id VARCHAR(36) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    UNIQUE (profile_id, field_id)
);

-- Permission Sets: Additional permission bundles (many per user, additive)
CREATE TABLE permission_set (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

-- Permission set system permissions
CREATE TABLE permset_system_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (permission_set_id, permission_name)
);

-- Permission set object permissions
CREATE TABLE permset_object_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    can_create BOOLEAN NOT NULL DEFAULT FALSE,
    can_read BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_all BOOLEAN NOT NULL DEFAULT FALSE,
    can_modify_all BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (permission_set_id, collection_id)
);

-- Permission set field permissions
CREATE TABLE permset_field_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    field_id VARCHAR(36) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    UNIQUE (permission_set_id, field_id)
);

-- User ↔ Profile assignment (one profile per user per tenant)
ALTER TABLE platform_user ADD COLUMN IF NOT EXISTS profile_id VARCHAR(36) REFERENCES profile(id);

-- User ↔ Permission Set assignment (many-to-many)
CREATE TABLE user_permission_set (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, permission_set_id)
);

-- Group ↔ Permission Set assignment
CREATE TABLE group_permission_set (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, permission_set_id)
);

-- Indexes for query performance
CREATE INDEX idx_profile_tenant ON profile(tenant_id);
CREATE INDEX idx_profile_sys_perm_profile ON profile_system_permission(profile_id);
CREATE INDEX idx_profile_obj_perm_profile ON profile_object_permission(profile_id);
CREATE INDEX idx_profile_obj_perm_collection ON profile_object_permission(collection_id);
CREATE INDEX idx_profile_field_perm_profile ON profile_field_permission(profile_id);
CREATE INDEX idx_profile_field_perm_collection ON profile_field_permission(collection_id);
CREATE INDEX idx_permset_tenant ON permission_set(tenant_id);
CREATE INDEX idx_permset_sys_perm_permset ON permset_system_permission(permission_set_id);
CREATE INDEX idx_permset_obj_perm_permset ON permset_object_permission(permission_set_id);
CREATE INDEX idx_permset_field_perm_permset ON permset_field_permission(permission_set_id);
CREATE INDEX idx_user_permset_user ON user_permission_set(user_id);
CREATE INDEX idx_user_permset_permset ON user_permission_set(permission_set_id);
CREATE INDEX idx_group_permset_group ON group_permission_set(group_id);
CREATE INDEX idx_group_permset_permset ON group_permission_set(permission_set_id);
CREATE INDEX idx_platform_user_profile ON platform_user(profile_id);

-- Seed default profiles for each existing tenant
-- This uses a DO block for procedural logic
DO $$
DECLARE
    t_id VARCHAR(36);
    p_id VARCHAR(36);
    perm_name VARCHAR(100);
    all_permissions TEXT[] := ARRAY[
        'VIEW_SETUP', 'CUSTOMIZE_APPLICATION', 'MANAGE_USERS', 'MANAGE_GROUPS',
        'MANAGE_SHARING', 'MANAGE_WORKFLOWS', 'MANAGE_REPORTS', 'MANAGE_EMAIL_TEMPLATES',
        'MANAGE_CONNECTED_APPS', 'MANAGE_DATA', 'API_ACCESS', 'VIEW_ALL_DATA',
        'MODIFY_ALL_DATA', 'MANAGE_APPROVALS', 'MANAGE_LISTVIEWS'
    ];
BEGIN
    FOR t_id IN SELECT id FROM tenant LOOP
        -- 1. System Administrator (all permissions)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'System Administrator',
                'Full, unrestricted access to all features and data', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name, TRUE);
        END LOOP;

        -- 2. Standard User (API_ACCESS, MANAGE_LISTVIEWS)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'Standard User',
                'Read, create, and edit records in all collections', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name,
                    perm_name IN ('API_ACCESS', 'MANAGE_LISTVIEWS'));
        END LOOP;

        -- 3. Read Only (VIEW_ALL_DATA)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'Read Only',
                'View all records and reports, no create/edit/delete capability', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name,
                    perm_name = 'VIEW_ALL_DATA');
        END LOOP;

        -- 4. Marketing User (API_ACCESS, MANAGE_LISTVIEWS, MANAGE_EMAIL_TEMPLATES)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'Marketing User',
                'Standard User plus manage email templates', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name,
                    perm_name IN ('API_ACCESS', 'MANAGE_LISTVIEWS', 'MANAGE_EMAIL_TEMPLATES'));
        END LOOP;

        -- 5. Contract Manager (API_ACCESS, MANAGE_LISTVIEWS, MANAGE_APPROVALS)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'Contract Manager',
                'Standard User plus manage approval processes', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name,
                    perm_name IN ('API_ACCESS', 'MANAGE_LISTVIEWS', 'MANAGE_APPROVALS'));
        END LOOP;

        -- 6. Solution Manager (VIEW_SETUP, CUSTOMIZE_APPLICATION, MANAGE_REPORTS, MANAGE_WORKFLOWS, MANAGE_LISTVIEWS, API_ACCESS)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'Solution Manager',
                'Customize application structure: collections, fields, layouts, picklists, reports', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name,
                    perm_name IN ('VIEW_SETUP', 'CUSTOMIZE_APPLICATION', 'MANAGE_REPORTS',
                                  'MANAGE_WORKFLOWS', 'MANAGE_LISTVIEWS', 'API_ACCESS'));
        END LOOP;

        -- 7. Minimum Access (no permissions)
        p_id := gen_random_uuid()::text;
        INSERT INTO profile (id, tenant_id, name, description, is_system)
        VALUES (p_id, t_id, 'Minimum Access',
                'Login only, no data access until explicitly granted via Permission Sets', TRUE);
        FOREACH perm_name IN ARRAY all_permissions LOOP
            INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
            VALUES (gen_random_uuid()::text, p_id, perm_name, FALSE);
        END LOOP;

        -- Assign System Administrator to all existing users in this tenant
        UPDATE platform_user
        SET profile_id = (
            SELECT id FROM profile
            WHERE tenant_id = t_id AND name = 'System Administrator'
        )
        WHERE tenant_id = t_id AND profile_id IS NULL;

        -- Create object permissions for all existing collections in this tenant
        -- System Administrator: full access
        INSERT INTO profile_object_permission (id, profile_id, collection_id,
            can_create, can_read, can_edit, can_delete, can_view_all, can_modify_all)
        SELECT gen_random_uuid()::text, p2.id, c.id, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE
        FROM collection c
        CROSS JOIN profile p2
        WHERE c.tenant_id = t_id AND c.active = TRUE
          AND p2.tenant_id = t_id AND p2.name = 'System Administrator';

        -- Standard User, Marketing User, Contract Manager: CRUD, no view_all/modify_all
        INSERT INTO profile_object_permission (id, profile_id, collection_id,
            can_create, can_read, can_edit, can_delete, can_view_all, can_modify_all)
        SELECT gen_random_uuid()::text, p2.id, c.id, TRUE, TRUE, TRUE, TRUE, FALSE, FALSE
        FROM collection c
        CROSS JOIN profile p2
        WHERE c.tenant_id = t_id AND c.active = TRUE
          AND p2.tenant_id = t_id AND p2.name IN ('Standard User', 'Marketing User', 'Contract Manager');

        -- Solution Manager: CRUD + view_all, no modify_all
        INSERT INTO profile_object_permission (id, profile_id, collection_id,
            can_create, can_read, can_edit, can_delete, can_view_all, can_modify_all)
        SELECT gen_random_uuid()::text, p2.id, c.id, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE
        FROM collection c
        CROSS JOIN profile p2
        WHERE c.tenant_id = t_id AND c.active = TRUE
          AND p2.tenant_id = t_id AND p2.name = 'Solution Manager';

        -- Read Only: read + view_all only
        INSERT INTO profile_object_permission (id, profile_id, collection_id,
            can_create, can_read, can_edit, can_delete, can_view_all, can_modify_all)
        SELECT gen_random_uuid()::text, p2.id, c.id, FALSE, TRUE, FALSE, FALSE, TRUE, FALSE
        FROM collection c
        CROSS JOIN profile p2
        WHERE c.tenant_id = t_id AND c.active = TRUE
          AND p2.tenant_id = t_id AND p2.name = 'Read Only';

        -- Minimum Access: no object permissions (intentionally empty)
    END LOOP;
END $$;
