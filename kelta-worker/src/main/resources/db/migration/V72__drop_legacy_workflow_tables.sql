-- V72: Drop legacy workflow tables after migration to flows.
--
-- This migration removes the legacy workflow rule engine tables. Before running,
-- ensure all workflow rules have been migrated to flows using the
-- POST /api/admin/migrate-workflow-rules endpoint.
--
-- Tables dropped:
--   workflow_action_log       - Individual action execution logs
--   workflow_execution_log    - Rule execution logs
--   workflow_pending_action   - Deferred action queue
--   workflow_rule_version     - Rule version history
--   workflow_action           - Action definitions per rule
--   workflow_action_type      - Registered action type registry
--   workflow_rule             - Workflow rule definitions
--
-- Ordering matters: children before parents (FK dependencies).

DROP TABLE IF EXISTS workflow_action_log CASCADE;
DROP TABLE IF EXISTS workflow_pending_action CASCADE;
DROP TABLE IF EXISTS workflow_rule_version CASCADE;
DROP TABLE IF EXISTS workflow_execution_log CASCADE;
DROP TABLE IF EXISTS workflow_action CASCADE;
DROP TABLE IF EXISTS workflow_action_type CASCADE;
DROP TABLE IF EXISTS workflow_rule CASCADE;
