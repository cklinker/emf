-- V117: Add simple groupBy/sortBy/sortDirection columns to report table
-- These map to the UI builder's simplified sort/group fields

ALTER TABLE report ADD COLUMN IF NOT EXISTS group_by VARCHAR(200);
ALTER TABLE report ADD COLUMN IF NOT EXISTS sort_by VARCHAR(200);
ALTER TABLE report ADD COLUMN IF NOT EXISTS sort_direction VARCHAR(4) DEFAULT 'ASC';
