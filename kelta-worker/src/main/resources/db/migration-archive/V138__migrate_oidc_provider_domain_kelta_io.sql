-- V138: update internal kelta-auth OIDC provider records from auth.rzware.com to auth.kelta.io
--
-- V101 hard-coded the issuer/jwks_uri as https://auth.rzware.com for all tenants
-- provisioned before the domain migration (PR #943). kelta-auth now identifies as
-- https://auth.kelta.io, so every existing tenant's internal provider record is stale:
--
--   * The UI does OIDC discovery against the stored issuer — wrong domain resolves nothing
--   * The gateway rejects platform JWTs (iss=auth.kelta.io) that don't match the DB row
--
-- TenantProvisioningHook already uses auth.kelta.io for *new* tenants; this migration
-- catches every tenant that was provisioned before the cutover.
--
-- The Authentik provider (authentik.rzware.com) is intentionally left untouched —
-- Authentik remains on rzware.com infrastructure per the domain migration plan.

UPDATE oidc_provider
SET issuer     = 'https://auth.kelta.io',
    jwks_uri   = 'https://auth.kelta.io/oauth2/jwks',
    updated_at = NOW()
WHERE issuer = 'https://auth.rzware.com';
