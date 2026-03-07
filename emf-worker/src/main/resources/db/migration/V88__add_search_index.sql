-- ============================================================================
-- V88: Add searchable field flag and full-text search index table
-- ============================================================================
-- Enables per-field searchable configuration and a centralized search_index
-- table with PostgreSQL tsvector full-text search for global search.
-- ============================================================================

-- 1. Add searchable column to field table
ALTER TABLE field ADD COLUMN IF NOT EXISTS searchable BOOLEAN DEFAULT false;

COMMENT ON COLUMN field.searchable IS
'When true, this field''s values are included in the full-text search index.
 The collection display field is always searchable regardless of this flag.';

-- 2. Create the search_index table
CREATE TABLE IF NOT EXISTS search_index (
    id              VARCHAR(36)              PRIMARY KEY,
    tenant_id       VARCHAR(36)              NOT NULL REFERENCES tenant(id),
    collection_id   VARCHAR(36)              NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    collection_name VARCHAR(100)             NOT NULL,
    record_id       VARCHAR(36)              NOT NULL,
    display_value   VARCHAR(500),
    search_content  TEXT,
    search_vector   tsvector,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_search_index_tenant_collection_record
        UNIQUE (tenant_id, collection_name, record_id)
);

-- GIN index for fast full-text search
CREATE INDEX IF NOT EXISTS idx_search_index_vector
    ON search_index USING GIN (search_vector);

-- Index for tenant-scoped queries
CREATE INDEX IF NOT EXISTS idx_search_index_tenant
    ON search_index (tenant_id);

-- 3. Trigger to auto-update search_vector from search_content
CREATE OR REPLACE FUNCTION search_index_update_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.search_content, ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trig_search_index_vector
    BEFORE INSERT OR UPDATE OF search_content ON search_index
    FOR EACH ROW
    EXECUTE FUNCTION search_index_update_vector();

-- 4. Enable RLS on search_index
ALTER TABLE search_index ENABLE ROW LEVEL SECURITY;
ALTER TABLE search_index FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON search_index;
DROP POLICY IF EXISTS admin_bypass ON search_index;

CREATE POLICY tenant_isolation ON search_index
    USING (tenant_id = current_setting('app.current_tenant_id', true));

CREATE POLICY admin_bypass ON search_index
    USING (current_setting('app.current_tenant_id', true) = '');
