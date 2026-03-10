-- V35: Add missing created_at/updated_at columns for Phase 2-3 tables
-- Continuation of V34 — covers tables from V19, V21, V23, V24

-- V19: field_history
ALTER TABLE field_history
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- V21: layout_section
ALTER TABLE layout_section
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- V21: layout_field
ALTER TABLE layout_field
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- V21: layout_related_list
ALTER TABLE layout_related_list
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- V21: layout_assignment
ALTER TABLE layout_assignment
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- V23: report_folder
ALTER TABLE report_folder
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- V24: dashboard_component
ALTER TABLE dashboard_component
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
