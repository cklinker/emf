-- Add slug and published fields to ui_page for Page Builder support
-- slug: URL-safe identifier for pages, unique per tenant
-- published: controls page visibility to end users

ALTER TABLE ui_page ADD COLUMN IF NOT EXISTS slug VARCHAR(200);
ALTER TABLE ui_page ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT FALSE;

-- Auto-populate slug from path for existing rows (strip leading slash, replace / with -)
UPDATE ui_page SET slug = REPLACE(LTRIM(path, '/'), '/', '-') WHERE slug IS NULL;

-- Make slug NOT NULL after populating
ALTER TABLE ui_page ALTER COLUMN slug SET NOT NULL;

-- Unique constraint: one slug per tenant
ALTER TABLE ui_page ADD CONSTRAINT uq_ui_page_tenant_slug UNIQUE (tenant_id, slug);

-- Index for slug lookups
CREATE INDEX idx_ui_page_slug ON ui_page(slug);

-- Index for published filtering
CREATE INDEX idx_ui_page_published ON ui_page(published);
