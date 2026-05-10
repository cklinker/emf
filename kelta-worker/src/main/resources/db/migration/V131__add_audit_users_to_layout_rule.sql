-- V131: Add created_by/updated_by audit columns to layout_rule
--
-- PhysicalTableStorageAdapter unconditionally inserts/selects
-- id, created_at, updated_at, created_by, updated_by for every collection
-- (see V68). V130 created layout_rule without created_by/updated_by, which
-- caused INSERTs to fail with "column does not exist" for any DB that ran
-- V130 before this fix landed. Use IF NOT EXISTS so fresh installs from the
-- patched V130 are no-ops.

ALTER TABLE layout_rule
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
