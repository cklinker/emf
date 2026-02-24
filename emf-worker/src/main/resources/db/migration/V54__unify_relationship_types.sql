-- V54: Consolidate REFERENCE and LOOKUP field types into MASTER_DETAIL.
-- Existing required/cascade_delete values are preserved as-is.

-- Convert field type from REFERENCE/LOOKUP to MASTER_DETAIL
UPDATE field
SET type = 'MASTER_DETAIL'
WHERE type IN ('REFERENCE', 'LOOKUP');

-- Normalize relationship_type to MASTER_DETAIL for all relationship fields
UPDATE field
SET relationship_type = 'MASTER_DETAIL'
WHERE relationship_type IN ('LOOKUP', 'REFERENCE');

-- Fix any relationship fields that have NULL relationship_type
UPDATE field
SET relationship_type = 'MASTER_DETAIL'
WHERE type = 'MASTER_DETAIL'
  AND relationship_type IS NULL
  AND reference_collection_id IS NOT NULL;
