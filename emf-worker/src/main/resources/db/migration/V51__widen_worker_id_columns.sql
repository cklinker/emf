-- V51: Widen worker ID columns to accommodate Kubernetes pod names
--
-- The worker.id column was VARCHAR(36) (UUID-sized), but when WORKER_ID is set
-- to the Kubernetes pod name (e.g. "emf-worker-threadline-6bdd85cf4c-krllx"),
-- it can exceed 36 characters. K8s pod names can be up to 253 characters.
--
-- Also widens collection_assignment.worker_id which has a FK to worker(id).

-- Drop the FK constraint first
ALTER TABLE collection_assignment DROP CONSTRAINT IF EXISTS collection_assignment_worker_id_fkey;

-- Widen worker.id to accommodate pod names (up to 253 chars in K8s)
ALTER TABLE worker ALTER COLUMN id TYPE VARCHAR(253);

-- Widen collection_assignment.worker_id to match
ALTER TABLE collection_assignment ALTER COLUMN worker_id TYPE VARCHAR(253);

-- Re-add the FK constraint
ALTER TABLE collection_assignment
    ADD CONSTRAINT collection_assignment_worker_id_fkey
    FOREIGN KEY (worker_id) REFERENCES worker(id);
