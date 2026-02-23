-- V59: Workflow engine foundation - action type registry, action logging, and error handling
-- Part of Workflow Automation Phase A (A3 + A6)

-- 1. Create workflow_action_type table for pluggable action handler registry
CREATE TABLE workflow_action_type (
    id              VARCHAR(36)   PRIMARY KEY,
    key             VARCHAR(50)   NOT NULL UNIQUE,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(500),
    category        VARCHAR(50)   NOT NULL,
    config_schema   JSONB,
    icon            VARCHAR(50),
    handler_class   VARCHAR(255)  NOT NULL,
    active          BOOLEAN       DEFAULT true,
    built_in        BOOLEAN       DEFAULT true,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. Seed the 7 built-in action types
INSERT INTO workflow_action_type (id, key, name, description, category, handler_class, icon) VALUES
    ('wat-field-update-001', 'FIELD_UPDATE', 'Field Update', 'Updates fields on the triggering record', 'DATA', 'com.emf.controlplane.service.workflow.handlers.FieldUpdateActionHandler', 'edit'),
    ('wat-email-alert-001', 'EMAIL_ALERT', 'Email Alert', 'Sends an email notification', 'COMMUNICATION', 'com.emf.controlplane.service.workflow.handlers.EmailAlertActionHandler', 'mail'),
    ('wat-create-record-001', 'CREATE_RECORD', 'Create Record', 'Creates a new record in a target collection', 'DATA', 'com.emf.controlplane.service.workflow.handlers.CreateRecordActionHandler', 'plus-circle'),
    ('wat-invoke-script-001', 'INVOKE_SCRIPT', 'Invoke Script', 'Executes a server-side script', 'INTEGRATION', 'com.emf.controlplane.service.workflow.handlers.InvokeScriptActionHandler', 'code'),
    ('wat-outbound-msg-001', 'OUTBOUND_MESSAGE', 'Outbound Message', 'Sends an HTTP webhook request', 'INTEGRATION', 'com.emf.controlplane.service.workflow.handlers.OutboundMessageActionHandler', 'send'),
    ('wat-create-task-001', 'CREATE_TASK', 'Create Task', 'Creates a task record for follow-up', 'DATA', 'com.emf.controlplane.service.workflow.handlers.CreateTaskActionHandler', 'check-square'),
    ('wat-publish-event-001', 'PUBLISH_EVENT', 'Publish Event', 'Publishes a custom Kafka event', 'INTEGRATION', 'com.emf.controlplane.service.workflow.handlers.PublishEventActionHandler', 'radio');

-- 3. Drop the existing CHECK constraint on workflow_action.action_type
-- This allows new action types to be added dynamically via the registry
ALTER TABLE workflow_action DROP CONSTRAINT IF EXISTS chk_action_type;

-- 4. Add created_at and updated_at to workflow_action (was missing)
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- 5. Add error_handling column to workflow_rule
ALTER TABLE workflow_rule ADD COLUMN IF NOT EXISTS error_handling VARCHAR(30) DEFAULT 'STOP_ON_ERROR';

-- 6. Create workflow_action_log table for per-action execution detail
CREATE TABLE workflow_action_log (
    id               VARCHAR(36) PRIMARY KEY,
    execution_log_id VARCHAR(36) NOT NULL REFERENCES workflow_execution_log(id) ON DELETE CASCADE,
    action_id        VARCHAR(36) REFERENCES workflow_action(id),
    action_type      VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    error_message    TEXT,
    input_snapshot   JSONB,
    output_snapshot  JSONB,
    duration_ms      INTEGER,
    executed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wf_action_log_exec ON workflow_action_log(execution_log_id);
CREATE INDEX idx_wf_action_type_key ON workflow_action_type(key);
CREATE INDEX idx_wf_action_type_category ON workflow_action_type(category);
