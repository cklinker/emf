-- V24: Dashboard builder tables
-- Part of Phase 3 Stream D: Dashboard Builder (3.4)

CREATE TABLE dashboard (
    id               VARCHAR(36)   PRIMARY KEY,
    tenant_id        VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(1000),
    folder_id        VARCHAR(36)   REFERENCES report_folder(id),
    access_level     VARCHAR(20)   DEFAULT 'PRIVATE',
    is_dynamic       BOOLEAN       DEFAULT false,
    running_user_id  VARCHAR(36)   REFERENCES platform_user(id),
    column_count     INTEGER       DEFAULT 3,
    created_by       VARCHAR(36)   NOT NULL REFERENCES platform_user(id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE dashboard_component (
    id              VARCHAR(36) PRIMARY KEY,
    dashboard_id    VARCHAR(36) NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    report_id       VARCHAR(36) NOT NULL REFERENCES report(id),
    component_type  VARCHAR(20) NOT NULL,
    title           VARCHAR(200),
    column_position INTEGER     NOT NULL,
    row_position    INTEGER     NOT NULL,
    column_span     INTEGER     DEFAULT 1,
    row_span        INTEGER     DEFAULT 1,
    config          JSONB       DEFAULT '{}',
    sort_order      INTEGER     NOT NULL
);

CREATE INDEX idx_dashboard_tenant ON dashboard(tenant_id);
CREATE INDEX idx_dashboard_component ON dashboard_component(dashboard_id, sort_order);
