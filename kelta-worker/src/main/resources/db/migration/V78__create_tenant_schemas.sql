-- ============================================================================
-- V78: Create PostgreSQL schemas for existing tenants
-- ============================================================================
-- Each tenant gets its own schema named by the tenant's slug.
-- User-defined collection tables will live in these schemas.
-- System tables remain in the public schema.
-- ============================================================================

DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT id, slug FROM tenant WHERE status != 'DECOMMISSIONED'
    LOOP
        EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', t.slug);
        RAISE NOTICE 'Created schema for tenant: % (id=%)', t.slug, t.id;
    END LOOP;
END $$;
