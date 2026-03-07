-- V90: Allow null user_id in setup_audit_trail for system-initiated operations
-- (e.g., delete operations where no user context is available)

ALTER TABLE setup_audit_trail ALTER COLUMN user_id DROP NOT NULL;

-- Defensive cleanup of any invalid 'system' user_id values
UPDATE setup_audit_trail SET user_id = NULL WHERE user_id = 'system';
