-- V135: Per-user email verification + welcome-sent tracking.
--
-- email_verified flips to TRUE after the user clicks the verification link.
-- welcomed_at is stamped after the user's first successful login so we only
-- send the welcome email once.

ALTER TABLE platform_user
    ADD COLUMN IF NOT EXISTS email_verified                BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verification_token      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS welcomed_at                   TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_user_email_verification_token
    ON platform_user(email_verification_token)
    WHERE email_verification_token IS NOT NULL;

COMMENT ON COLUMN platform_user.email_verified IS
    'TRUE once the user has confirmed ownership of their email address.';
COMMENT ON COLUMN platform_user.welcomed_at IS
    'Timestamp of the first successful login. Prevents the welcome email being sent twice.';
