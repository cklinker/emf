-- V149: record_script — tenant-defined server-side scripts bound to record lifecycle events.
-- Unified record experience, slice 7. Executed by the sandboxed GraalVM ScriptExecutor from a
-- BeforeSaveHook: BEFORE_* may block a write (validation) or return field updates; AFTER_* run
-- side effects. Mirrors the RLS pattern used by record_tombstone (V143).

CREATE TABLE IF NOT EXISTS record_script (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    collection_id   VARCHAR(36)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    trigger_type    VARCHAR(20)  NOT NULL,
    script_source   TEXT         NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    order_sequence  INTEGER      NOT NULL DEFAULT 0,
    timeout_seconds INTEGER      NOT NULL DEFAULT 5,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    updated_by      VARCHAR(36),
    CONSTRAINT uq_record_script_name UNIQUE (tenant_id, collection_id, name)
);

-- Hot lookup: active scripts for a collection + trigger, in order.
CREATE INDEX IF NOT EXISTS idx_record_script_lookup
    ON record_script (tenant_id, collection_id, trigger_type, active, order_sequence);

-- RLS: tenant isolation + admin bypass (empty tenant setting), matching record_tombstone (V143).
ALTER TABLE record_script ENABLE ROW LEVEL SECURITY;
ALTER TABLE record_script FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON record_script;
DROP POLICY IF EXISTS admin_bypass     ON record_script;
CREATE POLICY tenant_isolation ON record_script
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON record_script
    USING (current_setting('app.current_tenant_id', true) = '');
