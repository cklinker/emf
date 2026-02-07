-- V9: Add tenant_id foreign key to all tenant-scoped tables
-- Phase 1 Task A6: Tenant Data Isolation
--
-- Strategy:
-- 1. Create a default "platform" tenant for existing data
-- 2. Add tenant_id column as nullable
-- 3. Backfill existing rows with default tenant ID
-- 4. Set tenant_id to NOT NULL
-- 5. Add FK constraint and index
-- 6. Recreate unique constraints to include tenant_id

-- ============================================================================
-- DEFAULT TENANT FOR EXISTING DATA
-- ============================================================================

INSERT INTO tenant (id, slug, name, edition, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Organization', 'ENTERPRISE', 'ACTIVE', NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;

-- ============================================================================
-- SERVICE TABLE
-- ============================================================================

ALTER TABLE service ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE service SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE service ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE service ADD CONSTRAINT fk_service_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_service_tenant ON service(tenant_id);

-- Drop old unique constraint on name (auto-generated name from inline UNIQUE)
-- PostgreSQL auto-names it service_name_key
ALTER TABLE service DROP CONSTRAINT IF EXISTS service_name_key;
ALTER TABLE service ADD CONSTRAINT uq_service_tenant_name UNIQUE (tenant_id, name);

-- ============================================================================
-- COLLECTION TABLE
-- ============================================================================

ALTER TABLE collection ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE collection SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE collection ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE collection ADD CONSTRAINT fk_collection_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_collection_tenant ON collection(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE collection DROP CONSTRAINT IF EXISTS uk_collection_name;
ALTER TABLE collection ADD CONSTRAINT uq_collection_tenant_name UNIQUE (tenant_id, name);

-- ============================================================================
-- ROLE TABLE
-- ============================================================================

ALTER TABLE role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE role SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE role ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE role ADD CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_role_tenant ON role(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE role DROP CONSTRAINT IF EXISTS uk_role_name;
ALTER TABLE role ADD CONSTRAINT uq_role_tenant_name UNIQUE (tenant_id, name);

-- ============================================================================
-- POLICY TABLE
-- ============================================================================

ALTER TABLE policy ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE policy SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE policy ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE policy ADD CONSTRAINT fk_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_policy_tenant ON policy(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE policy DROP CONSTRAINT IF EXISTS uk_policy_name;
ALTER TABLE policy ADD CONSTRAINT uq_policy_tenant_name UNIQUE (tenant_id, name);

-- ============================================================================
-- OIDC_PROVIDER TABLE
-- ============================================================================

ALTER TABLE oidc_provider ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE oidc_provider SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE oidc_provider ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE oidc_provider ADD CONSTRAINT fk_oidc_provider_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_oidc_provider_tenant ON oidc_provider(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE oidc_provider DROP CONSTRAINT IF EXISTS uk_oidc_provider_name;
ALTER TABLE oidc_provider ADD CONSTRAINT uq_oidc_provider_tenant_name UNIQUE (tenant_id, name);

-- ============================================================================
-- UI_PAGE TABLE
-- ============================================================================

ALTER TABLE ui_page ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE ui_page SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE ui_page ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ui_page ADD CONSTRAINT fk_ui_page_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_ui_page_tenant ON ui_page(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE ui_page DROP CONSTRAINT IF EXISTS uk_ui_page_path;
ALTER TABLE ui_page ADD CONSTRAINT uq_ui_page_tenant_path UNIQUE (tenant_id, path);

-- ============================================================================
-- UI_MENU TABLE
-- ============================================================================

ALTER TABLE ui_menu ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE ui_menu SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE ui_menu ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ui_menu ADD CONSTRAINT fk_ui_menu_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_ui_menu_tenant ON ui_menu(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE ui_menu DROP CONSTRAINT IF EXISTS uk_ui_menu_name;
ALTER TABLE ui_menu ADD CONSTRAINT uq_ui_menu_tenant_name UNIQUE (tenant_id, name);

-- ============================================================================
-- PACKAGE TABLE
-- ============================================================================

ALTER TABLE package ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE package SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE package ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE package ADD CONSTRAINT fk_package_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_package_tenant ON package(tenant_id);

-- Recreate unique constraint with tenant_id
ALTER TABLE package DROP CONSTRAINT IF EXISTS uk_package_name_version;
ALTER TABLE package ADD CONSTRAINT uq_package_tenant_name_version UNIQUE (tenant_id, name, version);

-- ============================================================================
-- MIGRATION_RUN TABLE
-- ============================================================================

ALTER TABLE migration_run ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
UPDATE migration_run SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE migration_run ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE migration_run ADD CONSTRAINT fk_migration_run_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_migration_run_tenant ON migration_run(tenant_id);
