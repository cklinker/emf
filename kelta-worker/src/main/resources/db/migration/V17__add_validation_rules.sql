-- V17: Create validation_rule table for formula-based record validation
-- Phase 2 Stream D: Validation Rules & Record Types (D1)

CREATE TABLE IF NOT EXISTS validation_rule (
    id                      VARCHAR(36)  PRIMARY KEY,
    tenant_id               VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id           VARCHAR(36)  NOT NULL REFERENCES collection(id),
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(500),
    active                  BOOLEAN      NOT NULL DEFAULT true,
    error_condition_formula TEXT         NOT NULL,
    error_message           VARCHAR(1000) NOT NULL,
    error_field             VARCHAR(100),
    evaluate_on             VARCHAR(20)  NOT NULL DEFAULT 'CREATE_AND_UPDATE',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_validation_rule_name UNIQUE (tenant_id, collection_id, name),
    CONSTRAINT chk_evaluate_on CHECK (evaluate_on IN ('CREATE', 'UPDATE', 'CREATE_AND_UPDATE'))
);

CREATE INDEX IF NOT EXISTS idx_validation_rule_collection ON validation_rule(collection_id, active);
CREATE INDEX IF NOT EXISTS idx_validation_rule_tenant ON validation_rule(tenant_id);
