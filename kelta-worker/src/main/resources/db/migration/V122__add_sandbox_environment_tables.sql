-- ============================================================================
-- V122: Sandbox environment and metadata promotion tables
-- ============================================================================

-- Environments represent isolated sandbox/staging instances of a tenant's metadata
CREATE TABLE environment (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name            VARCHAR(200)  NOT NULL,
    description     TEXT,
    type            VARCHAR(20)   NOT NULL DEFAULT 'SANDBOX',
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    source_env_id   VARCHAR(36)   REFERENCES environment(id),
    config          JSONB,
    created_by      VARCHAR(255),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_env_type   CHECK (type IN ('PRODUCTION', 'SANDBOX', 'STAGING')),
    CONSTRAINT chk_env_status CHECK (status IN ('CREATING', 'ACTIVE', 'ARCHIVED', 'FAILED'))
);

CREATE INDEX idx_environment_tenant ON environment (tenant_id);
CREATE INDEX idx_environment_type   ON environment (type);
CREATE UNIQUE INDEX idx_environment_tenant_name ON environment (tenant_id, name);

-- Metadata snapshots capture the state of metadata at a point in time
CREATE TABLE metadata_snapshot (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    environment_id  VARCHAR(36)   NOT NULL REFERENCES environment(id),
    name            VARCHAR(200)  NOT NULL,
    snapshot_data   JSONB         NOT NULL,
    item_count      INTEGER       NOT NULL DEFAULT 0,
    created_by      VARCHAR(255),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metadata_snapshot_env ON metadata_snapshot (environment_id);

-- Promotions track metadata movement between environments
CREATE TABLE environment_promotion (
    id                  VARCHAR(36)   NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    source_env_id       VARCHAR(36)   NOT NULL REFERENCES environment(id),
    target_env_id       VARCHAR(36)   NOT NULL REFERENCES environment(id),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    promotion_type      VARCHAR(20)   NOT NULL DEFAULT 'FULL',
    snapshot_id         VARCHAR(36)   REFERENCES metadata_snapshot(id),
    changes_summary     JSONB,
    items_promoted      INTEGER       NOT NULL DEFAULT 0,
    items_skipped       INTEGER       NOT NULL DEFAULT 0,
    items_failed        INTEGER       NOT NULL DEFAULT 0,
    error_message       TEXT,
    approved_by         VARCHAR(255),
    approved_at         TIMESTAMP,
    promoted_by         VARCHAR(255),
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_promo_status CHECK (status IN ('PENDING', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ROLLED_BACK')),
    CONSTRAINT chk_promo_type   CHECK (promotion_type IN ('FULL', 'SELECTIVE'))
);

CREATE INDEX idx_env_promotion_tenant    ON environment_promotion (tenant_id);
CREATE INDEX idx_env_promotion_source    ON environment_promotion (source_env_id);
CREATE INDEX idx_env_promotion_target    ON environment_promotion (target_env_id);
CREATE INDEX idx_env_promotion_status    ON environment_promotion (status);
CREATE INDEX idx_env_promotion_created   ON environment_promotion (created_at DESC);

-- Tracks individual items within a selective promotion
CREATE TABLE promotion_item (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY,
    promotion_id    VARCHAR(36)   NOT NULL REFERENCES environment_promotion(id) ON DELETE CASCADE,
    item_type       VARCHAR(30)   NOT NULL,
    item_id         VARCHAR(36)   NOT NULL,
    item_name       VARCHAR(200),
    action          VARCHAR(20)   NOT NULL DEFAULT 'CREATE',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,

    CONSTRAINT chk_promo_item_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'SKIP')),
    CONSTRAINT chk_promo_item_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_promotion_item_promo ON promotion_item (promotion_id);
