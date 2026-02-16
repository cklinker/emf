-- V48: Enhance layout editor for WYSIWYG builder
-- Adds support for section types (highlights panel), tab groups,
-- conditional visibility rules, and field label/help text overrides.

-- layout_section enhancements
ALTER TABLE layout_section ADD COLUMN section_type VARCHAR(30) DEFAULT 'STANDARD';
ALTER TABLE layout_section ADD COLUMN tab_group VARCHAR(100);
ALTER TABLE layout_section ADD COLUMN tab_label VARCHAR(200);
ALTER TABLE layout_section ADD COLUMN visibility_rule JSONB;

-- layout_field enhancements
ALTER TABLE layout_field ADD COLUMN label_override VARCHAR(200);
ALTER TABLE layout_field ADD COLUMN help_text_override VARCHAR(500);
ALTER TABLE layout_field ADD COLUMN visibility_rule JSONB;
