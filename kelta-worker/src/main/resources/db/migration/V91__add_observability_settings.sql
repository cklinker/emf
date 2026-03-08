-- Observability settings (retention periods, etc.)
-- Settings are stored per-tenant in PostgreSQL (small config table).
-- Actual retention is enforced via OpenSearch ISM policies.
CREATE TABLE observability_settings (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenant(id),
    setting_key     VARCHAR(100) NOT NULL,
    setting_value   VARCHAR(500) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_observability_settings_tenant_key UNIQUE (tenant_id, setting_key)
);

-- Seed default retention settings for all existing tenants
INSERT INTO observability_settings (id, tenant_id, setting_key, setting_value, created_at, updated_at)
SELECT gen_random_uuid()::text, t.id, 'trace_retention_days', '30', NOW(), NOW()
FROM tenant t;

INSERT INTO observability_settings (id, tenant_id, setting_key, setting_value, created_at, updated_at)
SELECT gen_random_uuid()::text, t.id, 'log_retention_days', '30', NOW(), NOW()
FROM tenant t;

INSERT INTO observability_settings (id, tenant_id, setting_key, setting_value, created_at, updated_at)
SELECT gen_random_uuid()::text, t.id, 'audit_retention_days', '90', NOW(), NOW()
FROM tenant t;
