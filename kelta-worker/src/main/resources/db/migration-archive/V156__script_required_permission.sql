-- V156: Optional per-script execution gate. When set, POST /api/scripts/{id}/execute
-- requires the caller's profile to hold this system permission.
ALTER TABLE script ADD COLUMN IF NOT EXISTS required_permission VARCHAR(100);
