-- A module whose JAR fails signature, checksum or classloading used to fall back to stub handlers
-- that returned SUCCESS ({"status":"EXECUTED","mode":"stub"}) while the module reported ACTIVE, so
-- a cryptographically rejected module was indistinguishable from a working one and flows calling it
-- passed. Loading now fails closed into QUARANTINED, and a module that loaded but is missing
-- something it declared reports DEGRADED.
--
-- The error is persisted because it is the only way an admin can see WHY: the load happens on pod
-- startup or on a NATS event, long after any request they made.

ALTER TABLE tenant_module DROP CONSTRAINT IF EXISTS chk_module_status;

ALTER TABLE tenant_module ADD CONSTRAINT chk_module_status
    CHECK (status IN ('INSTALLING', 'INSTALLED', 'ACTIVE', 'DISABLED', 'FAILED',
                      'UNINSTALLING', 'QUARANTINED', 'DEGRADED', 'STUB'));

ALTER TABLE tenant_module ADD COLUMN IF NOT EXISTS last_error      text;
ALTER TABLE tenant_module ADD COLUMN IF NOT EXISTS last_error_at   timestamptz;
ALTER TABLE tenant_module ADD COLUMN IF NOT EXISTS last_loaded_at  timestamptz;
ALTER TABLE tenant_module ADD COLUMN IF NOT EXISTS load_attempts   integer NOT NULL DEFAULT 0;
