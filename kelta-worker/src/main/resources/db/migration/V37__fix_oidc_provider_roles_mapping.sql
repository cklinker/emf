-- EMF Control Plane: Fix OIDC Provider Role Mapping
-- Flyway Migration V37: Set rolesMapping for providers that have rolesClaim but no mapping
--
-- V36 set roles_claim = 'groups' for Authentik providers but the WHERE clause
-- (roles_claim IS NULL) did not match providers where roles_claim was already set.
-- This migration unconditionally sets rolesMapping for Authentik providers that
-- have roles_claim configured but no mapping.

UPDATE oidc_provider
SET roles_mapping = '{"emf-admins": "ADMIN", "emf-users": "USER", "emf-developers": "DEVELOPER", "emf-viewers": "VIEWER", "authentik Admins": "PLATFORM_ADMIN"}'
WHERE roles_mapping IS NULL
  AND roles_claim IS NOT NULL
  AND issuer LIKE '%authentik%';
