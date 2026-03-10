-- =============================================
-- V62: Workflow Enterprise Features (Phase D)
-- =============================================
-- D1: Retry policies on workflow_action
-- D2: Workflow rule versioning
-- D3: Execution mode (parallel/sequential) on workflow_rule
-- D4: Analytics support (indexes for aggregation queries)
-- D5: Log retention support (indexes for cleanup queries)

-- ----- D1: Retry Policies -----
ALTER TABLE workflow_action ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE workflow_action ADD COLUMN retry_delay_seconds INTEGER NOT NULL DEFAULT 60;
ALTER TABLE workflow_action ADD COLUMN retry_backoff VARCHAR(20) NOT NULL DEFAULT 'FIXED';

-- Add attempt_number to action log for tracking retries
ALTER TABLE workflow_action_log ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1;

-- ----- D2: Workflow Versioning -----
CREATE TABLE workflow_rule_version (
    id                VARCHAR(36)   PRIMARY KEY,
    workflow_rule_id  VARCHAR(36)   NOT NULL REFERENCES workflow_rule(id) ON DELETE CASCADE,
    version_number    INTEGER       NOT NULL,
    snapshot          JSONB         NOT NULL,
    change_summary    VARCHAR(500),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    CONSTRAINT uq_rule_version UNIQUE (workflow_rule_id, version_number)
);

CREATE INDEX idx_workflow_rule_version_rule_id ON workflow_rule_version(workflow_rule_id);

-- Add version reference to execution log
ALTER TABLE workflow_execution_log ADD COLUMN rule_version INTEGER;

-- ----- D3: Execution Mode -----
ALTER TABLE workflow_rule ADD COLUMN execution_mode VARCHAR(20) NOT NULL DEFAULT 'SEQUENTIAL';

-- ----- D4: Analytics — indexes for aggregation queries -----
CREATE INDEX idx_workflow_exec_log_tenant_status ON workflow_execution_log(tenant_id, status);
CREATE INDEX idx_workflow_exec_log_executed_at ON workflow_execution_log(executed_at);
CREATE INDEX idx_workflow_exec_log_rule_status ON workflow_execution_log(workflow_rule_id, status);
CREATE INDEX idx_workflow_action_log_execution ON workflow_action_log(execution_log_id, status);

-- ----- D5: Log Retention — index for cleanup by date -----
CREATE INDEX idx_workflow_exec_log_cleanup ON workflow_execution_log(executed_at, tenant_id);
CREATE INDEX idx_workflow_action_log_cleanup ON workflow_action_log(executed_at);
