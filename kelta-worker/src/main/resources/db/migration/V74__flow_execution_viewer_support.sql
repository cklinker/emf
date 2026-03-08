-- V74: Flow execution viewer support
-- Adds columns needed for parallel branch tracking and improved execution querying.

-- Support for Parallel state branch tracking in step logs
ALTER TABLE flow_step_log ADD COLUMN parent_execution_id VARCHAR(36);
ALTER TABLE flow_step_log ADD COLUMN branch_index INTEGER;

-- Index for faster step log queries ordered by time
CREATE INDEX idx_flow_step_log_time ON flow_step_log(execution_id, started_at);

-- Index for retry queries - find failed/cancelled executions
CREATE INDEX idx_flow_execution_status ON flow_execution(flow_id, status);
