-- EMF Control Plane OIDC Provider Role Mapping
-- Flyway Migration V36: Configure role claim and mapping for Authentik providers
--
-- Sets roles_claim to 'groups' (Authentik's standard claim for group membership)
-- and configures rolesMapping to translate Authentik group names to internal role names
-- expected by @PreAuthorize annotations (ADMIN, USER, DEVELOPER, VIEWER, PLATFORM_ADMIN).

-- Update Authentik providers: map groups claim to internal roles
UPDATE oidc_provider
SET
    roles_claim = 'groups',
    roles_mapping = '{"emf-admins": "ADMIN", "emf-users": "USER", "emf-developers": "DEVELOPER", "emf-viewers": "VIEWER", "authentik Admins": "PLATFORM_ADMIN"}'
WHERE roles_claim IS NULL
  AND issuer LIKE '%authentik%';

-- Also set for any provider that doesn't have roles_claim configured yet
-- and uses the default groups claim (safe fallback — no mapping applied if no matching groups)
UPDATE oidc_provider
SET roles_claim = 'groups'
WHERE roles_claim IS NULL
  AND issuer NOT LIKE '%keycloak%'
  AND issuer NOT LIKE '%localhost%';
