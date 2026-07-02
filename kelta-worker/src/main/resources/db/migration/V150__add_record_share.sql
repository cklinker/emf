-- V150: record_share — manual per-record shares (Salesforce-style manual sharing).
-- A row grants a user or group (shared_with_type) an access_level on a specific record
-- (collection_id + record_id). CRUD is served by the generic dynamic collection path; the
-- record-detail Sharing panel manages these rows. Enforcement (record-authz consulting
-- shares) is a follow-up — this migration ships the store. Mirrors the RLS pattern in V149.

CREATE TABLE IF NOT EXISTS record_share (
    id                VARCHAR(36)  PRIMARY KEY,
    tenant_id         VARCHAR(36)  NOT NULL,
    collection_id     VARCHAR(36)  NOT NULL,
    record_id         VARCHAR(36)  NOT NULL,
    shared_with_id    VARCHAR(36)  NOT NULL,
    shared_with_type  VARCHAR(20)  NOT NULL,
    access_level      VARCHAR(20)  NOT NULL DEFAULT 'READ',
    reason            VARCHAR(500),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(36),
    updated_by        VARCHAR(36),
    CONSTRAINT uq_record_share UNIQUE (tenant_id, collection_id, record_id, shared_with_id)
);

-- Hot lookup: shares for a given record (the Sharing panel query).
CREATE INDEX IF NOT EXISTS idx_record_share_lookup
    ON record_share (tenant_id, collection_id, record_id);

-- RLS: tenant isolation + admin bypass (empty tenant setting), matching record_script (V149).
ALTER TABLE record_share ENABLE ROW LEVEL SECURITY;
ALTER TABLE record_share FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON record_share;
DROP POLICY IF EXISTS admin_bypass     ON record_share;
CREATE POLICY tenant_isolation ON record_share
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON record_share
    USING (current_setting('app.current_tenant_id', true) = '');
