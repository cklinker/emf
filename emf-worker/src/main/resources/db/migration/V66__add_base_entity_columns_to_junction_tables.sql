-- V66: Add missing BaseEntity columns to permission junction tables
--
-- The PhysicalTableStorageAdapter.create() unconditionally inserts
-- id, created_at, updated_at, created_by, updated_by for every collection.
-- V55 created user_permission_set and group_permission_set with id and
-- created_at, but omitted updated_at, created_by, and updated_by.
-- Without these columns, POST (assign) operations fail with a column-not-found error.

ALTER TABLE user_permission_set
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

ALTER TABLE group_permission_set
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
