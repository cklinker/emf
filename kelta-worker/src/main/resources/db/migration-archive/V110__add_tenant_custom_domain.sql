-- V110: Custom tenant domains for CNAME routing

CREATE TABLE tenant_custom_domain (
    id        VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    domain    VARCHAR(255) NOT NULL UNIQUE,
    verified  BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_custom_domain_domain ON tenant_custom_domain(domain);
