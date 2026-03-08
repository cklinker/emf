-- V11: Create permission system tables
-- Part of Phase 1 Stream C: Profile and Permission System

-- ============================================================================
-- PROFILE TABLE
-- ============================================================================

CREATE TABLE profile (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    is_system       BOOLEAN      DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_profile_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_profile_tenant ON profile(tenant_id);

-- ============================================================================
-- OBJECT_PERMISSION TABLE
-- Per-collection CRUD permissions assigned to a profile
-- ============================================================================

CREATE TABLE object_permission (
    id              VARCHAR(36)  PRIMARY KEY,
    profile_id      VARCHAR(36)  NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id   VARCHAR(36)  NOT NULL,
    can_create      BOOLEAN      DEFAULT false,
    can_read        BOOLEAN      DEFAULT false,
    can_edit        BOOLEAN      DEFAULT false,
    can_delete      BOOLEAN      DEFAULT false,
    can_view_all    BOOLEAN      DEFAULT false,
    can_modify_all  BOOLEAN      DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_objperm_profile_collection UNIQUE (profile_id, collection_id)
);

CREATE INDEX idx_objperm_profile    ON object_permission(profile_id);
CREATE INDEX idx_objperm_collection ON object_permission(collection_id);

-- ============================================================================
-- FIELD_PERMISSION TABLE
-- Per-field visibility permissions assigned to a profile
-- ============================================================================

CREATE TABLE field_permission (
    id              VARCHAR(36)  PRIMARY KEY,
    profile_id      VARCHAR(36)  NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    field_id        VARCHAR(36)  NOT NULL,
    visibility      VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fieldperm_profile_field UNIQUE (profile_id, field_id),
    CONSTRAINT chk_field_visibility CHECK (visibility IN ('VISIBLE', 'READ_ONLY', 'HIDDEN'))
);

CREATE INDEX idx_fieldperm_profile ON field_permission(profile_id);
CREATE INDEX idx_fieldperm_field   ON field_permission(field_id);

-- ============================================================================
-- SYSTEM_PERMISSION TABLE
-- System-wide permissions assigned to a profile
-- ============================================================================

CREATE TABLE system_permission (
    id              VARCHAR(36)  PRIMARY KEY,
    profile_id      VARCHAR(36)  NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    permission_key  VARCHAR(100) NOT NULL,
    granted         BOOLEAN      DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sysperm_profile_key UNIQUE (profile_id, permission_key),
    CONSTRAINT chk_permission_key CHECK (permission_key IN (
        'MANAGE_USERS', 'CUSTOMIZE_APPLICATION', 'MANAGE_SHARING',
        'MANAGE_WORKFLOWS', 'MANAGE_REPORTS', 'API_ACCESS',
        'MANAGE_INTEGRATIONS', 'MANAGE_DATA', 'VIEW_SETUP',
        'MANAGE_SANDBOX', 'VIEW_ALL_DATA', 'MODIFY_ALL_DATA'
    ))
);

CREATE INDEX idx_sysperm_profile ON system_permission(profile_id);

-- ============================================================================
-- PERMISSION_SET TABLE
-- Additive permission sets that can be assigned to users
-- ============================================================================

CREATE TABLE permission_set (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    is_system       BOOLEAN      DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_permset_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_permset_tenant ON permission_set(tenant_id);

-- ============================================================================
-- PERMSET_OBJECT_PERMISSION TABLE
-- Per-collection CRUD permissions within a permission set
-- ============================================================================

CREATE TABLE permset_object_permission (
    id                VARCHAR(36)  PRIMARY KEY,
    permission_set_id VARCHAR(36)  NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    collection_id     VARCHAR(36)  NOT NULL,
    can_create        BOOLEAN      DEFAULT false,
    can_read          BOOLEAN      DEFAULT false,
    can_edit          BOOLEAN      DEFAULT false,
    can_delete        BOOLEAN      DEFAULT false,
    can_view_all      BOOLEAN      DEFAULT false,
    can_modify_all    BOOLEAN      DEFAULT false,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_psobjperm_permset_collection UNIQUE (permission_set_id, collection_id)
);

CREATE INDEX idx_psobjperm_permset ON permset_object_permission(permission_set_id);

-- ============================================================================
-- PERMSET_FIELD_PERMISSION TABLE
-- Per-field visibility within a permission set
-- ============================================================================

CREATE TABLE permset_field_permission (
    id                VARCHAR(36)  PRIMARY KEY,
    permission_set_id VARCHAR(36)  NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    field_id          VARCHAR(36)  NOT NULL,
    visibility        VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_psfieldperm_permset_field UNIQUE (permission_set_id, field_id),
    CONSTRAINT chk_ps_field_visibility CHECK (visibility IN ('VISIBLE', 'READ_ONLY', 'HIDDEN'))
);

CREATE INDEX idx_psfieldperm_permset ON permset_field_permission(permission_set_id);

-- ============================================================================
-- PERMSET_SYSTEM_PERMISSION TABLE
-- System permissions within a permission set
-- ============================================================================

CREATE TABLE permset_system_permission (
    id                VARCHAR(36)  PRIMARY KEY,
    permission_set_id VARCHAR(36)  NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    permission_key    VARCHAR(100) NOT NULL,
    granted           BOOLEAN      DEFAULT false,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pssysperm_permset_key UNIQUE (permission_set_id, permission_key),
    CONSTRAINT chk_ps_permission_key CHECK (permission_key IN (
        'MANAGE_USERS', 'CUSTOMIZE_APPLICATION', 'MANAGE_SHARING',
        'MANAGE_WORKFLOWS', 'MANAGE_REPORTS', 'API_ACCESS',
        'MANAGE_INTEGRATIONS', 'MANAGE_DATA', 'VIEW_SETUP',
        'MANAGE_SANDBOX', 'VIEW_ALL_DATA', 'MODIFY_ALL_DATA'
    ))
);

CREATE INDEX idx_pssysperm_permset ON permset_system_permission(permission_set_id);

-- ============================================================================
-- USER_PERMISSION_SET TABLE
-- Junction table linking users to permission sets
-- ============================================================================

CREATE TABLE user_permission_set (
    user_id           VARCHAR(36)  NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    permission_set_id VARCHAR(36)  NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, permission_set_id)
);

CREATE INDEX idx_userpermset_user    ON user_permission_set(user_id);
CREATE INDEX idx_userpermset_permset ON user_permission_set(permission_set_id);

-- ============================================================================
-- ADD FOREIGN KEY FROM platform_user.profile_id TO profile
-- ============================================================================

ALTER TABLE platform_user
    ADD CONSTRAINT fk_user_profile FOREIGN KEY (profile_id) REFERENCES profile(id);
