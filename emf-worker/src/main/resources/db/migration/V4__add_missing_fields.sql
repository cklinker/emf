-- Add missing fields to collection table
ALTER TABLE collection
ADD COLUMN IF NOT EXISTS display_name VARCHAR(100),
ADD COLUMN IF NOT EXISTS storage_mode VARCHAR(50) DEFAULT 'PHYSICAL_TABLE';

-- Set display_name to name for existing records
UPDATE collection SET display_name = name WHERE display_name IS NULL;

-- Add missing fields to field table
ALTER TABLE field
ADD COLUMN IF NOT EXISTS display_name VARCHAR(100),
ADD COLUMN IF NOT EXISTS unique_constraint BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS indexed BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS default_value JSONB,
ADD COLUMN IF NOT EXISTS reference_target VARCHAR(100),
ADD COLUMN IF NOT EXISTS field_order INTEGER DEFAULT 0;

-- Set display_name to name for existing records
UPDATE field SET display_name = name WHERE display_name IS NULL;

-- Add expression field to policy table
ALTER TABLE policy
ADD COLUMN IF NOT EXISTS expression TEXT;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_collection_display_name ON collection(display_name);
CREATE INDEX IF NOT EXISTS idx_collection_storage_mode ON collection(storage_mode);
CREATE INDEX IF NOT EXISTS idx_field_display_name ON field(display_name);
CREATE INDEX IF NOT EXISTS idx_field_unique ON field(unique_constraint) WHERE unique_constraint = TRUE;
CREATE INDEX IF NOT EXISTS idx_field_indexed ON field(indexed) WHERE indexed = TRUE;
CREATE INDEX IF NOT EXISTS idx_field_order ON field(field_order);
CREATE INDEX IF NOT EXISTS idx_field_reference_target ON field(reference_target) WHERE reference_target IS NOT NULL;
