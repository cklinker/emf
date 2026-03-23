-- V107: TOTP MFA tables for multi-factor authentication
-- Supports per-user TOTP enrollment with encrypted secrets and single-use recovery codes

CREATE TABLE user_totp_secret (
    id                  VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id             VARCHAR(36) NOT NULL UNIQUE REFERENCES platform_user(id),
    secret              VARCHAR(500) NOT NULL,          -- AES-256-GCM encrypted base32 secret
    verified            BOOLEAN NOT NULL DEFAULT false,
    last_used_at        BIGINT,                         -- epoch second of last accepted TOTP code (replay prevention)
    mfa_failed_attempts INTEGER NOT NULL DEFAULT 0,     -- rate limiting counter
    mfa_locked_until    TIMESTAMP WITH TIME ZONE,       -- MFA-specific lockout
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE user_recovery_code (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id     VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    code_hash   VARCHAR(500) NOT NULL,  -- BCrypt hash
    used        BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_recovery_code_user ON user_recovery_code(user_id);

-- Add mfa_required to password_policy for per-tenant enforcement
ALTER TABLE password_policy ADD COLUMN mfa_required BOOLEAN NOT NULL DEFAULT false;
