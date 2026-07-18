-- Per-flow "run as" user: the audit identity stamped on records created or
-- updated by this flow when the execution has no initiating user (cron,
-- NATS-trigger, webhook starts). Falls back to the flow owner (created_by)
-- when unset. Nullable — no backfill.
ALTER TABLE flow ADD COLUMN IF NOT EXISTS run_as_user_id VARCHAR(36);
