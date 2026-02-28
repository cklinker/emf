-- V71: Flow Engine Enhancements
-- Expands flow tables for state machine execution engine support.
-- Adds step logging, pending resume, audit trail, and dedup tables.

-- Expand flow_type to include KAFKA_TRIGGERED
ALTER TABLE flow DROP CONSTRAINT chk_flow_type;
ALTER TABLE flow ADD CONSTRAINT chk_flow_type CHECK (flow_type IN (
    'RECORD_TRIGGERED', 'KAFKA_TRIGGERED', 'SCHEDULED', 'AUTOLAUNCHED', 'SCREEN'
));

-- Add scheduling column for SCHEDULED flows
ALTER TABLE flow ADD COLUMN IF NOT EXISTS last_scheduled_run TIMESTAMP WITH TIME ZONE;

-- Add columns to flow_execution for state data persistence
ALTER TABLE flow_execution ADD COLUMN IF NOT EXISTS state_data JSONB DEFAULT '{}';
ALTER TABLE flow_execution ADD COLUMN IF NOT EXISTS step_count INTEGER DEFAULT 0;
ALTER TABLE flow_execution ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE flow_execution ADD COLUMN IF NOT EXISTS initial_input JSONB;
ALTER TABLE flow_execution ADD COLUMN IF NOT EXISTS is_test BOOLEAN DEFAULT false;

-- Step-level execution log
CREATE TABLE flow_step_log (
    id                VARCHAR(36) PRIMARY KEY,
    execution_id      VARCHAR(36) NOT NULL REFERENCES flow_execution(id) ON DELETE CASCADE,
    state_id          VARCHAR(100) NOT NULL,
    state_name        VARCHAR(200),
    state_type        VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    input_snapshot    JSONB,
    output_snapshot   JSONB,
    error_message     TEXT,
    error_code        VARCHAR(100),
    attempt_number    INTEGER DEFAULT 1,
    duration_ms       INTEGER,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_step_log_exec ON flow_step_log(execution_id);
CREATE INDEX idx_flow_step_log_state ON flow_step_log(execution_id, state_id);

-- Pending resume table for Wait states and scheduled resumptions
CREATE TABLE flow_pending_resume (
    id                VARCHAR(36) PRIMARY KEY,
    execution_id      VARCHAR(36) NOT NULL REFERENCES flow_execution(id) ON DELETE CASCADE,
    tenant_id         VARCHAR(36) NOT NULL,
    resume_at         TIMESTAMP WITH TIME ZONE,
    resume_event      VARCHAR(200),
    claimed_by        VARCHAR(100),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_pending_resume_time ON flow_pending_resume(resume_at) WHERE claimed_by IS NULL;
CREATE INDEX idx_flow_pending_resume_event ON flow_pending_resume(resume_event) WHERE claimed_by IS NULL;

-- Audit trail for flow lifecycle events
CREATE TABLE flow_audit_log (
    id          VARCHAR(36) PRIMARY KEY,
    tenant_id   VARCHAR(36) NOT NULL,
    flow_id     VARCHAR(36) NOT NULL,
    action      VARCHAR(30) NOT NULL,
    user_id     VARCHAR(36),
    details     JSONB,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_audit_tenant ON flow_audit_log(tenant_id, flow_id);

-- Idempotency dedup table for trigger events
CREATE TABLE flow_execution_dedup (
    event_id    VARCHAR(100) NOT NULL,
    flow_id     VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, flow_id)
);
