-- V148: Per-tenant IP allowlist (Twingate range restriction).
--
-- When ip_allowlist_enabled is true, non-admin users may only reach the tenant's
-- /api/** data paths from a source IP inside one of the CIDR ranges in
-- ip_allowlist_cidrs. Account admins (MANAGE_TENANTS) bypass the restriction so a
-- misconfigured range can never lock them out. Enforced at the gateway; broadcast to
-- all pods via kelta.config.tenant.ip-allowlist.changed.<tenantId>.
--
-- Mirrors the tenant email-config columns added in V134. The tenant table already has
-- RLS configured, so no RLS-enable change is needed for these new columns.

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS ip_allowlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ip_allowlist_cidrs   JSONB   NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN tenant.ip_allowlist_enabled IS
    'When true, non-admin requests to /api/** must originate from a CIDR in ip_allowlist_cidrs.';
COMMENT ON COLUMN tenant.ip_allowlist_cidrs IS
    'JSON array of allowed CIDR ranges, e.g. ["10.0.0.0/8","192.168.0.0/16"]. Empty array = no restriction.';
