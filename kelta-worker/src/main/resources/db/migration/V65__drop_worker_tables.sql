-- V65: Drop worker lifecycle tables
-- Worker registration, heartbeat, and assignment tracking have been removed.
-- Kubernetes manages pod health via liveness/readiness probes.
-- All workers serve all collections; no per-worker assignment is needed.

DROP TABLE IF EXISTS collection_assignment;
DROP TABLE IF EXISTS worker;
