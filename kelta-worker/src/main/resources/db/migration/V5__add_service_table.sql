-- V003: Add Service table and update Collection table with service_id foreign key
--
-- This migration adds support for multi-service architecture where each collection
-- belongs to a specific domain service. This enables:
-- - Clear ownership of collections by domain services
-- - Proper routing of DDL execution events to the correct service
-- - Database isolation per service
-- - Multi-tenant service deployment

-- Create service table
CREATE TABLE IF NOT EXISTS service (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    description VARCHAR(500),
    base_path VARCHAR(100) DEFAULT '/api',
    environment VARCHAR(50),
    database_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Create index on service name for lookups
CREATE INDEX idx_service_name ON service(name) WHERE active = true;

-- Create index on service environment for filtering
CREATE INDEX idx_service_environment ON service(environment) WHERE active = true;

-- Create a default service for existing collections
INSERT INTO service (id, name, display_name, description, base_path, environment, active, created_at, updated_at)
VALUES (
    gen_random_uuid()::text,
    'default-service',
    'Default Service',
    'Default domain service for existing collections',
    '/api',
    'development',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Add service_id column to collection table
ALTER TABLE collection ADD COLUMN service_id VARCHAR(36);

-- Set service_id for existing collections to the default service
UPDATE collection 
SET service_id = (SELECT id FROM service WHERE name = 'default-service')
WHERE service_id IS NULL;

-- Make service_id NOT NULL after backfilling
ALTER TABLE collection ALTER COLUMN service_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE collection 
ADD CONSTRAINT fk_collection_service 
FOREIGN KEY (service_id) REFERENCES service(id);

-- Create index on collection service_id for joins
CREATE INDEX idx_collection_service_id ON collection(service_id);

-- Create composite index for service + collection name lookups
CREATE INDEX idx_collection_service_name ON collection(service_id, name) WHERE active = true;

-- Add comment to document the relationship
COMMENT ON COLUMN collection.service_id IS 'Foreign key to the service that owns this collection. Each collection belongs to exactly one domain service.';
COMMENT ON TABLE service IS 'Domain services that host collections. Each service has its own database and can host multiple collections.';
