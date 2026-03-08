-- V73: Runtime module tables for tenant-scoped module management
-- Supports installing, enabling, and disabling modules per tenant

CREATE TABLE tenant_module (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenant(id),
    module_id       VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    description     VARCHAR(1000),
    source_url      VARCHAR(2000) NOT NULL,
    jar_checksum    VARCHAR(64) NOT NULL,
    jar_size_bytes  BIGINT,
    module_class    VARCHAR(500) NOT NULL,
    manifest        JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'INSTALLED',
    installed_by    VARCHAR(36) NOT NULL,
    installed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_module UNIQUE (tenant_id, module_id),
    CONSTRAINT chk_module_status CHECK (status IN ('INSTALLING', 'INSTALLED', 'ACTIVE', 'DISABLED', 'FAILED', 'UNINSTALLING'))
);

CREATE TABLE tenant_module_action (
    id               VARCHAR(36) PRIMARY KEY,
    tenant_module_id VARCHAR(36) NOT NULL REFERENCES tenant_module(id) ON DELETE CASCADE,
    action_key       VARCHAR(100) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    category         VARCHAR(50),
    description      VARCHAR(500),
    config_schema    JSONB,
    input_schema     JSONB,
    output_schema    JSONB,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_module_tenant ON tenant_module(tenant_id);
CREATE INDEX idx_tenant_module_status ON tenant_module(tenant_id, status);
CREATE INDEX idx_tenant_module_action_module ON tenant_module_action(tenant_module_id);
