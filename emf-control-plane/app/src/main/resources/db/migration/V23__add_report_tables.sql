-- V23: Report builder tables
-- Part of Phase 3 Stream C: Report Builder (3.3)

CREATE TABLE report_folder (
    id            VARCHAR(36)   PRIMARY KEY,
    tenant_id     VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name          VARCHAR(100)  NOT NULL,
    access_level  VARCHAR(20)   DEFAULT 'PRIVATE',
    created_by    VARCHAR(36)   NOT NULL REFERENCES platform_user(id),
    CONSTRAINT uq_report_folder UNIQUE (tenant_id, name, created_by)
);

CREATE TABLE report (
    id                    VARCHAR(36)   PRIMARY KEY,
    tenant_id             VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name                  VARCHAR(200)  NOT NULL,
    description           VARCHAR(1000),
    report_type           VARCHAR(20)   NOT NULL,
    primary_collection_id VARCHAR(36)   NOT NULL REFERENCES collection(id),
    related_joins         JSONB         DEFAULT '[]',
    columns               JSONB         NOT NULL,
    filters               JSONB         DEFAULT '[]',
    filter_logic          VARCHAR(500),
    row_groupings         JSONB         DEFAULT '[]',
    column_groupings      JSONB         DEFAULT '[]',
    sort_order            JSONB         DEFAULT '[]',
    chart_type            VARCHAR(20),
    chart_config          JSONB,
    scope                 VARCHAR(20)   DEFAULT 'MY_RECORDS',
    folder_id             VARCHAR(36)   REFERENCES report_folder(id),
    access_level          VARCHAR(20)   DEFAULT 'PRIVATE',
    created_by            VARCHAR(36)   NOT NULL REFERENCES platform_user(id),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_tenant ON report(tenant_id);
CREATE INDEX idx_report_collection ON report(primary_collection_id);
CREATE INDEX idx_report_folder ON report(folder_id);
CREATE INDEX idx_report_user ON report(created_by);
