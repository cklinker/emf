# EPIC: EMF Worker Service — Collection Hosting, Auto-Scaling & Tenant Isolation

## 1. Problem Statement

Today, collections in EMF are served by statically deployed services (e.g., `sample-service`) that hard-code their collection definitions and register them with the control plane on startup. This has several limitations:

- **No dynamic assignment** — A collection is bound to a specific service at development time. Adding or removing collections requires code changes and redeployment.
- **No horizontal scaling** — A single service instance handles all collections assigned to it. Under load, there is no mechanism to add more instances and rebalance.
- **No tenant isolation** — Multiple tenants share the same worker process and database connection pool. A noisy tenant can degrade performance for others.
- **No zero-downtime provisioning** — Creating a new collection in the UI has no corresponding runtime to serve it until an operator manually deploys one.

The EMF Worker is a **generic, reusable runtime** that starts up empty, registers with the control plane, receives collection assignments, initializes storage, and begins serving API requests — all dynamically, with no collection-specific code.

---

## 2. Architecture Overview

```
┌──────────┐       ┌──────────────┐       ┌──────────────────────────────────┐
│  Client   │──────>│   Gateway    │──────>│         Worker Pool              │
│ (Browser/ │       │  (routes by  │       │                                  │
│  API)     │       │  collection) │       │  ┌─────────┐    ┌─────────┐     │
└──────────┘       └──────┬───────┘       │  │Worker-1 │    │Worker-2 │     │
                          │               │  │(tenant-a│    │(shared) │     │
                          │               │  │ orders, │    │ leads,  │     │
                  ┌───────▼───────┐       │  │ items)  │    │ contacts│     │
                  │ Control Plane │       │  └─────────┘    └─────────┘     │
                  │               │       │                                  │
                  │ - Worker      │       │  ┌─────────┐                    │
                  │   Registry    │       │  │Worker-3 │                    │
                  │ - Collection  │       │  │(tenant-b│                    │
                  │   Assignment  │       │  │ orders) │                    │
                  │ - Health      │       │  └─────────┘                    │
                  └───────────────┘       └──────────────────────────────────┘
```

### Key Principles

1. **Workers are cattle, not pets** — Any worker instance can serve any collection. Workers are interchangeable and disposable.
2. **Control plane is the brain** — All assignment decisions (which worker hosts which collection) are made by the control plane. Workers just execute.
3. **Gateway is the router** — The gateway knows which worker URL serves each collection and forwards requests accordingly. Routing updates are event-driven.
4. **Kubernetes is the muscle** — K8s handles pod lifecycle, health checks, scaling, and network routing. The system leverages K8s primitives rather than reimplementing them.

---

## 3. Component Design

### 3.1 EMF Worker Service (`emf-worker/`)

A new Spring Boot application built on `runtime-core`. It is a **generic collection host** with no hard-coded collections.

#### Lifecycle

```
Boot → Register with Control Plane → Receive Assignments → Initialize Storage → Serve Traffic → Heartbeat
```

1. **Startup**: Worker starts, generates a unique `workerId` (UUID), and reads its configuration (control plane URL, K8s pod name, namespace, labels).
2. **Registration**: Worker calls `POST /control/workers/register` with:
   ```json
   {
     "workerId": "w-abc123",
     "podName": "emf-worker-5f7d8c-xk4z2",
     "namespace": "emf",
     "host": "emf-worker-5f7d8c-xk4z2.emf-worker.emf.svc.cluster.local",
     "port": 8080,
     "capacity": 50,
     "labels": { "tenant": "shared", "pool": "default" },
     "status": "STARTING"
   }
   ```
3. **Assignment Fetch**: Control plane responds (or sends via Kafka) with collection assignments for this worker.
4. **Collection Initialization**: For each assigned collection, the worker:
   - Fetches the full collection definition (fields, validation rules, storage mode) from the control plane.
   - Registers it in the local `CollectionRegistry`.
   - Initializes storage via `StorageAdapter` (creates tables if needed, or connects to existing JSONB storage).
   - Marks the collection as `READY` in the local registry.
5. **Ready**: Worker updates its status to `READY`. The control plane publishes a Kafka event to the gateway, which adds routes pointing to this worker.
6. **Heartbeat**: Worker sends periodic heartbeats (`POST /control/workers/{id}/heartbeat`) every 15 seconds with:
   - Current load (active connections, request rate, memory usage)
   - List of collections being served with per-collection health
   - Status (`READY`, `DRAINING`, `OVERLOADED`)
7. **Shutdown**: On graceful shutdown (SIGTERM from K8s), the worker:
   - Sets status to `DRAINING`.
   - Control plane reassigns its collections to other workers.
   - Gateway routes are updated to point elsewhere.
   - Worker finishes in-flight requests and exits.

#### Key Classes

| Class | Responsibility |
|-------|---------------|
| `WorkerRegistrationService` | Registers with control plane, sends heartbeats |
| `CollectionAssignmentListener` | Kafka listener for assignment changes (ADD/REMOVE collection) |
| `CollectionLifecycleManager` | Initializes/tears down collections locally |
| `WorkerHealthReporter` | Collects metrics and reports to control plane |
| `DynamicCollectionRouter` | (from runtime-core) Serves REST endpoints for assigned collections |

#### Configuration

```yaml
emf:
  worker:
    id: ${WORKER_ID:${random.uuid}}
    pool: ${WORKER_POOL:default}
    capacity: ${WORKER_CAPACITY:50}  # max collections
    tenant-affinity: ${TENANT_AFFINITY:}  # empty = shared, or specific tenant ID
    heartbeat-interval: 15s
    control-plane-url: ${CONTROL_PLANE_URL:http://emf-control-plane:8080}
```

### 3.2 Control Plane Extensions

New tables and APIs to manage workers and collection assignments.

#### New Entities

**`worker` table:**
```sql
CREATE TABLE worker (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36),
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
```

Status values: `STARTING`, `READY`, `DRAINING`, `OFFLINE`, `FAILED`

**`collection_assignment` table:**
```sql
CREATE TABLE collection_assignment (
    id VARCHAR(36) PRIMARY KEY,
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    worker_id VARCHAR(36) NOT NULL REFERENCES worker(id),
    tenant_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ready_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(collection_id, worker_id)
);
```

Status values: `PENDING`, `INITIALIZING`, `READY`, `DRAINING`, `REMOVED`

#### New APIs

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/control/workers/register` | Worker self-registration |
| `POST` | `/control/workers/{id}/heartbeat` | Worker heartbeat with metrics |
| `POST` | `/control/workers/{id}/deregister` | Worker graceful shutdown |
| `GET` | `/control/workers` | List all workers (admin) |
| `GET` | `/control/workers/{id}` | Get worker details (admin) |
| `GET` | `/control/workers/{id}/assignments` | Get worker's collection assignments |
| `POST` | `/control/assignments/assign` | Manually assign collection to worker (admin) |
| `POST` | `/control/assignments/unassign` | Manually unassign collection (admin) |
| `GET` | `/control/assignments` | List all assignments (admin) |

#### New Kafka Topics

| Topic | Publisher | Consumer | Payload |
|-------|-----------|----------|---------|
| `emf.worker.assignment.changed` | Control Plane | Worker, Gateway | `{ workerId, collectionId, action: ASSIGN/UNASSIGN, workerUrl }` |
| `emf.worker.status.changed` | Control Plane | Gateway | `{ workerId, status, collections[] }` |

#### Assignment Algorithm

The control plane's `CollectionAssignmentService` decides which worker hosts each collection:

```
function assignCollection(collection, tenantId):
    1. Find candidate workers:
       - Status = READY
       - current_load < capacity
       - If collection has tenant_affinity → prefer workers with matching tenant_affinity
       - If no affinity → use workers in "default" pool

    2. Score candidates:
       - Lower load = higher score
       - Same-tenant collections already on worker = bonus (locality)
       - Same-database-url workers = bonus (connection reuse)

    3. Pick highest-scoring worker

    4. If no candidates:
       - Emit "scale-up-needed" event (triggers K8s HPA or KEDA)
       - Queue assignment for retry

    5. Create collection_assignment record
    6. Publish ASSIGN event to Kafka
```

### 3.3 Gateway Extensions

The gateway already supports dynamic routes via Kafka events. Extensions needed:

1. **Worker-aware route definitions**: `RouteDefinition.backendUrl` now points to a worker URL instead of a static service URL.
2. **Multi-worker routing**: When a collection is served by multiple workers (for HA), the gateway uses round-robin or weighted routing.
3. **Worker status listener**: Subscribes to `emf.worker.status.changed` to remove routes for workers that go offline.
4. **Health-aware routing**: If a worker heartbeat is stale (>45s), the gateway marks its routes as unhealthy and stops sending traffic.

The existing `ConfigEventListener` and `RouteRegistry` are extended (not replaced) to handle worker-based routes.

---

## 4. Kubernetes Scaling Strategy

### 4.1 Deployment Architecture

```yaml
# Worker Deployment — default shared pool
apiVersion: apps/v1
kind: Deployment
metadata:
  name: emf-worker-default
  namespace: emf
spec:
  replicas: 2  # minimum
  selector:
    matchLabels:
      app: emf-worker
      pool: default
  template:
    metadata:
      labels:
        app: emf-worker
        pool: default
    spec:
      containers:
        - name: emf-worker
          image: cklinker/emf-worker:latest
          env:
            - name: WORKER_POOL
              value: "default"
            - name: WORKER_CAPACITY
              value: "50"
            - name: CONTROL_PLANE_URL
              value: "http://emf-control-plane:8080"
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 15
      terminationGracePeriodSeconds: 60
---
# Headless Service for pod-to-pod DNS
apiVersion: v1
kind: Service
metadata:
  name: emf-worker
  namespace: emf
spec:
  clusterIP: None
  selector:
    app: emf-worker
  ports:
    - port: 8080
```

### 4.2 Horizontal Pod Autoscaler (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: emf-worker-default-hpa
  namespace: emf
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: emf-worker-default
  minReplicas: 2
  maxReplicas: 20
  metrics:
    # Scale on CPU utilization
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    # Scale on custom metric: collections per worker
    - type: Pods
      pods:
        metric:
          name: emf_worker_collection_count
        target:
          type: AverageValue
          averageValue: "40"  # target 40 collections per pod (80% of capacity)
```

### 4.3 KEDA (Event-Driven Autoscaling) — Alternative/Complement

For more sophisticated scaling based on Kafka queue depth or control-plane metrics:

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: emf-worker-scaler
  namespace: emf
spec:
  scaleTargetRef:
    name: emf-worker-default
  minReplicaCount: 2
  maxReplicaCount: 20
  triggers:
    # Scale when unassigned collections exist
    - type: metrics-api
      metadata:
        targetValue: "0"
        url: "http://emf-control-plane:8080/control/metrics/unassigned-collections"
        valueLocation: "count"
    # Scale on Kafka consumer lag
    - type: kafka
      metadata:
        bootstrapServers: kafka:9092
        consumerGroup: emf-worker
        topic: emf.worker.assignment.changed
        lagThreshold: "10"
```

### 4.4 Scale-Down and Draining

When K8s decides to scale down a worker pod:

1. **PreStop hook** sends `POST /control/workers/{id}/deregister` to control plane.
2. Control plane sets worker status to `DRAINING`.
3. Control plane reassigns the worker's collections to other healthy workers.
4. Gateway receives route updates via Kafka and stops sending new traffic.
5. Worker's `terminationGracePeriodSeconds` (60s) allows in-flight requests to complete.
6. Pod terminates.

---

## 5. Tenant Isolation

### 5.1 Isolation Levels

The system supports three isolation levels, configurable per tenant:

| Level | Description | Mechanism | Cost |
|-------|-------------|-----------|------|
| **Shared** (default) | Collections from any tenant can share workers | Default worker pool, no affinity | Lowest |
| **Pool Isolation** | Tenant gets a dedicated worker pool | Separate K8s Deployment with `tenant-affinity` label | Medium |
| **Full Isolation** | Tenant gets dedicated workers AND dedicated database | Separate Deployment + separate PostgreSQL schema/database | Highest |

### 5.2 Shared Pool (Default)

All tenants' collections run on workers in the `default` pool. Isolation is logical:
- Each collection's storage is in its own table (or JSONB partition).
- The `tenantId` column on all entities ensures data separation at the query level.
- No one tenant's collections are concentrated on a single worker — the assignment algorithm spreads them.

### 5.3 Pool Isolation (Dedicated Workers)

For tenants that need performance guarantees:

```yaml
# Dedicated worker pool for tenant "acme-corp"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: emf-worker-acme-corp
  namespace: emf
spec:
  replicas: 3
  selector:
    matchLabels:
      app: emf-worker
      pool: acme-corp
      tenant: acme-corp
  template:
    spec:
      containers:
        - name: emf-worker
          env:
            - name: WORKER_POOL
              value: "acme-corp"
            - name: TENANT_AFFINITY
              value: "acme-corp-tenant-id"
      # Optional: use node affinity for hardware isolation
      nodeSelector:
        tenant-tier: premium
```

When the control plane assigns collections for tenant `acme-corp`, it preferentially selects workers in the `acme-corp` pool.

### 5.4 Full Isolation (Dedicated Database)

For regulated or enterprise tenants:

- A separate PostgreSQL database (or schema) is provisioned.
- The worker's `SPRING_DATASOURCE_URL` points to the tenant-specific database.
- The `Service` entity in the control plane records the tenant-specific `databaseUrl`.
- Workers in this pool only receive collections for this tenant.

This already fits the existing `Service.databaseUrl` field — it just needs to be used by the worker at runtime.

### 5.5 Tenant Configuration (Control Plane)

New fields on the `tenant` table:

```sql
ALTER TABLE tenant ADD COLUMN isolation_level VARCHAR(20) DEFAULT 'SHARED';
ALTER TABLE tenant ADD COLUMN worker_pool VARCHAR(50);
ALTER TABLE tenant ADD COLUMN dedicated_database_url VARCHAR(500);
ALTER TABLE tenant ADD COLUMN max_collections INTEGER DEFAULT 100;
ALTER TABLE tenant ADD COLUMN max_workers INTEGER DEFAULT 10;
```

---

## 6. Use Cases

### UC-1: New Collection Auto-Provisioning

**Scenario**: Admin creates a new collection via the UI.

**Flow**:
1. Admin clicks "Create Collection" in the UI, fills in name, fields, storage mode.
2. UI calls `POST /control/collections` on the control plane.
3. Control plane saves the collection to the database.
4. Control plane's `CollectionAssignmentService` finds the best available worker.
5. Control plane creates a `collection_assignment` record (status: PENDING).
6. Control plane publishes `ASSIGN` event to Kafka topic `emf.worker.assignment.changed`.
7. The assigned worker receives the event, fetches the collection definition, initializes storage.
8. Worker reports collection status as READY via heartbeat.
9. Control plane publishes route update to gateway via `emf.config.collection.changed`.
10. Gateway adds a route for `/api/{collectionName}/**` pointing to the worker.
11. The collection is now live and serving traffic — no deployment required.

**Time to live**: ~5-10 seconds from creation to first request.

### UC-2: Horizontal Scaling Under Load

**Scenario**: A popular collection is getting high traffic, causing worker CPU to spike.

**Flow**:
1. Worker heartbeat reports high CPU/request rate.
2. K8s HPA detects `averageCpuUtilization > 70%` across the worker deployment.
3. HPA scales the deployment from 3 to 5 replicas.
4. Two new worker pods start and register with the control plane.
5. Control plane rebalances: moves some collections from overloaded workers to the new empty workers.
6. Old workers drain the moved collections; new workers initialize them.
7. Gateway routes update to reflect new worker assignments.
8. Load is now spread across 5 workers.

### UC-3: Tenant Onboarding with Isolation

**Scenario**: A new enterprise customer requires dedicated infrastructure.

**Flow**:
1. Platform admin creates tenant with `isolation_level: POOL_ISOLATED` and `worker_pool: acme`.
2. Platform admin deploys a dedicated `emf-worker-acme` K8s Deployment (via Helm values or ArgoCD app).
3. Workers in the `acme` pool start and register with `tenant_affinity: acme-tenant-id`.
4. When the acme tenant creates collections, they are assigned exclusively to workers in the `acme` pool.
5. Other tenants' traffic never reaches acme's workers.

### UC-4: Worker Failure Recovery

**Scenario**: A worker pod crashes or its node goes down.

**Flow**:
1. Worker stops sending heartbeats.
2. Control plane's `WorkerHealthMonitor` (scheduled task, runs every 30s) detects stale heartbeat (>45s).
3. Control plane marks the worker as `OFFLINE`.
4. Control plane reassigns the worker's collections to other healthy workers in the same pool.
5. Gateway receives route updates and stops sending traffic to the dead worker.
6. K8s restarts the failed pod (restart policy). When it comes back, it re-registers and may receive assignments again.
7. Total downtime for affected collections: ~30-60 seconds.

### UC-5: Zero-Downtime Collection Schema Migration

**Scenario**: Admin adds a new field to an existing collection.

**Flow**:
1. Admin adds a field via the UI → `PUT /control/collections/{id}` with new field definition.
2. Control plane increments `currentVersion`, creates new `CollectionVersion`.
3. Control plane publishes collection changed event to Kafka.
4. The worker hosting this collection receives the event.
5. Worker's `CollectionLifecycleManager` calls `StorageAdapter.updateCollectionSchema(oldDef, newDef)`.
6. For PHYSICAL_TABLE mode: runs `ALTER TABLE ADD COLUMN`.
7. For JSONB mode: no-op (schema is implicit).
8. Worker updates its local `CollectionRegistry` with the new definition.
9. New field is immediately available for reads and writes.

### UC-6: Bulk Collection Migration Between Pools

**Scenario**: A tenant is upgraded from shared to dedicated infrastructure.

**Flow**:
1. Platform admin updates tenant `isolation_level` from `SHARED` to `POOL_ISOLATED`.
2. Platform admin creates dedicated worker pool for the tenant.
3. Admin triggers `POST /control/assignments/migrate?tenantId=X&targetPool=Y`.
4. Control plane identifies all collections for tenant X on shared workers.
5. For each collection, control plane:
   a. Assigns to a worker in pool Y (status: PENDING).
   b. New worker initializes the collection.
   c. Once READY, control plane publishes route update (gateway switches traffic).
   d. Old worker's assignment is set to DRAINING, then REMOVED.
6. Migration is gradual — one collection at a time — ensuring zero downtime.

### UC-7: Multi-Region / Geo-Distributed Workers

**Scenario**: A global deployment needs workers close to users in different regions.

**Flow**:
1. Separate K8s clusters in `us-east`, `eu-west`, `ap-southeast`.
2. Each cluster runs its own worker pool with region labels.
3. Control plane (centralized or federated) assigns collections to region-specific pools based on tenant region preferences.
4. Gateway uses region-aware routing (e.g., Cloudflare Workers or regional ingress) to direct traffic to the nearest worker pool.
5. Workers in each region connect to regional database replicas for low-latency reads.

### UC-8: Development / Sandbox Environments

**Scenario**: Developers need isolated environments to test collection changes.

**Flow**:
1. Developer creates a "sandbox" via the UI.
2. Control plane provisions a lightweight worker in a `sandbox` pool (small resource limits).
3. Developer's collections are assigned to the sandbox worker.
4. Sandbox worker uses a separate database schema (or in-memory H2) for isolation.
5. Developer can test freely without affecting production.
6. Sandbox auto-expires after configurable TTL (e.g., 24 hours).

### UC-9: Canary Deployments for Worker Updates

**Scenario**: A new version of the worker needs to be rolled out safely.

**Flow**:
1. Operator deploys a canary worker pool with the new version (1 replica).
2. Control plane assigns a small subset of collections (e.g., 5%) to canary workers.
3. Gateway routes traffic for those collections to the canary.
4. Operator monitors error rates, latency, resource usage.
5. If healthy, operator scales canary to full and drains old workers.
6. If issues detected, operator rolls back by unassigning canary collections.

### UC-10: Resource Quota Enforcement

**Scenario**: A tenant has a plan limit of 20 collections.

**Flow**:
1. Tenant has `max_collections: 20` configured.
2. Tenant tries to create collection #21.
3. Control plane's `CollectionService` checks the count of active collections for this tenant.
4. Count exceeds `max_collections` → request rejected with `409 Conflict` and clear error message.
5. UI displays: "You have reached your collection limit (20). Please upgrade your plan or delete unused collections."

---

## 7. Data Flow Diagrams

### Collection Creation → Worker Assignment → Gateway Route

```
Admin UI                Control Plane              Kafka                 Worker              Gateway
   │                         │                       │                    │                    │
   │  POST /collections      │                       │                    │                    │
   │────────────────────────>│                       │                    │                    │
   │                         │  save collection      │                    │                    │
   │                         │  run assignment algo   │                    │                    │
   │                         │                       │                    │                    │
   │                         │  publish ASSIGN        │                    │                    │
   │                         │──────────────────────>│                    │                    │
   │                         │                       │  ASSIGN event      │                    │
   │                         │                       │───────────────────>│                    │
   │                         │                       │                    │  GET /collections/  │
   │                         │                       │                    │  {id}/full          │
   │                         │<──────────────────────────────────────────│                    │
   │                         │  collection definition │                    │                    │
   │                         │──────────────────────────────────────────>│                    │
   │                         │                       │                    │  init storage       │
   │                         │                       │                    │  register in        │
   │                         │                       │                    │  CollectionRegistry  │
   │                         │                       │                    │                    │
   │                         │       heartbeat (READY)│                    │                    │
   │                         │<──────────────────────────────────────────│                    │
   │                         │                       │                    │                    │
   │                         │  publish route update  │                    │                    │
   │                         │──────────────────────>│                    │                    │
   │                         │                       │  route event       │                    │
   │                         │                       │───────────────────────────────────────>│
   │                         │                       │                    │                    │
   │                         │                       │                    │    update           │
   │                         │                       │                    │    RouteRegistry     │
   │  201 Created            │                       │                    │                    │
   │<────────────────────────│                       │                    │                    │
```

### Request Flow Through Gateway to Worker

```
Client              Gateway                  Worker
  │                    │                       │
  │ GET /api/orders/1  │                       │
  │───────────────────>│                       │
  │                    │  lookup RouteRegistry  │
  │                    │  path=/api/orders/**   │
  │                    │  → backendUrl=worker-2 │
  │                    │                       │
  │                    │  authz check (policy)  │
  │                    │                       │
  │                    │  forward request       │
  │                    │──────────────────────>│
  │                    │                       │  DynamicCollectionRouter
  │                    │                       │  → StorageAdapter.getById()
  │                    │  200 OK + JSON         │
  │                    │<──────────────────────│
  │ 200 OK + JSON      │                       │
  │<───────────────────│                       │
```

---

## 8. Implementation Phases

### Phase W1: Worker Foundation (MVP)

**Goal**: A single generic worker that registers and serves collections.

| Task | Description |
|------|-------------|
| W1.1 | Create `emf-worker/` Maven module with Spring Boot, runtime-core dependency |
| W1.2 | Worker registration API in control plane (`worker` table, `WorkerController`, `WorkerService`) |
| W1.3 | `WorkerRegistrationService` in worker — registers on startup, heartbeats, deregisters on shutdown |
| W1.4 | `CollectionAssignmentService` in control plane — simple round-robin assignment |
| W1.5 | `collection_assignment` table and APIs |
| W1.6 | `CollectionAssignmentListener` in worker — Kafka consumer, initializes collections |
| W1.7 | `CollectionLifecycleManager` in worker — fetch definition, init storage, register routes |
| W1.8 | Gateway extension — accept worker-based route updates via new Kafka topics |
| W1.9 | Dockerfile and docker-compose entry for emf-worker |
| W1.10 | Integration test: create collection → worker picks it up → gateway routes to it |

### Phase W2: Health, Monitoring & Rebalancing

**Goal**: Production-ready health monitoring and automatic rebalancing.

| Task | Description |
|------|-------------|
| W2.1 | `WorkerHealthMonitor` in control plane — detect stale heartbeats, mark OFFLINE |
| W2.2 | Automatic reassignment on worker failure |
| W2.3 | Worker health metrics endpoint (`/actuator/metrics/emf.worker.*`) |
| W2.4 | Prometheus metrics integration (collection count, request rate, error rate, latency) |
| W2.5 | Rebalancing algorithm — redistribute when workers are unevenly loaded |
| W2.6 | Admin UI: Workers page (list workers, status, assignments, health) |

### Phase W3: Kubernetes Auto-Scaling

**Goal**: Workers scale automatically based on load.

| Task | Description |
|------|-------------|
| W3.1 | HPA configuration for worker deployment |
| W3.2 | Custom metrics exporter for collections-per-worker |
| W3.3 | KEDA scaler for unassigned-collections metric |
| W3.4 | PreStop hook for graceful draining |
| W3.5 | PodDisruptionBudget for safe rollouts |
| W3.6 | Helm chart for worker deployment (configurable pools, replicas, resources) |

### Phase W4: Tenant Isolation

**Goal**: Per-tenant worker pools and database isolation.

| Task | Description |
|------|-------------|
| W4.1 | Tenant isolation level configuration (SHARED / POOL_ISOLATED / FULL_ISOLATED) |
| W4.2 | Pool-aware assignment algorithm |
| W4.3 | Dedicated worker pool provisioning (Helm values per tenant) |
| W4.4 | Dynamic DataSource in worker — support per-tenant database URLs |
| W4.5 | Collection migration between pools (`/control/assignments/migrate`) |
| W4.6 | Tenant resource quota enforcement |
| W4.7 | Admin UI: Tenant isolation settings |

### Phase W5: Advanced Routing & HA

**Goal**: Multi-worker redundancy and intelligent routing.

| Task | Description |
|------|-------------|
| W5.1 | Multi-worker assignment — same collection on 2+ workers for HA |
| W5.2 | Gateway round-robin routing across multiple workers |
| W5.3 | Health-weighted routing (prefer healthy/low-latency workers) |
| W5.4 | Circuit breaker per worker in gateway |
| W5.5 | Canary deployment support (version-aware assignment) |

---

## 9. Technical Decisions

### Why Not Use K8s Service Mesh (Istio/Linkerd)?

A service mesh could handle some routing concerns, but:
- **Collection-level routing** is application-specific — a mesh routes at the service level, not the collection level.
- The gateway already has this logic and understands EMF's authorization model.
- Adding a mesh introduces operational complexity without clear benefit for this use case.

The gateway remains the collection-aware router. K8s handles pod lifecycle and scaling.

### Why Not a Sidecar Pattern?

Workers could theoretically be injected as sidecars into existing pods. However:
- Workers need their own lifecycle (register, heartbeat, drain).
- Collections need independent scaling (you want more workers when you have more collections, not more of something else).
- A standalone deployment is simpler to operate and reason about.

### Why Kafka for Assignment Events (Not HTTP Push)?

- **Reliability**: Kafka guarantees at-least-once delivery. If a worker misses an event, it can replay.
- **Decoupling**: Control plane doesn't need to know worker addresses to push events.
- **Scalability**: Multiple workers consume from the same topic with partitioning.
- **Consistency with existing patterns**: The gateway already uses Kafka for config events.

### Database Per Worker vs. Shared Database

Workers share the application database by default:
- `JSONB` mode: All collections use a shared `jsonb_records` table, partitioned by `collection_id`.
- `PHYSICAL_TABLE` mode: Each collection gets its own table, but in the same database.

For full tenant isolation, a dedicated database is used (configurable via `Service.databaseUrl`). The worker's `DataSource` can be switched dynamically based on the assigned collections' service configuration.

---

## 10. Migration Path

### From `sample-service` to `emf-worker`

The existing `sample-service` pattern (hard-coded collections, `CollectionInitializer`) will continue to work — it's a statically configured service. The worker is the *dynamic* equivalent.

Over time, teams can migrate from sample-service-style deployments to worker-based deployments:

1. Create collections via the UI (instead of code).
2. Let the control plane assign them to workers (instead of deploying a custom service).
3. Delete the custom service deployment.

The `Service` entity in the control plane already distinguishes between worker-backed services and custom services via a new `serviceType` field (`WORKER` vs `CUSTOM`).

---

## 11. Open Questions

1. **Database connection pooling**: Should each worker maintain its own connection pool, or should we use PgBouncer as a shared pool?
2. **Collection hot-migration**: Can we move a collection from one worker to another with zero downtime (dual-write period)?
3. **Worker versioning**: How do we handle schema incompatibilities when a new worker version is deployed?
4. **Cross-collection queries**: How do queries that span multiple collections (joins, lookups) work when collections are on different workers?
5. **Event sourcing**: Should we capture all record changes as events for audit/replay, and does the worker own that?
