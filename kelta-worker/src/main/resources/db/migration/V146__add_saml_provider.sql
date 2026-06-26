-- Rec 8: per-tenant SAML 2.0 identity-provider configuration.
--
-- Worker-owned config (mirrors oidc_provider); kelta-auth reads it (via WorkerClient)
-- to build per-tenant SAML relying-party registrations for the SSO flow (separate
-- slice). One enabled provider lets a tenant federate logins to its own IdP.
CREATE TABLE saml_provider (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(36) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    registration_id   VARCHAR(100) NOT NULL,
    idp_entity_id     VARCHAR(500) NOT NULL,
    sso_url           VARCHAR(500) NOT NULL,
    idp_certificate   TEXT NOT NULL,
    name_id_format    VARCHAR(200),
    email_attribute   VARCHAR(200),
    profile_attribute VARCHAR(200),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    UNIQUE (tenant_id, registration_id)
);

ALTER TABLE saml_provider ENABLE ROW LEVEL SECURITY;
ALTER TABLE saml_provider FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON saml_provider;
DROP POLICY IF EXISTS admin_bypass ON saml_provider;
CREATE POLICY tenant_isolation ON saml_provider
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON saml_provider
    USING (current_setting('app.current_tenant_id', true) = '');
