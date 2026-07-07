-- V128: Idempotency cache for the CALL_API flow step (PR 4)
-- Records the last successful response per (tenant, idempotency_key) so
-- retried flow runs against non-idempotent upstreams (POST/PUT/PATCH) replay
-- the cached response instead of double-executing on the remote system.

CREATE TABLE api_call_idempotency (
    tenant_id        VARCHAR(36)              NOT NULL,
    idempotency_key  VARCHAR(200)             NOT NULL,
    flow_run_id      VARCHAR(36),
    state_name       VARCHAR(200),
    status_code      INTEGER,
    response_body    TEXT,
    response_hash    VARCHAR(64),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key)
);

CREATE INDEX idx_api_call_idempotency_expires
    ON api_call_idempotency(expires_at);

COMMENT ON TABLE api_call_idempotency IS
    'Cached responses for non-idempotent CALL_API steps; replayed on retry within TTL';

-- RLS — same pattern as V126/V127.
ALTER TABLE api_call_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_call_idempotency FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON api_call_idempotency;
DROP POLICY IF EXISTS admin_bypass     ON api_call_idempotency;
CREATE POLICY tenant_isolation ON api_call_idempotency
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON api_call_idempotency
    USING (current_setting('app.current_tenant_id', true) = '');
