-- EMF PostgreSQL Initialization Script
-- Creates additional databases if needed for other services

-- Ensure the main database exists (already created by POSTGRES_DB env var)
-- This script can be extended for additional setup

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE emf_control_plane TO emf;

-- NOTE: Collection tables (projects, tasks) will be created automatically
-- by the EMF runtime-core StorageAdapter when the sample service starts.
-- The StorageAdapter.initializeCollection() method generates and executes
-- CREATE TABLE statements based on collection definitions, including:
-- - Column definitions with appropriate types
-- - Primary key constraints
-- - Foreign key constraints for relationships
-- - Indexes for foreign keys and unique constraints
-- - created_at and updated_at timestamp columns

-- Log completion
DO $
BEGIN
    RAISE NOTICE 'EMF PostgreSQL initialization complete';
END $;
