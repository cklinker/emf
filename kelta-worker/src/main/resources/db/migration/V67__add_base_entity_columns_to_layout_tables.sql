-- V67: Add missing BaseEntity columns to page layout tables
--
-- The PhysicalTableStorageAdapter.create() unconditionally inserts
-- id, created_at, updated_at, created_by, updated_by for every collection.
-- V21 created the page layout tables but omitted created_by and updated_by
-- from page_layout, and omitted all audit columns from the child tables.
-- Without these columns, create/update operations fail with a column-not-found error.

-- page_layout: has id, created_at, updated_at — needs created_by, updated_by
ALTER TABLE page_layout
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- layout_section: has id — needs created_at, updated_at, created_by, updated_by
ALTER TABLE layout_section
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- layout_field: has id — needs created_at, updated_at, created_by, updated_by
ALTER TABLE layout_field
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- layout_related_list: has id — needs created_at, updated_at, created_by, updated_by
ALTER TABLE layout_related_list
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- layout_assignment: has id — needs created_at, updated_at, created_by, updated_by
ALTER TABLE layout_assignment
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
