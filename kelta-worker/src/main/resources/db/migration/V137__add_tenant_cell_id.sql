-- V137: add cell_id to tenant for Tier-3 cell-based deployment
--
-- Background: at >1k tenants the single-stack model (one gateway + worker
-- + auth + ai serving everyone) hits per-pod, per-DB, and per-Redis limits.
-- Cell-based architecture shards tenants across N independent stacks
-- (cells), each with its own DB / Redis / NATS. Blast radius of an
-- incident shrinks to one cell instead of the whole platform.
--
-- This migration only adds the routing column. Nothing in the runtime
-- reads it yet — gateway routing changes ship in a follow-up. Defaulting
-- to 'default' means every existing tenant is in the default cell, so
-- adding this column is a no-op at runtime until we deploy a second cell.

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS cell_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- Index on cell_id supports the gateway's tenant→cell lookup at request
-- time. The lookup will be cached, but a missing index would still hurt
-- on cache-cold pods at startup.
CREATE INDEX IF NOT EXISTS idx_tenant_cell_id ON tenant(cell_id);

COMMENT ON COLUMN tenant.cell_id IS
    'Cell (shard) this tenant belongs to. ''default'' for everyone today; '
    'becomes per-tenant once Tier-3 cell-based deployment ships. '
    'Routes tenant traffic to a specific cell-local stack (gateway / worker / DB / Redis / NATS).';
