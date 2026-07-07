-- V129: Email template enrichment for the payload mapper UI (PR 5)
-- - variables_schema declares the named inputs the template expects, so the
--   PayloadMapper UI can render the right target schema.
-- - smtp_credential_id lets a template route through a per-template SMTP
--   credential (e.g., a transactional service like SendGrid) via the PR 1
--   credential vault, instead of the platform-default SMTP.

ALTER TABLE email_template
    ADD COLUMN IF NOT EXISTS variables_schema JSONB,
    ADD COLUMN IF NOT EXISTS smtp_credential_id VARCHAR(36);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'email_template'
          AND constraint_name = 'fk_email_template_smtp_credential'
    ) THEN
        ALTER TABLE email_template
            ADD CONSTRAINT fk_email_template_smtp_credential
            FOREIGN KEY (smtp_credential_id) REFERENCES credential(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_email_template_smtp_credential
    ON email_template(smtp_credential_id) WHERE smtp_credential_id IS NOT NULL;

COMMENT ON COLUMN email_template.variables_schema IS
    'JSON schema describing the inputs the template expects (drives the PayloadMapper target panel)';
COMMENT ON COLUMN email_template.smtp_credential_id IS
    'Optional: route this template through a specific SMTP credential from the vault (PR 1)';
