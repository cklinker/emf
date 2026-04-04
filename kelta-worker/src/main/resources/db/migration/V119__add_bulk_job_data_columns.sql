-- V119: Add data storage columns to bulk_job for inline payloads and file references
-- Part of KEL-18: Bulk data operations processor

ALTER TABLE bulk_job ADD COLUMN data_payload JSONB;
ALTER TABLE bulk_job ADD COLUMN file_storage_key VARCHAR(500);
ALTER TABLE bulk_job ADD COLUMN error_message TEXT;
