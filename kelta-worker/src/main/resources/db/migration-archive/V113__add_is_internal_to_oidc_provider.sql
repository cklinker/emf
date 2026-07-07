-- Add is_internal flag to distinguish internal JWT-validation providers
-- from external SSO providers shown on the login page.
-- Internal providers (e.g. 'Kelta Platform (Internal)') are used for validating
-- platform-issued JWTs and must not appear as SSO login options in the UI.

ALTER TABLE oidc_provider ADD COLUMN is_internal BOOLEAN NOT NULL DEFAULT FALSE;

-- Back-fill: any provider with client_id = 'kelta-platform' is internal.
UPDATE oidc_provider SET is_internal = TRUE WHERE client_id = 'kelta-platform';
