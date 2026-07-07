-- ============================================================================
-- V96: Register kelta-api Authentik application as OIDC provider
--
-- The E2E service account uses client_credentials grant from the Authentik
-- "kelta-api" application, which has a different issuer URL than the "emf"
-- application used for browser-based login. This migration registers it so
-- the gateway can validate tokens from either application.
-- ============================================================================

INSERT INTO oidc_provider (
    id, tenant_id, name, issuer, jwks_uri, audience, active, client_id,
    groups_claim, groups_profile_mapping,
    created_at, updated_at
)
SELECT
    'kelta-api-provider',
    '00000000-0000-0000-0000-000000000001',
    'Authentik (API)',
    'https://authentik.rzware.com/application/o/kelta-api/',
    'https://authentik.rzware.com/application/o/kelta-api/jwks/',
    '',
    true,
    -- Reuse the same client_id as the existing provider for consistency
    op.client_id,
    op.groups_claim,
    op.groups_profile_mapping,
    NOW(),
    NOW()
FROM oidc_provider op
WHERE op.id = 'local-keycloak'
  AND NOT EXISTS (
    SELECT 1 FROM oidc_provider WHERE issuer = 'https://authentik.rzware.com/application/o/kelta-api/'
  );
