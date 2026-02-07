-- V12: Record-Level Sharing Model
-- Creates tables for org-wide defaults, sharing rules, record shares, and user groups.
-- Adds role hierarchy support to the role table.

-- Organization-wide default per collection
CREATE TABLE org_wide_default (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    internal_access VARCHAR(20) NOT NULL DEFAULT 'PUBLIC_READ_WRITE',
    external_access VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_owd UNIQUE (tenant_id, collection_id),
    CONSTRAINT chk_internal CHECK (internal_access IN ('PRIVATE','PUBLIC_READ','PUBLIC_READ_WRITE')),
    CONSTRAINT chk_external CHECK (external_access IN ('PRIVATE','PUBLIC_READ','PUBLIC_READ_WRITE'))
);

-- Role hierarchy for record access inheritance
ALTER TABLE role ADD COLUMN parent_role_id VARCHAR(36) REFERENCES role(id);
ALTER TABLE role ADD COLUMN hierarchy_level INTEGER DEFAULT 0;
CREATE INDEX idx_role_parent ON role(parent_role_id);

-- Sharing rule: criteria-based or owner-based
CREATE TABLE sharing_rule (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    shared_from VARCHAR(36),
    shared_to VARCHAR(36) NOT NULL,
    shared_to_type VARCHAR(20) NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    criteria JSONB,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_rule_type CHECK (rule_type IN ('OWNER_BASED','CRITERIA_BASED')),
    CONSTRAINT chk_shared_to_type CHECK (shared_to_type IN ('ROLE','GROUP','QUEUE')),
    CONSTRAINT chk_access_level CHECK (access_level IN ('READ','READ_WRITE'))
);
CREATE INDEX idx_sharing_rule_collection ON sharing_rule(collection_id);
CREATE INDEX idx_sharing_rule_tenant ON sharing_rule(tenant_id);

-- Manual sharing: record owner grants access to specific users/groups
CREATE TABLE record_share (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    record_id VARCHAR(36) NOT NULL,
    shared_with_id VARCHAR(36) NOT NULL,
    shared_with_type VARCHAR(20) NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    reason VARCHAR(20) DEFAULT 'MANUAL',
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_share_with_type CHECK (shared_with_type IN ('USER','GROUP','ROLE')),
    CONSTRAINT chk_share_access CHECK (access_level IN ('READ','READ_WRITE')),
    CONSTRAINT chk_share_reason CHECK (reason IN ('MANUAL','RULE','TEAM','TERRITORY')),
    CONSTRAINT uq_record_share UNIQUE (collection_id, record_id, shared_with_id, shared_with_type)
);
CREATE INDEX idx_share_record ON record_share(collection_id, record_id);
CREATE INDEX idx_share_user ON record_share(shared_with_id, shared_with_type);

-- Groups (public groups for sharing)
CREATE TABLE user_group (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    group_type VARCHAR(20) DEFAULT 'PUBLIC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_group_type CHECK (group_type IN ('PUBLIC','QUEUE')),
    CONSTRAINT uq_group_name UNIQUE (tenant_id, name)
);

CREATE TABLE user_group_member (
    group_id VARCHAR(36) NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_group_member_user ON user_group_member(user_id);
