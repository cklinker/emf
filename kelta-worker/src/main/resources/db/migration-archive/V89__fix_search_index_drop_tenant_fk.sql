-- ============================================================================
-- V89: Drop foreign key on search_index.tenant_id
-- ============================================================================
-- The tenant_id FK constraint causes failures when indexing records from
-- system/seed collections whose tenant IDs may not exist in the tenant table.
-- RLS and the explicit WHERE tenant_id = ? clause already provide tenant
-- isolation, so the FK is unnecessary.
-- ============================================================================

ALTER TABLE search_index DROP CONSTRAINT IF EXISTS search_index_tenant_id_fkey;
