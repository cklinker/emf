-- V142: Index file_attachment by (tenant_id, record_id) to support cascade cleanup.
--
-- When a parent record is deleted, AttachmentCleanupHook removes the record's
-- attachments by `WHERE record_id = ? AND tenant_id = ?`. The existing
-- idx_attachment_record index leads with collection_id, which is not provided to
-- the after-delete hook, so this composite index serves the lookup/delete directly.

CREATE INDEX IF NOT EXISTS idx_attachment_tenant_record
    ON file_attachment (tenant_id, record_id);
