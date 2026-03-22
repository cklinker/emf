-- V103: Add OIDC federation fields for identity brokering
-- Adds encrypted client secret storage and OIDC Discovery endpoint overrides

-- Encrypted client secret (AES-256-GCM envelope encryption)
-- Format: enc:v1:<iv>:<ciphertext> when encrypted, NULL when not set
ALTER TABLE oidc_provider ADD COLUMN client_secret_enc TEXT;

-- OIDC Discovery endpoint overrides
-- When NULL, endpoints are auto-discovered from the issuer's .well-known/openid-configuration
-- When set, overrides take precedence over discovered values
ALTER TABLE oidc_provider ADD COLUMN authorization_uri VARCHAR(500);
ALTER TABLE oidc_provider ADD COLUMN token_uri VARCHAR(500);
ALTER TABLE oidc_provider ADD COLUMN userinfo_uri VARCHAR(500);
ALTER TABLE oidc_provider ADD COLUMN end_session_uri VARCHAR(500);

-- Discovery metadata
-- Tracks whether Discovery was successful for this provider
ALTER TABLE oidc_provider ADD COLUMN discovery_status VARCHAR(20) DEFAULT 'unknown';

COMMENT ON COLUMN oidc_provider.client_secret_enc IS 'AES-256-GCM encrypted client secret for token exchange with external IdPs';
COMMENT ON COLUMN oidc_provider.authorization_uri IS 'Override for OIDC authorization endpoint (auto-discovered from issuer when NULL)';
COMMENT ON COLUMN oidc_provider.token_uri IS 'Override for OIDC token endpoint (auto-discovered from issuer when NULL)';
COMMENT ON COLUMN oidc_provider.userinfo_uri IS 'Override for OIDC userinfo endpoint (auto-discovered from issuer when NULL)';
COMMENT ON COLUMN oidc_provider.end_session_uri IS 'Override for OIDC end session endpoint (auto-discovered from issuer when NULL)';
COMMENT ON COLUMN oidc_provider.discovery_status IS 'OIDC Discovery status: discovered, manual, error, unknown';
