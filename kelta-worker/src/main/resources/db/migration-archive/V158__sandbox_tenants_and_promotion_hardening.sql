-- ============================================================================
-- V157: Sandbox tenants + metadata promotion hardening
-- ============================================================================
-- 1. tenant.parent_tenant_id       — a sandbox is a real tenant linked to its
--                                    production (parent) tenant
-- 2. environment sandbox/remote    — environment rows point at a local sandbox
--                                    tenant OR describe a remote push target
-- 3. environment_promotion         — conflict mode + target-side restore snapshot
-- 4. promotion_item.tenant_id      — V85/V86 child-table pattern
-- 5. platform_instance             — stable per-installation identity for
--                                    cross-cluster package provenance
-- 6. RLS retrofit                  — the V122 tables were never RLS-enabled
-- 7. MANAGE_SANDBOXES backfill     — granted to System Administrator profiles
-- ============================================================================

-- 1. Tenant parent link ------------------------------------------------------
ALTER TABLE tenant ADD COLUMN parent_tenant_id VARCHAR(36) REFERENCES tenant(id);
CREATE INDEX idx_tenant_parent ON tenant (parent_tenant_id)
    WHERE parent_tenant_id IS NOT NULL;

-- 2. Environment: local sandbox tenant pointer OR remote target descriptor ---
ALTER TABLE environment ADD COLUMN sandbox_tenant_id VARCHAR(36) REFERENCES tenant(id);
ALTER TABLE environment ADD COLUMN remote_base_url VARCHAR(500);
ALTER TABLE environment ADD COLUMN remote_tenant_slug VARCHAR(63);
ALTER TABLE environment ADD COLUMN credential_ref VARCHAR(200);

CREATE UNIQUE INDEX idx_environment_sandbox_tenant
    ON environment (sandbox_tenant_id) WHERE sandbox_tenant_id IS NOT NULL;

-- A non-production environment is either local (sandbox tenant) or remote
-- (base URL), never both. Production rows represent the tenant itself.
ALTER TABLE environment ADD CONSTRAINT chk_env_locality
    CHECK (type = 'PRODUCTION'
           OR NOT (sandbox_tenant_id IS NOT NULL AND remote_base_url IS NOT NULL));

ALTER TABLE environment DROP CONSTRAINT chk_env_status;
ALTER TABLE environment ADD CONSTRAINT chk_env_status
    CHECK (status IN ('CREATING', 'ACTIVE', 'REFRESHING', 'ARCHIVED', 'FAILED'));

-- 3. Promotion: conflict mode + target-side restore snapshot -----------------
ALTER TABLE environment_promotion ADD COLUMN conflict_mode VARCHAR(10) NOT NULL DEFAULT 'SKIP';
ALTER TABLE environment_promotion ADD CONSTRAINT chk_promo_conflict
    CHECK (conflict_mode IN ('SKIP', 'OVERWRITE'));
ALTER TABLE environment_promotion ADD COLUMN target_snapshot_id VARCHAR(36)
    REFERENCES metadata_snapshot(id);

-- 4. promotion_item gets tenant_id (V85/V86 child-table pattern) -------------
ALTER TABLE promotion_item ADD COLUMN tenant_id VARCHAR(36);
UPDATE promotion_item pi
SET tenant_id = ep.tenant_id
FROM environment_promotion ep
WHERE pi.promotion_id = ep.id AND pi.tenant_id IS NULL;
ALTER TABLE promotion_item ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_promotion_item_tenant ON promotion_item (tenant_id);

-- promotion_item natural-key selection (name-keyed selective promotions)
ALTER TABLE promotion_item ALTER COLUMN item_id DROP NOT NULL;

-- 5. Stable installation identity for package provenance ---------------------
CREATE TABLE platform_instance (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
INSERT INTO platform_instance (id) VALUES (gen_random_uuid()::text);

-- 6. RLS retrofit: the V122 tables were created without RLS ------------------
DO $$
DECLARE
    tbl TEXT;
    sandbox_tables TEXT[] := ARRAY[
        'environment',
        'metadata_snapshot',
        'environment_promotion',
        'promotion_item'
    ];
BEGIN
    FOREACH tbl IN ARRAY sandbox_tables
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = tbl AND column_name = 'tenant_id'
        ) THEN
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
            EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);

            EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);
            EXECUTE format('DROP POLICY IF EXISTS admin_bypass ON %I', tbl);

            EXECUTE format(
                'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant_id'', true))',
                tbl
            );

            EXECUTE format(
                'CREATE POLICY admin_bypass ON %I USING (current_setting(''app.current_tenant_id'', true) = '''')',
                tbl
            );

            RAISE NOTICE 'Enabled RLS on table: %', tbl;
        ELSE
            RAISE NOTICE 'Skipping table % (not found or no tenant_id column)', tbl;
        END IF;
    END LOOP;
END $$;

-- 7. MANAGE_SANDBOXES: seed for existing profiles (granted only to admins) ---
INSERT INTO profile_system_permission (id, tenant_id, profile_id, permission_name, granted)
SELECT gen_random_uuid()::text, p.tenant_id, p.id, 'MANAGE_SANDBOXES',
       (p.name = 'System Administrator')
FROM profile p
WHERE NOT EXISTS (
    SELECT 1 FROM profile_system_permission s
    WHERE s.profile_id = p.id AND s.permission_name = 'MANAGE_SANDBOXES'
);
