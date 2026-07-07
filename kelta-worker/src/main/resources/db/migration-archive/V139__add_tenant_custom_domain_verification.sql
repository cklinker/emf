-- Adds the per-domain ownership-verification token used by
-- TenantDomainController. Customers prove they own the domain by setting a
-- DNS TXT record at _kelta-verify.<domain> whose value equals this token;
-- the verify endpoint runs the DNS lookup before flipping `verified` to true.
--
-- Existing rows (manually verified before this rollout) keep verified=true
-- and get a NULL token — DNS check is skipped for rows where the token is
-- NULL so the migration is a no-op for them.
ALTER TABLE tenant_custom_domain
    ADD COLUMN IF NOT EXISTS verification_token VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_tenant_custom_domain_token
    ON tenant_custom_domain(verification_token)
    WHERE verification_token IS NOT NULL;
