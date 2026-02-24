-- V20: Add track_history column to field table
-- Part of Phase 2 Stream E: Audit & History

ALTER TABLE field ADD COLUMN IF NOT EXISTS track_history BOOLEAN DEFAULT false;

COMMENT ON COLUMN field.track_history IS
'When true, changes to this field are recorded in the field_history table.
 Default: false. Enable for fields that need audit trails.';
