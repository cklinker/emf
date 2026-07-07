-- V26: Workflow rules, actions, and execution log tables
-- Part of Phase 4 Stream B: Workflow Rules (4.1)

CREATE TABLE workflow_rule (
    id                     VARCHAR(36)   PRIMARY KEY,
    tenant_id              VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    collection_id          VARCHAR(36)   NOT NULL REFERENCES collection(id),
    name                   VARCHAR(200)  NOT NULL,
    description            VARCHAR(1000),
    active                 BOOLEAN       DEFAULT true,
    trigger_type           VARCHAR(30)   NOT NULL,
    filter_formula         TEXT,
    re_evaluate_on_update  BOOLEAN       DEFAULT false,
    execution_order        INTEGER       DEFAULT 0,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_rule UNIQUE (tenant_id, collection_id, name),
    CONSTRAINT chk_trigger CHECK (trigger_type IN (
        'ON_CREATE','ON_UPDATE','ON_CREATE_OR_UPDATE','ON_DELETE'
    ))
);

CREATE TABLE workflow_action (
    id               VARCHAR(36) PRIMARY KEY,
    workflow_rule_id VARCHAR(36) NOT NULL REFERENCES workflow_rule(id) ON DELETE CASCADE,
    action_type      VARCHAR(30) NOT NULL,
    execution_order  INTEGER     DEFAULT 0,
    config           JSONB       NOT NULL,
    active           BOOLEAN     DEFAULT true,
    CONSTRAINT chk_action_type CHECK (action_type IN (
        'FIELD_UPDATE','EMAIL_ALERT','CREATE_RECORD','INVOKE_SCRIPT',
        'OUTBOUND_MESSAGE','CREATE_TASK','PUBLISH_EVENT'
    ))
);

CREATE TABLE workflow_execution_log (
    id               VARCHAR(36) PRIMARY KEY,
    tenant_id        VARCHAR(36) NOT NULL,
    workflow_rule_id VARCHAR(36) NOT NULL REFERENCES workflow_rule(id),
    record_id        VARCHAR(36) NOT NULL,
    trigger_type     VARCHAR(30) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    actions_executed INTEGER     DEFAULT 0,
    error_message    TEXT,
    executed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    duration_ms      INTEGER
);

CREATE INDEX idx_workflow_rule_tenant ON workflow_rule(tenant_id, collection_id);
CREATE INDEX idx_wf_action_rule ON workflow_action(workflow_rule_id, execution_order);
CREATE INDEX idx_wf_exec_log ON workflow_execution_log(tenant_id, workflow_rule_id);
