-- V19: Create field_history table for tracking field-level changes
-- Part of Phase 2 Stream E: Audit & History

CREATE TABLE IF NOT EXISTS field_history (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    record_id     VARCHAR(36)  NOT NULL,
    field_name    VARCHAR(100) NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    changed_by    VARCHAR(36)  NOT NULL,
    changed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    change_source VARCHAR(20)  NOT NULL DEFAULT 'UI'
);

CREATE INDEX idx_field_history_record
    ON field_history(collection_id, record_id, changed_at DESC);
CREATE INDEX idx_field_history_field
    ON field_history(collection_id, field_name, changed_at DESC);
CREATE INDEX idx_field_history_user
    ON field_history(changed_by, changed_at DESC);
CREATE INDEX idx_field_history_tenant
    ON field_history(tenant_id);

COMMENT ON COLUMN field_history.change_source IS
'Source of the change: UI, API, WORKFLOW, SYSTEM, IMPORT';
