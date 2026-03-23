-- V106: Password policy and password history tables
-- Per-tenant configurable password policies with NIST SP 800-63B aligned defaults

CREATE TABLE password_policy (
    id                      VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tenant_id               VARCHAR(36) NOT NULL UNIQUE REFERENCES tenant(id),
    min_length              INTEGER NOT NULL DEFAULT 8,
    max_length              INTEGER NOT NULL DEFAULT 128,
    require_uppercase       BOOLEAN NOT NULL DEFAULT false,
    require_lowercase       BOOLEAN NOT NULL DEFAULT false,
    require_digit           BOOLEAN NOT NULL DEFAULT false,
    require_special         BOOLEAN NOT NULL DEFAULT false,
    history_count           INTEGER NOT NULL DEFAULT 3,
    dictionary_check        BOOLEAN NOT NULL DEFAULT true,
    personal_data_check     BOOLEAN NOT NULL DEFAULT true,
    lockout_threshold       INTEGER NOT NULL DEFAULT 5,
    lockout_duration_minutes INTEGER NOT NULL DEFAULT 30,
    max_age_days            INTEGER,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE password_history (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         VARCHAR(36) NOT NULL,
    password_hash   VARCHAR(500) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);
