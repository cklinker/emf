-- ============================================================================
-- V121: Data export tables for scheduled and on-demand tenant data exports
-- ============================================================================

CREATE TABLE data_export (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name            VARCHAR(200)  NOT NULL,
    description     TEXT,
    export_scope    VARCHAR(20)   NOT NULL DEFAULT 'SELECTIVE',
    collection_ids  TEXT,                          -- JSON array of collection IDs (null = all)
    format          VARCHAR(10)   NOT NULL DEFAULT 'CSV',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_records   INTEGER       NOT NULL DEFAULT 0,
    records_exported INTEGER      NOT NULL DEFAULT 0,
    storage_key     VARCHAR(500),                  -- S3 key for completed export
    file_size_bytes BIGINT,
    created_by      VARCHAR(255),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_export_scope  CHECK (export_scope IN ('FULL', 'SELECTIVE')),
    CONSTRAINT chk_export_format CHECK (format IN ('CSV', 'JSON')),
    CONSTRAINT chk_export_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_data_export_tenant     ON data_export (tenant_id);
CREATE INDEX idx_data_export_status     ON data_export (status);
CREATE INDEX idx_data_export_created_at ON data_export (created_at DESC);

-- Extend scheduled_job to support DATA_EXPORT job type
ALTER TABLE scheduled_job DROP CONSTRAINT IF EXISTS chk_job_type;
ALTER TABLE scheduled_job ADD CONSTRAINT chk_job_type
    CHECK (job_type IN ('FLOW', 'SCRIPT', 'REPORT_EXPORT', 'DATA_EXPORT'));
