-- Remove request_data table — request/response payloads are now logged at DEBUG level
-- instead of being stored in PostgreSQL.
DROP TABLE IF EXISTS request_data;
