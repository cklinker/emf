-- V105: Connected app audit trail for compliance tracking
-- Tracks: token generation, revocation, secret rotation, app lifecycle changes

CREATE TABLE connected_app_audit (
    id                VARCHAR(36)   PRIMARY KEY,
    tenant_id         VARCHAR(36)   NOT NULL,
    connected_app_id  VARCHAR(36)   NOT NULL REFERENCES connected_app(id) ON DELETE CASCADE,
    action            VARCHAR(30)   NOT NULL,
    details           JSONB,
    performed_by      VARCHAR(36),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ca_audit_app ON connected_app_audit(connected_app_id, created_at DESC);
CREATE INDEX idx_ca_audit_tenant ON connected_app_audit(tenant_id, created_at DESC);
