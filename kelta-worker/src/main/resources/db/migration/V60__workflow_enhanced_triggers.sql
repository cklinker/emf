-- V60: Workflow Enhanced Triggers (Phase B)
-- B1: Add trigger_fields for field-level change detection
-- B2: Add scheduled trigger support columns
-- B3: Add MANUAL trigger type
-- B4: Add BEFORE_CREATE and BEFORE_UPDATE trigger types

-- B1: Field-level change detection
-- When populated (e.g. ["status","priority"]), rule only fires if one of those fields changed.
-- When null/empty, fires on any change (backward compatible).
ALTER TABLE workflow_rule ADD COLUMN IF NOT EXISTS trigger_fields JSONB;

-- B2: Scheduled trigger support
ALTER TABLE workflow_rule ADD COLUMN IF NOT EXISTS cron_expression VARCHAR(100);
ALTER TABLE workflow_rule ADD COLUMN IF NOT EXISTS timezone VARCHAR(50);
ALTER TABLE workflow_rule ADD COLUMN IF NOT EXISTS last_scheduled_run TIMESTAMP WITH TIME ZONE;

-- Expand trigger_type constraint to include new trigger types
-- First drop the existing constraint (if any)
DO $$
BEGIN
    ALTER TABLE workflow_rule DROP CONSTRAINT IF EXISTS workflow_rule_trigger_type_check;
EXCEPTION
    WHEN undefined_object THEN NULL;
END $$;

-- Add updated constraint with all trigger types
ALTER TABLE workflow_rule ADD CONSTRAINT workflow_rule_trigger_type_check
    CHECK (trigger_type IN ('ON_CREATE', 'ON_UPDATE', 'ON_DELETE', 'ON_CREATE_OR_UPDATE',
                            'SCHEDULED', 'MANUAL', 'BEFORE_CREATE', 'BEFORE_UPDATE'));
