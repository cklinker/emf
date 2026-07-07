-- V18: Create record_type and record_type_picklist tables
-- Phase 2 Stream D: Validation Rules & Record Types (D7)

CREATE TABLE IF NOT EXISTS record_type (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    is_default    BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_record_type_name UNIQUE (tenant_id, collection_id, name)
);

CREATE INDEX IF NOT EXISTS idx_record_type_collection ON record_type(collection_id, is_active);

-- Record-type-specific picklist value overrides
CREATE TABLE IF NOT EXISTS record_type_picklist (
    id               VARCHAR(36)  PRIMARY KEY,
    record_type_id   VARCHAR(36)  NOT NULL REFERENCES record_type(id) ON DELETE CASCADE,
    field_id         VARCHAR(36)  NOT NULL REFERENCES field(id),
    available_values JSONB        NOT NULL,
    default_value    VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_record_type_picklist UNIQUE (record_type_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_record_type_picklist_rt ON record_type_picklist(record_type_id);
