-- V154: Per-job configuration blob for scheduled jobs.
-- REPORT_EXPORT jobs store email delivery config: {"recipients": ["a@x.com", ...]}.
ALTER TABLE scheduled_job ADD COLUMN IF NOT EXISTS config JSONB;
