-- V57: Add created_at / updated_at to setup_audit_trail and login_history
-- so both entities can extend TenantScopedEntity (which extends BaseEntity).

-- setup_audit_trail: populate created_at from existing timestamp column
ALTER TABLE setup_audit_trail
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE setup_audit_trail SET created_at = timestamp, updated_at = timestamp WHERE created_at IS NULL;

ALTER TABLE setup_audit_trail
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT NOW();

-- login_history: populate created_at from existing login_time column
ALTER TABLE login_history
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE login_history SET created_at = login_time, updated_at = login_time WHERE created_at IS NULL;

ALTER TABLE login_history
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT NOW();
