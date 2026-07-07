-- V118: Add OAuth authorization code flow support to connected apps
-- Adds grant_types to control which OAuth flows a connected app supports,
-- require_pkce flag for public clients, and consent_required flag.

ALTER TABLE connected_app
    ADD COLUMN grant_types    JSONB   NOT NULL DEFAULT '["client_credentials"]',
    ADD COLUMN require_pkce   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN consent_required BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN connected_app.grant_types IS 'Allowed OAuth2 grant types: client_credentials, authorization_code';
COMMENT ON COLUMN connected_app.require_pkce IS 'Whether PKCE is required (recommended for public/SPA clients)';
COMMENT ON COLUMN connected_app.consent_required IS 'Whether user consent screen is shown during authorization';
