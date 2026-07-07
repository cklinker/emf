-- V22: List views for saved collection queries
-- Part of Phase 3 Stream B: List Views (3.2)

CREATE TABLE list_view (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id   VARCHAR(36)  NOT NULL REFERENCES collection(id),
    name            VARCHAR(100) NOT NULL,
    created_by      VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    visibility      VARCHAR(20)  DEFAULT 'PRIVATE',
    is_default      BOOLEAN      DEFAULT false,
    columns         JSONB        NOT NULL,
    filter_logic    VARCHAR(500),
    filters         JSONB        NOT NULL DEFAULT '[]',
    sort_field      VARCHAR(100),
    sort_direction  VARCHAR(4)   DEFAULT 'ASC',
    row_limit       INTEGER      DEFAULT 50,
    chart_config    JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_list_view UNIQUE (tenant_id, collection_id, name, created_by)
);

CREATE INDEX idx_list_view_collection ON list_view(tenant_id, collection_id);
CREATE INDEX idx_list_view_user ON list_view(created_by);
