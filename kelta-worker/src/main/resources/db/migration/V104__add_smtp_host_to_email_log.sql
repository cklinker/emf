-- V104: Add smtp_host to email_log for audit trail
-- Tracks which SMTP server (platform default or tenant override) was used for each email

ALTER TABLE email_log ADD COLUMN smtp_host VARCHAR(255);
