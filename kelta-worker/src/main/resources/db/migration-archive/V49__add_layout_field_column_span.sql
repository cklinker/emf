-- V49: Add column_span to layout_field
-- Allows a field to span multiple columns in a multi-column layout section.
-- Default is 1 (field occupies a single column).

ALTER TABLE layout_field ADD COLUMN column_span INTEGER DEFAULT 1;
