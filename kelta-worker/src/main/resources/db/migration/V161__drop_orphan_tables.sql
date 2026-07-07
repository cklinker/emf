-- Drop two orphan tables the DB-design audit found: present in every environment
-- but referenced by no code (no repository SQL, no system-collection registration).
--
-- NOTE: numbered V161 assuming V160 (permission-set remnants, PR #1186) merges first.
-- If this merges before that PR, renumber to V160.

-- user_group_member: the original flat (group_id, user_id) join table from V12.
-- Superseded by group_membership (V45, nesting-aware), which is the only membership
-- table any code reads/writes. The old table was never dropped. Any residual rows are
-- orphaned legacy data never migrated to the V45 schema and are discarded here.
DROP TABLE IF EXISTS user_group_member;

-- flow_execution_dedup: an event-dedup table from V71 (event_id, flow_id, created_at)
-- that the current flow engine never uses — deduplication runs through flow_execution,
-- record_tombstone, and api_call_idempotency instead.
DROP TABLE IF EXISTS flow_execution_dedup;
