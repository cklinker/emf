-- Worker registry table
CREATE TABLE worker (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) DEFAULT 'default',
    pod_name VARCHAR(253),
    namespace VARCHAR(63),
    host VARCHAR(253) NOT NULL,
    port INTEGER NOT NULL DEFAULT 8080,
    base_url VARCHAR(500) NOT NULL,
    pool VARCHAR(50) NOT NULL DEFAULT 'default',
    capacity INTEGER NOT NULL DEFAULT 50,
    current_load INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTING',
    tenant_affinity VARCHAR(36),
    labels JSONB,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_worker_status ON worker(status);
CREATE INDEX idx_worker_pool ON worker(pool);
CREATE INDEX idx_worker_tenant_affinity ON worker(tenant_affinity);

-- Collection assignment table
CREATE TABLE collection_assignment (
    id VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    worker_id VARCHAR(36) NOT NULL REFERENCES worker(id),
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'default',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ready_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(collection_id, worker_id)
);

CREATE INDEX idx_collection_assignment_worker ON collection_assignment(worker_id);
CREATE INDEX idx_collection_assignment_collection ON collection_assignment(collection_id);
CREATE INDEX idx_collection_assignment_status ON collection_assignment(status);
