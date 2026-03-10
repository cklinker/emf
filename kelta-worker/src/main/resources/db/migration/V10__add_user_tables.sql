-- V10: Create platform_user and login_history tables
-- Part of Phase 1 Stream B: Users & Authentication

-- ============================================================================
-- PLATFORM_USER TABLE
-- ============================================================================

CREATE TABLE platform_user (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    email           VARCHAR(320) NOT NULL,
    username        VARCHAR(100),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    locale          VARCHAR(10)  DEFAULT 'en_US',
    timezone        VARCHAR(50)  DEFAULT 'UTC',
    profile_id      VARCHAR(36),
    manager_id      VARCHAR(36)  REFERENCES platform_user(id),
    last_login_at   TIMESTAMP WITH TIME ZONE,
    login_count     INTEGER      DEFAULT 0,
    mfa_enabled     BOOLEAN      DEFAULT false,
    settings        JSONB        DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'PENDING_ACTIVATION'))
);

CREATE INDEX idx_user_tenant  ON platform_user(tenant_id);
CREATE INDEX idx_user_email   ON platform_user(tenant_id, email);
CREATE INDEX idx_user_manager ON platform_user(manager_id);
CREATE INDEX idx_user_status  ON platform_user(tenant_id, status);

-- ============================================================================
-- LOGIN_HISTORY TABLE
-- ============================================================================

CREATE TABLE login_history (
    id              VARCHAR(36)  PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    login_time      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    source_ip       VARCHAR(45),
    login_type      VARCHAR(20)  NOT NULL DEFAULT 'UI',
    status          VARCHAR(20)  NOT NULL,
    user_agent      VARCHAR(500),
    CONSTRAINT chk_login_type   CHECK (login_type IN ('UI', 'API', 'OAUTH', 'SERVICE_ACCOUNT')),
    CONSTRAINT chk_login_status CHECK (status IN ('SUCCESS', 'FAILED', 'LOCKED_OUT'))
);

CREATE INDEX idx_login_user   ON login_history(user_id, login_time DESC);
CREATE INDEX idx_login_tenant ON login_history(tenant_id, login_time DESC);
