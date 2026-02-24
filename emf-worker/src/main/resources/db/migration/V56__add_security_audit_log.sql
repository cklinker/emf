-- V56: Security audit log for tracking security-related events
-- Provides an immutable audit trail for authentication, authorization, and configuration changes.

CREATE TABLE security_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) REFERENCES tenant(id),
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(30) NOT NULL,
    actor_user_id VARCHAR(36),
    actor_email VARCHAR(320),
    target_type VARCHAR(50),
    target_id VARCHAR(36),
    target_name VARCHAR(255),
    details JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    correlation_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_audit_tenant_time ON security_audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_security_audit_event_type ON security_audit_log(event_type, created_at DESC);
CREATE INDEX idx_security_audit_actor ON security_audit_log(actor_user_id, created_at DESC);
CREATE INDEX idx_security_audit_target ON security_audit_log(target_type, target_id, created_at DESC);
CREATE INDEX idx_security_audit_category ON security_audit_log(event_category, created_at DESC);
