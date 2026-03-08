-- H2 initialization script for JSONB compatibility testing
-- H2 doesn't have native JSONB support, but we can use JSON type and functions

-- Set PostgreSQL compatibility mode
SET MODE PostgreSQL;

-- Note: H2's JSON support is limited compared to PostgreSQL's JSONB
-- For full JSONB testing, use Testcontainers with PostgreSQL
