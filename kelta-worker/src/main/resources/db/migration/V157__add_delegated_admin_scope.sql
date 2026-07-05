-- V157: delegated_admin_scope — delegated administration (scoped user management).
-- A full admin defines scopes; each scope lists delegated users (delegated_user_ids) who may
-- manage users whose profile is in manageable_profile_ids, assign permission sets from
-- assignable_permission_set_ids, and use the capability booleans. Enforcement is read-fresh in
-- DelegatedAdminService (no in-memory cache, no NATS broadcast — quick_action precedent);
-- scope CRUD via DelegatedAdminScopeController (MANAGE_DELEGATED_ADMINS) and guarded by
-- DelegatedAdminScopeValidationHook (no delegating admin-of-admins).

CREATE TABLE IF NOT EXISTS delegated_admin_scope (
    id                            VARCHAR(36)  PRIMARY KEY,
    tenant_id                     VARCHAR(36)  NOT NULL,
    name                          VARCHAR(200) NOT NULL,
    description                   VARCHAR(500),
    active                        BOOLEAN      NOT NULL DEFAULT TRUE,
    delegated_user_ids            JSONB        NOT NULL DEFAULT '[]',
    manageable_profile_ids        JSONB        NOT NULL DEFAULT '[]',
    assignable_permission_set_ids JSONB        NOT NULL DEFAULT '[]',
    can_create_users              BOOLEAN      NOT NULL DEFAULT FALSE,
    can_deactivate_users          BOOLEAN      NOT NULL DEFAULT FALSE,
    can_reset_passwords           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by                    VARCHAR(36),
    updated_by                    VARCHAR(36)
);

-- Hot lookup: active scopes for a tenant (DelegatedAdminService.effectiveScope reads all
-- active rows and filters delegated_user_ids containment in SQL).
CREATE INDEX IF NOT EXISTS idx_delegated_admin_scope_lookup
    ON delegated_admin_scope (tenant_id, active);

-- RLS: tenant isolation + admin bypass (empty tenant setting), matching quick_action (V151).
ALTER TABLE delegated_admin_scope ENABLE ROW LEVEL SECURITY;
ALTER TABLE delegated_admin_scope FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON delegated_admin_scope;
DROP POLICY IF EXISTS admin_bypass     ON delegated_admin_scope;
CREATE POLICY tenant_isolation ON delegated_admin_scope
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON delegated_admin_scope
    USING (current_setting('app.current_tenant_id', true) = '');

-- ---------------------------------------------------------------------------
-- Permission: MANAGE_DELEGATED_ADMINS
-- Clone the existing MANAGE_USERS grants onto MANAGE_DELEGATED_ADMINS for every existing
-- profile (whoever administers users decides who may delegate), mirroring the
-- MANAGE_CAMPAIGNS seeding in V152. New tenants seed it via TenantProvisioningHook.
-- ---------------------------------------------------------------------------
INSERT INTO profile_system_permission (id, tenant_id, profile_id, permission_name, granted)
SELECT gen_random_uuid()::text, p.tenant_id, p.profile_id, 'MANAGE_DELEGATED_ADMINS', p.granted
FROM profile_system_permission p
WHERE p.permission_name = 'MANAGE_USERS'
  AND NOT EXISTS (
      SELECT 1 FROM profile_system_permission x
      WHERE x.profile_id = p.profile_id
        AND x.permission_name = 'MANAGE_DELEGATED_ADMINS'
  );
