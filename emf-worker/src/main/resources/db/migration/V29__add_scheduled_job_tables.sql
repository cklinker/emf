-- V29: Scheduled jobs and job execution log tables
-- Part of Phase 4 Stream E: Scheduled Jobs (4.4)

CREATE TABLE scheduled_job (
    id               VARCHAR(36)   PRIMARY KEY,
    tenant_id        VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(500),
    job_type         VARCHAR(20)   NOT NULL,
    job_reference_id VARCHAR(36),
    cron_expression  VARCHAR(100)  NOT NULL,
    timezone         VARCHAR(50)   DEFAULT 'UTC',
    active           BOOLEAN       DEFAULT true,
    last_run_at      TIMESTAMP WITH TIME ZONE,
    last_status      VARCHAR(20),
    next_run_at      TIMESTAMP WITH TIME ZONE,
    created_by       VARCHAR(36)   NOT NULL REFERENCES platform_user(id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scheduled_job UNIQUE (tenant_id, name),
    CONSTRAINT chk_job_type CHECK (job_type IN ('FLOW','SCRIPT','REPORT_EXPORT'))
);

CREATE TABLE job_execution_log (
    id                VARCHAR(36)   PRIMARY KEY,
    job_id            VARCHAR(36)   NOT NULL REFERENCES scheduled_job(id),
    status            VARCHAR(20)   NOT NULL,
    records_processed INTEGER       DEFAULT 0,
    error_message     TEXT,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at      TIMESTAMP WITH TIME ZONE,
    duration_ms       INTEGER
);

CREATE INDEX idx_scheduled_job_tenant ON scheduled_job(tenant_id);
CREATE INDEX idx_scheduled_job_next_run ON scheduled_job(active, next_run_at);
CREATE INDEX idx_job_exec_log ON job_execution_log(job_id);
