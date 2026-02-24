-- V13: Setup Audit Trail table
-- Tracks all configuration changes made through the control plane

CREATE TABLE setup_audit_trail (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    user_id         VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    action          VARCHAR(50)  NOT NULL,
    section         VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       VARCHAR(36),
    entity_name     VARCHAR(200),
    old_value       JSONB,
    new_value       JSONB,
    timestamp       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_action CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'ACTIVATED', 'DEACTIVATED'))
);

CREATE INDEX idx_audit_tenant_time ON setup_audit_trail(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_entity ON setup_audit_trail(entity_type, entity_id);
CREATE INDEX idx_audit_user ON setup_audit_trail(user_id, timestamp DESC);
