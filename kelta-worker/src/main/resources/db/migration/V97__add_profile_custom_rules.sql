-- Profile custom ABAC rules for Cerbos policy generation
-- Stores admin-defined authorization rules that are converted to CEL conditions
-- in generated Cerbos policies.

CREATE TABLE profile_custom_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,        -- read, create, edit, delete
    effect VARCHAR(10) NOT NULL,         -- allow, deny
    condition_type VARCHAR(20) NOT NULL, -- visual, cel
    condition_json JSONB NOT NULL,       -- visual: {field, operator, value} / cel: {expression}
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_custom_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_custom_rules_profile ON profile_custom_rules(profile_id);
CREATE INDEX idx_custom_rules_tenant ON profile_custom_rules(tenant_id);
