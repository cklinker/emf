-- V134: Tenant-level email columns backed by the credential vault.
--
-- Replaces the plaintext `tenant.settings.email.smtp` JSONB blob with a typed
-- reference to a row in the encrypted `credential` table. The legacy JSONB
-- path is still read (for back-compat) until V129+1 tenants migrate, but new
-- writes go through the vault.

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS email_smtp_credential_id VARCHAR(36)
        REFERENCES credential(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS email_from_address       VARCHAR(320),
    ADD COLUMN IF NOT EXISTS email_from_name          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS auto_invite_on_create    BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN tenant.email_smtp_credential_id IS
    'FK to credential table (type=smtp). When set, overrides platform default SMTP. '
    'NULL means the tenant uses the platform default mail server.';
COMMENT ON COLUMN tenant.email_from_address IS
    'Override for the From address on outbound mail (platform default applies when NULL).';
COMMENT ON COLUMN tenant.email_from_name IS
    'Override for the From display name on outbound mail.';
COMMENT ON COLUMN tenant.auto_invite_on_create IS
    'When true, a user.invite email is sent automatically on SCIM/admin user creation. '
    'When false, invites must be sent manually via POST /admin/users/{id}/invite.';

CREATE INDEX IF NOT EXISTS idx_tenant_email_smtp_credential
    ON tenant(email_smtp_credential_id) WHERE email_smtp_credential_id IS NOT NULL;
