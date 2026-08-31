-- setup_audit_trail.chk_audit_action only permitted the five record-mutation verbs, but
-- CredentialResolverImpl writes CREDENTIAL_RESOLVE on every credential access. Postgres
-- rejected each of those rows, and SetupAuditService swallows write failures by design
-- ("audit failures never disrupt normal operations"), so the only symptom was a log line:
--
--   new row for relation "setup_audit_trail" violates check constraint "chk_audit_action"
--
-- Net effect: there was no record of secret access on any tenant, for any integration.
-- Read access is a distinct verb from the mutation verbs and belongs in the same trail --
-- who read which credential, and when, is the part an audit actually needs.

ALTER TABLE setup_audit_trail DROP CONSTRAINT IF EXISTS chk_audit_action;

ALTER TABLE setup_audit_trail ADD CONSTRAINT chk_audit_action
    CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'ACTIVATED', 'DEACTIVATED',
                      'CREDENTIAL_RESOLVE'));
