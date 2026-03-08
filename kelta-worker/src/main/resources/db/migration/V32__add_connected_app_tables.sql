-- V32: Connected app and token tables
-- Part of Phase 5 Stream C: Connected Apps (5.3)

CREATE TABLE connected_app (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name                VARCHAR(200)  NOT NULL,
    description         VARCHAR(500),
    client_id           VARCHAR(100)  NOT NULL UNIQUE,
    client_secret_hash  VARCHAR(200)  NOT NULL,
    redirect_uris       JSONB         DEFAULT '[]',
    scopes              JSONB         DEFAULT '["api"]',
    ip_restrictions     JSONB         DEFAULT '[]',
    rate_limit_per_hour INTEGER       DEFAULT 10000,
    active              BOOLEAN       DEFAULT true,
    last_used_at        TIMESTAMP WITH TIME ZONE,
    created_by          VARCHAR(36)   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_connected_app UNIQUE (tenant_id, name)
);

CREATE TABLE connected_app_token (
    id                VARCHAR(36)   PRIMARY KEY,
    connected_app_id  VARCHAR(36)   NOT NULL REFERENCES connected_app(id) ON DELETE CASCADE,
    token_hash        VARCHAR(200)  NOT NULL,
    scopes            JSONB         NOT NULL,
    issued_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked           BOOLEAN       DEFAULT false,
    revoked_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_connected_app_tenant ON connected_app(tenant_id);
CREATE INDEX idx_connected_app_client ON connected_app(client_id);
CREATE INDEX idx_token_app ON connected_app_token(connected_app_id);
CREATE INDEX idx_token_hash ON connected_app_token(token_hash);
CREATE INDEX idx_token_expires ON connected_app_token(expires_at) WHERE revoked = false;
