-- V61: Workflow Flow Control & Advanced Actions (Phase C)
-- C1: DECISION action type (conditional branching)
-- C2: DELAY action type (deferred execution) + workflow_pending_action table
-- C3: New action types (UPDATE_RECORD, DELETE_RECORD, SEND_NOTIFICATION, HTTP_CALLOUT, TRIGGER_FLOW, LOG_MESSAGE)

-- 1. Seed new action types into workflow_action_type
INSERT INTO workflow_action_type (id, key, name, description, category, handler_class, icon) VALUES
    ('wat-decision-001', 'DECISION', 'Decision', 'Conditional branching with if/else logic', 'FLOW_CONTROL', 'com.emf.controlplane.service.workflow.handlers.DecisionActionHandler', 'git-branch'),
    ('wat-delay-001', 'DELAY', 'Delay', 'Delays execution for a specified duration or until a date', 'FLOW_CONTROL', 'com.emf.controlplane.service.workflow.handlers.DelayActionHandler', 'clock'),
    ('wat-update-record-001', 'UPDATE_RECORD', 'Update Record', 'Updates a record in any collection', 'DATA', 'com.emf.controlplane.service.workflow.handlers.UpdateRecordActionHandler', 'edit-2'),
    ('wat-delete-record-001', 'DELETE_RECORD', 'Delete Record', 'Deletes a record from a collection', 'DATA', 'com.emf.controlplane.service.workflow.handlers.DeleteRecordActionHandler', 'trash-2'),
    ('wat-send-notif-001', 'SEND_NOTIFICATION', 'Send Notification', 'Sends an in-app notification', 'COMMUNICATION', 'com.emf.controlplane.service.workflow.handlers.SendNotificationActionHandler', 'bell'),
    ('wat-http-callout-001', 'HTTP_CALLOUT', 'HTTP Callout', 'Makes a generic HTTP request with response capture', 'INTEGRATION', 'com.emf.controlplane.service.workflow.handlers.HttpCalloutActionHandler', 'globe'),
    ('wat-trigger-flow-001', 'TRIGGER_FLOW', 'Trigger Flow', 'Invokes another workflow rule as a subflow', 'FLOW_CONTROL', 'com.emf.controlplane.service.workflow.handlers.TriggerFlowActionHandler', 'play'),
    ('wat-log-message-001', 'LOG_MESSAGE', 'Log Message', 'Writes a message to the execution log', 'DATA', 'com.emf.controlplane.service.workflow.handlers.LogMessageActionHandler', 'file-text');

-- 2. Create workflow_pending_action table for delayed/scheduled actions
CREATE TABLE workflow_pending_action (
    id                VARCHAR(36) PRIMARY KEY,
    tenant_id         VARCHAR(36) NOT NULL,
    execution_log_id  VARCHAR(36) NOT NULL,
    workflow_rule_id  VARCHAR(36) NOT NULL,
    action_index      INTEGER NOT NULL,
    record_id         VARCHAR(36),
    record_snapshot   JSONB,
    scheduled_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(20) DEFAULT 'PENDING',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wf_pending_tenant ON workflow_pending_action(tenant_id);
CREATE INDEX idx_wf_pending_status ON workflow_pending_action(status, scheduled_at);
CREATE INDEX idx_wf_pending_rule ON workflow_pending_action(workflow_rule_id);
