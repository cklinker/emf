-- =============================================================================
-- V101: Add tables for Kelta Auth Server (Internal OIDC Provider)
-- =============================================================================

-- User credentials for internal IdP (password-based authentication)
CREATE TABLE user_credential (
    id VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    user_id VARCHAR(36) NOT NULL,
    password_hash VARCHAR(500) NOT NULL,
    password_changed_at TIMESTAMP WITH TIME ZONE,
    force_change_on_login BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    reset_token VARCHAR(500),
    reset_token_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_credential PRIMARY KEY (id),
    CONSTRAINT fk_user_credential_user FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_credential_user UNIQUE (user_id)
);

CREATE INDEX idx_user_credential_reset_token ON user_credential(reset_token) WHERE reset_token IS NOT NULL;

-- OAuth2 Registered Client (connected apps like Superset)
-- Follows Spring Authorization Server JDBC schema
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMP WITH TIME ZONE,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id)
);

-- OAuth2 Authorization (authorization codes, access tokens, refresh tokens)
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMP WITH TIME ZONE,
    authorization_code_expires_at TIMESTAMP WITH TIME ZONE,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMP WITH TIME ZONE,
    access_token_expires_at TIMESTAMP WITH TIME ZONE,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_expires_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_metadata TEXT,
    oidc_id_token_claims TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    refresh_token_expires_at TIMESTAMP WITH TIME ZONE,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMP WITH TIME ZONE,
    user_code_expires_at TIMESTAMP WITH TIME ZONE,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMP WITH TIME ZONE,
    device_code_expires_at TIMESTAMP WITH TIME ZONE,
    device_code_metadata TEXT,
    CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id)
);

-- OAuth2 Authorization Consent
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name)
);

-- Register kelta-auth as an available OIDC provider for tenants using the internal IdP
-- Tenants can be assigned this provider when they don't bring their own
INSERT INTO oidc_provider (id, name, issuer, jwks_uri, client_id, active, created_at)
SELECT 'kelta-internal-idp',
       'Kelta Platform (Internal)',
       'https://auth.rzware.com',
       'https://auth.rzware.com/oauth2/jwks',
       'kelta-platform',
       true,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM oidc_provider WHERE id = 'kelta-internal-idp');
