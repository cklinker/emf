-- V126: Credential vault foundation
-- Adds tenant-managed credentials (API keys, OAuth tokens, SMTP, etc.) with
-- AES-256-GCM envelope encryption for secret material.
--
-- Encrypted format: enc:v1:<iv>:<ciphertext> in data_enc.
-- Non-secret config (host, port, scopes, urls) lives in plaintext metadata JSONB
-- so the listing UI can render details without decrypting.

CREATE TABLE credential (
    id                VARCHAR(36)              PRIMARY KEY,
    tenant_id         VARCHAR(36)              NOT NULL REFERENCES tenant(id),
    name              VARCHAR(200)             NOT NULL,
    display_name      VARCHAR(200),
    description       VARCHAR(500),
    type              VARCHAR(50)              NOT NULL,
    provider_template VARCHAR(100),
    data_enc          TEXT                     NOT NULL,
    metadata          JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    last_test_at      TIMESTAMP WITH TIME ZONE,
    last_test_status  VARCHAR(20),
    last_test_error   TEXT,
    active            BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_by        VARCHAR(36),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(36),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_credential_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_credential_tenant      ON credential(tenant_id);
CREATE INDEX idx_credential_tenant_type ON credential(tenant_id, type);
CREATE INDEX idx_credential_provider_template
    ON credential(provider_template) WHERE provider_template IS NOT NULL;

COMMENT ON COLUMN credential.data_enc IS
    'AES-256-GCM encrypted JSON blob of secret fields (format: enc:v1:<iv>:<ct>)';
COMMENT ON COLUMN credential.metadata IS
    'Non-secret credential configuration (host, port, scopes, urls, etc.)';
COMMENT ON COLUMN credential.type IS
    'api_key | bearer_token | basic_auth | oauth2_client_credentials | '
    'oauth2_authorization_code | smtp | custom';

-- 1:1 with credential — separate row so the OAuth refresh path doesn't
-- contend with credential edits, and so token rotation has its own audit trail.
CREATE TABLE credential_oauth_token (
    id                    VARCHAR(36)              PRIMARY KEY,
    credential_id         VARCHAR(36)              NOT NULL UNIQUE
                              REFERENCES credential(id) ON DELETE CASCADE,
    tenant_id             VARCHAR(36)              NOT NULL REFERENCES tenant(id),
    access_token_enc      TEXT                     NOT NULL,
    refresh_token_enc     TEXT,
    token_type            VARCHAR(40),
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    refreshed_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_failure_count INTEGER                  NOT NULL DEFAULT 0,
    last_refresh_error    TEXT,
    scope                 TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credential_oauth_token_credential
    ON credential_oauth_token(credential_id);
CREATE INDEX idx_credential_oauth_token_tenant
    ON credential_oauth_token(tenant_id);
CREATE INDEX idx_credential_oauth_token_expires_at
    ON credential_oauth_token(expires_at);

-- Row-Level Security — mirrors V77 pattern.
-- Connection-init default of empty string for app.current_tenant_id allows
-- migrations and platform-internal operations to bypass the policy.

ALTER TABLE credential ENABLE ROW LEVEL SECURITY;
ALTER TABLE credential FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON credential;
DROP POLICY IF EXISTS admin_bypass     ON credential;
CREATE POLICY tenant_isolation ON credential
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON credential
    USING (current_setting('app.current_tenant_id', true) = '');

ALTER TABLE credential_oauth_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE credential_oauth_token FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON credential_oauth_token;
DROP POLICY IF EXISTS admin_bypass     ON credential_oauth_token;
CREATE POLICY tenant_isolation ON credential_oauth_token
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON credential_oauth_token
    USING (current_setting('app.current_tenant_id', true) = '');

-- Grant the new credential-management permissions to existing System Administrator
-- profiles. New tenants pick up the same grants via TenantProvisioningHook.
DO $$
DECLARE
    p_id VARCHAR(36);
BEGIN
    FOR p_id IN
        SELECT id FROM profile WHERE name = 'System Administrator' AND is_system = TRUE
    LOOP
        INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
        VALUES (gen_random_uuid()::text, p_id, 'MANAGE_CREDENTIALS', TRUE)
        ON CONFLICT DO NOTHING;
        INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
        VALUES (gen_random_uuid()::text, p_id, 'VIEW_CREDENTIALS', TRUE)
        ON CONFLICT DO NOTHING;
    END LOOP;

    -- Standard users can VIEW credentials (so they can pick one in a flow)
    -- but cannot manage them.
    FOR p_id IN
        SELECT id FROM profile WHERE name = 'Standard User' AND is_system = TRUE
    LOOP
        INSERT INTO profile_system_permission (id, profile_id, permission_name, granted)
        VALUES (gen_random_uuid()::text, p_id, 'VIEW_CREDENTIALS', TRUE)
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;
