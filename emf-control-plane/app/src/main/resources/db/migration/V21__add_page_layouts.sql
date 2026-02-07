-- V21: Structured page layout model
-- Part of Phase 3 Stream A: Page Layouts (3.1)

CREATE TABLE page_layout (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    layout_type   VARCHAR(20)  DEFAULT 'DETAIL',
    is_default    BOOLEAN      DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_layout UNIQUE (tenant_id, collection_id, name)
);

CREATE TABLE layout_section (
    id         VARCHAR(36)  PRIMARY KEY,
    layout_id  VARCHAR(36)  NOT NULL REFERENCES page_layout(id) ON DELETE CASCADE,
    heading    VARCHAR(200),
    columns    INTEGER      DEFAULT 2,
    sort_order INTEGER      NOT NULL,
    collapsed  BOOLEAN      DEFAULT false,
    style      VARCHAR(20)  DEFAULT 'DEFAULT'
);
CREATE INDEX idx_section_layout ON layout_section(layout_id, sort_order);

CREATE TABLE layout_field (
    id                      VARCHAR(36) PRIMARY KEY,
    section_id              VARCHAR(36) NOT NULL REFERENCES layout_section(id) ON DELETE CASCADE,
    field_id                VARCHAR(36) NOT NULL REFERENCES field(id),
    column_number           INTEGER     DEFAULT 1,
    sort_order              INTEGER     NOT NULL,
    is_required_on_layout   BOOLEAN     DEFAULT false,
    is_read_only_on_layout  BOOLEAN     DEFAULT false
);
CREATE INDEX idx_layout_field ON layout_field(section_id, sort_order);

CREATE TABLE layout_related_list (
    id                      VARCHAR(36) PRIMARY KEY,
    layout_id               VARCHAR(36) NOT NULL REFERENCES page_layout(id) ON DELETE CASCADE,
    related_collection_id   VARCHAR(36) NOT NULL REFERENCES collection(id),
    relationship_field_id   VARCHAR(36) NOT NULL REFERENCES field(id),
    display_columns         JSONB       NOT NULL,
    sort_field              VARCHAR(100),
    sort_direction          VARCHAR(4)  DEFAULT 'DESC',
    row_limit               INTEGER     DEFAULT 10,
    sort_order              INTEGER     NOT NULL
);

CREATE TABLE layout_assignment (
    id             VARCHAR(36) PRIMARY KEY,
    tenant_id      VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id  VARCHAR(36) NOT NULL REFERENCES collection(id),
    profile_id     VARCHAR(36) NOT NULL REFERENCES profile(id),
    record_type_id VARCHAR(36) REFERENCES record_type(id),
    layout_id      VARCHAR(36) NOT NULL REFERENCES page_layout(id),
    CONSTRAINT uq_layout_assign UNIQUE (tenant_id, collection_id, profile_id, record_type_id)
);
