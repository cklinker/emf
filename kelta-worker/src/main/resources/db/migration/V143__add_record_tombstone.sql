-- V143: Tombstone log for offline sync (Rec 2B). One row per deleted user-collection
-- record, written by RecordTombstoneHook (a wildcard after-delete hook). The
-- GET /api/{collection}/_changes feed returns these as deletions so an offline client
-- can prune its local replica. Deliberately decoupled from the hot delete path — no
-- soft-delete semantics are imposed on user tables.

CREATE TABLE record_tombstone (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(36)              NOT NULL,
    collection_name VARCHAR(255)             NOT NULL,
    record_id       VARCHAR(255)             NOT NULL,
    deleted_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_record_tombstone_changes
    ON record_tombstone(tenant_id, collection_name, deleted_at);

COMMENT ON TABLE record_tombstone IS
    'Deletion log per user-collection record; surfaced by GET /api/{collection}/_changes for offline sync';

-- RLS — same pattern as V126/V127/V128.
ALTER TABLE record_tombstone ENABLE ROW LEVEL SECURITY;
ALTER TABLE record_tombstone FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON record_tombstone;
DROP POLICY IF EXISTS admin_bypass     ON record_tombstone;
CREATE POLICY tenant_isolation ON record_tombstone
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY admin_bypass ON record_tombstone
    USING (current_setting('app.current_tenant_id', true) = '');
