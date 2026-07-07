-- V108: SMS verification for OTP-based authentication
-- Supports rate-limited, time-limited verification codes with SHA-256 hashing

CREATE TABLE sms_verification (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    phone       VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(128) NOT NULL,  -- SHA-256 hex hash
    tenant_id   VARCHAR(36) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT false,
    attempts    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sms_verification_phone ON sms_verification(phone, tenant_id, created_at DESC);

-- Add phone_number to platform_user for SMS MFA
ALTER TABLE platform_user ADD COLUMN phone_number VARCHAR(20);
