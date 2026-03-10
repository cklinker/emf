-- V33: Bulk job and result tables
-- Part of Phase 5 Stream D: Bulk & Composite APIs (5.4)

CREATE TABLE bulk_job (
    id                VARCHAR(36)   PRIMARY KEY,
    tenant_id         VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    collection_id     VARCHAR(36)   NOT NULL REFERENCES collection(id),
    operation         VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    total_records     INTEGER       DEFAULT 0,
    processed_records INTEGER       DEFAULT 0,
    success_records   INTEGER       DEFAULT 0,
    error_records     INTEGER       DEFAULT 0,
    external_id_field VARCHAR(100),
    content_type      VARCHAR(50)   DEFAULT 'application/json',
    batch_size        INTEGER       DEFAULT 200,
    created_by        VARCHAR(36)   NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE,
    completed_at      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bulk_operation CHECK (operation IN ('INSERT','UPDATE','UPSERT','DELETE')),
    CONSTRAINT chk_bulk_status CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED','ABORTED'))
);

CREATE TABLE bulk_job_result (
    id              VARCHAR(36)   PRIMARY KEY,
    bulk_job_id     VARCHAR(36)   NOT NULL REFERENCES bulk_job(id) ON DELETE CASCADE,
    record_index    INTEGER       NOT NULL,
    record_id       VARCHAR(36),
    status          VARCHAR(20)   NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_result_status CHECK (status IN ('SUCCESS','FAILURE'))
);

CREATE INDEX idx_bulk_job_tenant ON bulk_job(tenant_id, created_at DESC);
CREATE INDEX idx_bulk_job_status ON bulk_job(status);
CREATE INDEX idx_bulk_result_job ON bulk_job_result(bulk_job_id, record_index);
