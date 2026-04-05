-- Add S3 storage key column for module JAR files
ALTER TABLE tenant_module ADD COLUMN s3_key VARCHAR(2000);
