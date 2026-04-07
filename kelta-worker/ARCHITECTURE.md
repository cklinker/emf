# kelta-worker Architecture

## Overview

`kelta-worker` is the primary backend service of the Kelta platform. It owns the PostgreSQL schema (via Flyway), hosts all collection REST endpoints, executes business logic workflows, and publishes record change events to Kafka.

## System Diagram

```
                    ┌──────────────────────────────────────────┐
                    │               kelta-worker                │
                    ├──────────────────────────────────────────┤
  Kafka ──────────► │ CollectionSchemaListener                 │ ◄── schema change events
                    │ WorkflowEventListener                    │ ◄── record change events
                    ├──────────────────────────────────────────┤
  HTTP  ──────────► │ DynamicCollectionRouter                  │ ◄── JSON:API CRUD for all collections
  (via gateway)     │ InternalBootstrapController              │ ◄── gateway bootstrap, permissions
                    │ GovernorLimitsController                 │ ◄── rate limit config + usage
                    ├──────────────────────────────────────────┤
                    │ CollectionLifecycleManager               │ ◄── init, refresh, teardown
                    │ WorkflowEngine                           │ ◄── rule evaluation + execution
                    │ ScheduledWorkflowExecutor                │ ◄── polls for due scheduled rules
                    │ KafkaRecordEventPublisher                │ ◄── publishes to kelta.record.changed
                    ├──────────────────────────────────────────┤
                    │ PostgreSQL (Flyway — V1 through V67)     │
                    │ Redis (rate limit counters)              │
                    │ Kafka (event publishing + consuming)     │
                    │ S3 (optional — attachment presigned URLs)│
                    └──────────────────────────────────────────┘
```

## Subsystems

### Collection Lifecycle

The `CollectionLifecycleManager` is responsible for the full lifecycle of each configurable collection:

1. **Init** — On startup, `WorkerBootstrapService` loads all active collections from the database and registers them with `CollectionLifecycleManager`.
2. **Refresh** — When a `kelta.config.collection.changed` Kafka event arrives (consumed by `CollectionSchemaListener`), the manager re-registers the affected collection with updated schema.
3. **Teardown** — When a collection is deleted, the manager deregisters it and removes its routing.

`DynamicCollectionRouter` uses the registered collection metadata to route incoming HTTP requests to the appropriate action handlers in `runtime-module-core`.

### Workflow Execution

The workflow engine evaluates business rules and executes actions in response to record changes.

**Event-driven workflows:**
- `KafkaRecordEventPublisher` publishes to `kelta.record.changed` after every CRUD operation.
- `WorkflowEventListener` consumes these events and invokes `WorkflowEngine`.
- `WorkflowEngine` evaluates trigger conditions and executes matching rule actions (field updates, notifications, webhooks, etc.).

**Scheduled workflows:**
- `ScheduledWorkflowExecutor` polls every 60 seconds for workflow rules with due scheduled triggers.
- Uses optimistic locking to prevent duplicate execution across pods.

### Kafka Event Processing

The worker both publishes and consumes Kafka events:

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `kelta.record.changed` | Publish | Notify all services of record CRUD |
| `kelta.config.collection.changed` | Consume | Refresh collection registry on all pods |
| `kelta.record.changed` | Consume | Trigger workflow evaluation |

**Critical rule:** Configuration changes that affect in-memory state must be published to Kafka so all pods refresh. Never call `lifecycleManager.refreshX()` directly from a save hook.

### Flyway Migrations

Flyway is the sole owner of the database schema. The worker runs migrations on startup.

- Migration files: `src/main/resources/db/migration/V{n}__{description}.sql`
- Current range: V1 through V67
- Always check the current max version before adding a new migration

Key migration ranges:

| Range | Content |
|-------|---------|
| V1–V3 | Core schema (collections, fields, tenants), OIDC seeding |
| V10–V12 | Users, permissions, sharing |
| V15 | Picklist tables |
| V26 | Workflow rules |
| V39 | Worker-specific tables |
| V42–V43 | Notes, attachments, system collections |
| V50 | Demo data |
| V55 | Enhanced permission model |
| V59–V62 | Workflow engine foundation |
| V66–V67 | BaseEntity columns for junction/layout tables |

### Authorization (Cerbos)

Cerbos handles fine-grained authorization for collection record operations. The worker calls Cerbos on each request to check permissions against resource policies. Results are cached in Caffeine (in-memory, per-pod).

### Webhooks (Svix)

Outbound webhooks are delivered via Svix. The workflow engine triggers Svix deliveries as a workflow action type.

### Governor Limits

Per-tenant API limits are tracked and enforced:

| Limit | Default |
|-------|---------|
| API calls/day | 100,000 |
| Storage | 10 GB |
| Max users | 100 |
| Max collections | 200 |
| Max fields/collection | 500 |
| Max workflows | 50 |
| Max reports | 200 |

Rate limit counters are stored in Redis. `GovernorLimitsController` exposes limit config to the gateway for enforcement.

## Package Structure

```
io.kelta.worker
├── controller/       # InternalBootstrapController, GovernorLimitsController
├── service/          # WorkerBootstrapService, CollectionLifecycleManager, S3StorageService
├── listener/         # CollectionSchemaListener, WorkflowEventListener
├── event/            # KafkaRecordEventPublisher
├── workflow/         # WorkflowEngine, ScheduledWorkflowExecutor
├── filter/           # RequestMetricsFilter
├── advice/           # AttachmentUrlEnricher
└── config/           # Kafka, storage, metrics, S3 configuration beans
```

## Observability

Prometheus metrics at `/actuator/prometheus`:

| Metric | Type | Description |
|--------|------|-------------|
| `kelta_worker_request_total` | Counter | Requests by collection, method, status |
| `kelta_worker_request_duration_seconds` | Histogram | Latency by collection, method |
| `kelta_worker_error_total` | Counter | Errors by collection, error type |
| `kelta.worker.collections.active` | Gauge | Active collection count |
| `kelta.worker.collection.count` | Gauge | Collection count (used for HPA) |

Structured JSON logs via Logstash encoder. Traces exported via OpenTelemetry OTLP.
