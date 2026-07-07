-- V15: Picklist management tables
-- Stream B: Phase 2

-- Global picklist value set (reusable across multiple fields)
CREATE TABLE global_picklist (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    sorted        BOOLEAN      DEFAULT false,
    restricted    BOOLEAN      DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_global_picklist UNIQUE (tenant_id, name)
);

-- Picklist values (shared between field-specific and global picklists)
CREATE TABLE picklist_value (
    id                   VARCHAR(36)  PRIMARY KEY,
    picklist_source_type VARCHAR(20)  NOT NULL,
    picklist_source_id   VARCHAR(36)  NOT NULL,
    value                VARCHAR(255) NOT NULL,
    label                VARCHAR(255) NOT NULL,
    is_default           BOOLEAN      DEFAULT false,
    is_active            BOOLEAN      DEFAULT true,
    sort_order           INTEGER      DEFAULT 0,
    color                VARCHAR(20),
    description          VARCHAR(500),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_picklist_value UNIQUE (picklist_source_type, picklist_source_id, value),
    CONSTRAINT chk_source_type CHECK (picklist_source_type IN ('FIELD', 'GLOBAL'))
);
CREATE INDEX idx_picklist_source ON picklist_value(picklist_source_type, picklist_source_id);

-- Dependent picklist mapping (controlling field -> dependent field)
CREATE TABLE picklist_dependency (
    id                    VARCHAR(36)  PRIMARY KEY,
    controlling_field_id  VARCHAR(36)  NOT NULL REFERENCES field(id),
    dependent_field_id    VARCHAR(36)  NOT NULL REFERENCES field(id),
    mapping               JSONB        NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_picklist_dep UNIQUE (controlling_field_id, dependent_field_id)
);
