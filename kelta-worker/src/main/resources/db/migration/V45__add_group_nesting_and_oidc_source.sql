-- V45: Extend user_group with OIDC source tracking and nestable group membership
-- Part of Security Consolidation Phase S1

-- Add source tracking to user_group (MANUAL = hand-created, OIDC = synced from OIDC provider)
ALTER TABLE user_group ADD COLUMN source VARCHAR(20) DEFAULT 'MANUAL' NOT NULL;
ALTER TABLE user_group ADD COLUMN oidc_group_name VARCHAR(200);

-- Update group_type constraint to include SYSTEM type
ALTER TABLE user_group DROP CONSTRAINT IF EXISTS chk_group_type;
ALTER TABLE user_group ADD CONSTRAINT chk_group_type
    CHECK (group_type IN ('PUBLIC', 'QUEUE', 'SYSTEM'));

-- Index for efficient OIDC group lookup
CREATE INDEX idx_user_group_oidc ON user_group(tenant_id, oidc_group_name)
    WHERE oidc_group_name IS NOT NULL;
CREATE INDEX idx_user_group_source ON user_group(tenant_id, source);

-- New group_membership table supporting both USER and GROUP members (nesting)
CREATE TABLE group_membership (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    member_type VARCHAR(10) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_member_type CHECK (member_type IN ('USER', 'GROUP')),
    CONSTRAINT uq_group_membership UNIQUE (group_id, member_type, member_id)
);
CREATE INDEX idx_gm_group ON group_membership(group_id);
CREATE INDEX idx_gm_member ON group_membership(member_type, member_id);

-- Migrate existing user_group_member data into group_membership
INSERT INTO group_membership (id, group_id, member_type, member_id, created_at)
SELECT gen_random_uuid()::varchar, group_id, 'USER', user_id, NOW()
FROM user_group_member
ON CONFLICT DO NOTHING;

-- Create well-known "All Authenticated Users" system group per tenant
INSERT INTO user_group (id, tenant_id, name, description, group_type, source, created_at, updated_at)
SELECT
    '00000000-0000-0000-0000-' || LPAD(SUBSTRING(t.id, 1, 12), 12, '0'),
    t.id,
    'All Authenticated Users',
    'System group containing all authenticated users',
    'SYSTEM',
    'SYSTEM',
    NOW(),
    NOW()
FROM tenant t
ON CONFLICT DO NOTHING;
