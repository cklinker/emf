-- ============================================================================
-- V95: Replace hard-coded roles with profile-based permission mapping
--
-- 1. Adds MANAGE_TENANTS system permission to all profiles
-- 2. Adds groups_claim and groups_profile_mapping columns to oidc_provider
-- 3. Converts existing roles_mapping to groups_profile_mapping
-- ============================================================================

-- 1. Add MANAGE_TENANTS system permission to all profiles
-- System Administrator gets it granted; all others get it denied.
INSERT INTO profile_system_permission (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT
    gen_random_uuid()::text,
    p.tenant_id,
    p.id,
    'MANAGE_TENANTS',
    CASE WHEN p.name = 'System Administrator' THEN TRUE ELSE FALSE END,
    NOW(),
    NOW()
FROM profile p
WHERE NOT EXISTS (
    SELECT 1 FROM profile_system_permission psp
    WHERE psp.profile_id = p.id AND psp.permission_name = 'MANAGE_TENANTS'
);

-- 2. Add new columns to oidc_provider (keep old columns for backward compatibility)
ALTER TABLE oidc_provider ADD COLUMN IF NOT EXISTS groups_claim VARCHAR(200);
ALTER TABLE oidc_provider ADD COLUMN IF NOT EXISTS groups_profile_mapping TEXT;

-- 3. Copy roles_claim to groups_claim where not already set
UPDATE oidc_provider
SET groups_claim = roles_claim
WHERE groups_claim IS NULL AND roles_claim IS NOT NULL;

-- 4. Convert roles_mapping values to profile names in groups_profile_mapping
-- Maps: ADMIN/PLATFORM_ADMIN -> System Administrator, USER -> Standard User,
--        VIEWER -> Read Only, DEVELOPER -> Solution Manager
UPDATE oidc_provider
SET groups_profile_mapping = (
    SELECT jsonb_object_agg(
        kv.key,
        CASE kv.value #>> '{}'
            WHEN 'ADMIN' THEN 'System Administrator'
            WHEN 'PLATFORM_ADMIN' THEN 'System Administrator'
            WHEN 'USER' THEN 'Standard User'
            WHEN 'VIEWER' THEN 'Read Only'
            WHEN 'DEVELOPER' THEN 'Solution Manager'
            ELSE 'Standard User'
        END
    )::text
    FROM jsonb_each(roles_mapping::jsonb) AS kv
)
WHERE roles_mapping IS NOT NULL
  AND groups_profile_mapping IS NULL;

-- 5. Add JSON validity constraint on groups_profile_mapping
ALTER TABLE oidc_provider
    ADD CONSTRAINT chk_oidc_provider_groups_profile_mapping_json
    CHECK (groups_profile_mapping IS NULL OR groups_profile_mapping::jsonb IS NOT NULL);
