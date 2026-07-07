-- Seed local Keycloak OIDC provider for development
-- This migration only runs in local/dev environments

INSERT INTO oidc_provider (id, name, issuer, jwks_uri, client_id, active, created_at, updated_at)
VALUES (
    'local-keycloak',
    'Keycloak Local',
    'http://localhost:8180/realms/emf',
    'http://localhost:8180/realms/emf/protocol/openid-connect/certs',
    'emf-ui',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;
