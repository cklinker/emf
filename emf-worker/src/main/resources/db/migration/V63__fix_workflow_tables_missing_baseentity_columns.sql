-- V63: Fix workflow tables missing BaseEntity columns (created_at, updated_at)
--
-- WorkflowActionLog and WorkflowRuleVersion extend BaseEntity which requires
-- created_at and updated_at columns. These were omitted from the original
-- V59 and V62 migrations.

-- 1. Add missing created_at and updated_at to workflow_action_log
ALTER TABLE workflow_action_log ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE workflow_action_log ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- 2. Add missing updated_at to workflow_rule_version (created_at already exists from V62)
ALTER TABLE workflow_rule_version ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
