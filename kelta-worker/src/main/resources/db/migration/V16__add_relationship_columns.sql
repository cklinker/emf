-- V16: Add relationship metadata columns to field table
-- Supports LOOKUP and MASTER_DETAIL relationship types

ALTER TABLE field ADD COLUMN IF NOT EXISTS relationship_type VARCHAR(20);
ALTER TABLE field ADD COLUMN IF NOT EXISTS relationship_name VARCHAR(100);
ALTER TABLE field ADD COLUMN IF NOT EXISTS cascade_delete BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE field ADD COLUMN IF NOT EXISTS reference_collection_id VARCHAR(36);

-- Add FK from field.reference_collection_id to collection.id
ALTER TABLE field ADD CONSTRAINT fk_field_reference_collection
    FOREIGN KEY (reference_collection_id) REFERENCES collection(id)
    ON DELETE SET NULL;

-- Index for efficient lookup of fields referencing a given collection
CREATE INDEX IF NOT EXISTS idx_field_reference_collection ON field(reference_collection_id)
    WHERE reference_collection_id IS NOT NULL;

-- Index for finding all relationship fields of a given type
CREATE INDEX IF NOT EXISTS idx_field_relationship_type ON field(relationship_type)
    WHERE relationship_type IS NOT NULL;
