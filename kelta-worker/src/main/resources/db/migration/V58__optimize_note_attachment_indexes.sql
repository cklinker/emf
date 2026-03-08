-- V58: Optimize note and attachment indexes for tenant-scoped queries
--
-- The Hibernate tenant filter adds WHERE tenant_id = ? to every query,
-- but the existing composite indexes don't include tenant_id.
-- Adding tenant_id as the leading column allows PostgreSQL to use the
-- index for the full WHERE clause instead of doing a post-index filter.

-- Replace note composite index with tenant-aware version
DROP INDEX IF EXISTS idx_note_record;
CREATE INDEX idx_note_tenant_record ON note(tenant_id, collection_id, record_id, created_at DESC);

-- Replace attachment composite index with tenant-aware version
DROP INDEX IF EXISTS idx_attachment_record;
CREATE INDEX idx_attachment_tenant_record ON file_attachment(tenant_id, collection_id, record_id, uploaded_at DESC);
