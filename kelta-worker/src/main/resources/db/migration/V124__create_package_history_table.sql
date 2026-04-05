-- Package history table for tracking configuration package exports and imports
CREATE TABLE package_history (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description TEXT,
    type VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'pending',
    items TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_package_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT chk_package_history_type CHECK (type IN ('export', 'import')),
    CONSTRAINT chk_package_history_status CHECK (status IN ('success', 'failed', 'pending'))
);

CREATE INDEX idx_package_history_tenant_id ON package_history(tenant_id);
CREATE INDEX idx_package_history_created_at ON package_history(created_at DESC);
