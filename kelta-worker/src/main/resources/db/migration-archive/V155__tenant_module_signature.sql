-- V155: Persist the publisher signature verified at module install so the JAR
-- can be re-verified on every load (defense-in-depth vs S3 tamper).
ALTER TABLE tenant_module ADD COLUMN IF NOT EXISTS jar_signature TEXT;
