-- EMF Control Plane OIDC Claim Mapping
-- Flyway Migration V6: Add claim mapping columns to oidc_provider table
-- Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6

-- ============================================================================
-- ADD CLAIM MAPPING COLUMNS
-- ============================================================================

-- Add claim mapping columns to oidc_provider table
ALTER TABLE oidc_provider
    ADD COLUMN roles_claim VARCHAR(200),
    ADD COLUMN roles_mapping TEXT,
    ADD COLUMN email_claim VARCHAR(200),
    ADD COLUMN username_claim VARCHAR(200),
    ADD COLUMN name_claim VARCHAR(200);

-- ============================================================================
-- SET DEFAULT VALUES FOR EXISTING PROVIDERS
-- ============================================================================

-- Set default values for existing providers to ensure backward compatibility
-- These defaults follow the OpenID Connect standard claim names
UPDATE oidc_provider
SET 
    email_claim = 'email',
    username_claim = 'preferred_username',
    name_claim = 'name'
WHERE email_claim IS NULL;

-- ============================================================================
-- ADD CONSTRAINTS
-- ============================================================================

-- Add check constraint for roles_mapping JSON validity
-- This ensures that if roles_mapping is provided, it must be valid JSON
ALTER TABLE oidc_provider
    ADD CONSTRAINT chk_oidc_provider_roles_mapping_json
    CHECK (roles_mapping IS NULL OR roles_mapping::jsonb IS NOT NULL);

-- ============================================================================
-- ADD COLUMN COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON COLUMN oidc_provider.roles_claim IS 'Path to roles claim in JWT token (e.g., roles, realm_access.roles, groups). Supports dot notation for nested claims.';
COMMENT ON COLUMN oidc_provider.roles_mapping IS 'JSON mapping of external role values to internal role names. Example: {"external-admin": "ADMIN", "external-user": "USER"}';
COMMENT ON COLUMN oidc_provider.email_claim IS 'Path to email claim in JWT token. Default: email';
COMMENT ON COLUMN oidc_provider.username_claim IS 'Path to username claim in JWT token. Default: preferred_username';
COMMENT ON COLUMN oidc_provider.name_claim IS 'Path to name/display name claim in JWT token. Default: name';

-- ============================================================================
-- CREATE INDEXES FOR QUERY PERFORMANCE
-- ============================================================================

-- Create index on roles_claim for providers that use role-based access control
CREATE INDEX idx_oidc_provider_roles_claim ON oidc_provider(roles_claim) WHERE roles_claim IS NOT NULL;
