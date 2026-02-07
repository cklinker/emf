# Phase 5 — Integration and Scripting

> Opens the platform to external systems and custom logic. Builds on Phase 4's automation engine
> to provide server-side scripting, webhooks, connected app authentication, and bulk/composite APIs.

## Overview

Phase 5 consists of 4 streams implemented across Flyway migrations V30–V33:

| Stream | Feature | Migration | Description |
|--------|---------|-----------|-------------|
| A | Server-Side Scripting Engine | V30 | Sandboxed JavaScript execution for custom business logic |
| B | Webhooks | V31 | Push event notifications to external systems |
| C | Connected Apps | V32 | OAuth2 client credentials for external app access |
| D | Bulk & Composite APIs | V33 | High-volume data operations and multi-request batching |

---

## Stream A: Server-Side Scripting Engine (5.1)

### Purpose
Allow tenant developers to write custom business logic (JavaScript) that runs on the server in a sandboxed environment. Scripts can be bound to record triggers, scheduled execution, or exposed as custom API endpoints.

### Flyway Migration V30 — `V30__add_script_tables.sql`

```sql
CREATE TABLE script (
    id                VARCHAR(36)   PRIMARY KEY,
    tenant_id         VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name              VARCHAR(200)  NOT NULL,
    description       VARCHAR(500),
    script_type       VARCHAR(30)   NOT NULL,
    language          VARCHAR(20)   DEFAULT 'javascript',
    source_code       TEXT          NOT NULL,
    active            BOOLEAN       DEFAULT true,
    version           INTEGER       DEFAULT 1,
    created_by        VARCHAR(36)   NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_script UNIQUE (tenant_id, name),
    CONSTRAINT chk_script_type CHECK (script_type IN (
        'BEFORE_TRIGGER','AFTER_TRIGGER','SCHEDULED','API_ENDPOINT',
        'VALIDATION','EVENT_HANDLER','EMAIL_HANDLER'
    ))
);

CREATE TABLE script_trigger (
    id               VARCHAR(36) PRIMARY KEY,
    script_id        VARCHAR(36) NOT NULL REFERENCES script(id) ON DELETE CASCADE,
    collection_id    VARCHAR(36) NOT NULL REFERENCES collection(id),
    trigger_event    VARCHAR(20) NOT NULL,
    execution_order  INTEGER     DEFAULT 0,
    active           BOOLEAN     DEFAULT true,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_script_trigger UNIQUE (script_id, collection_id, trigger_event),
    CONSTRAINT chk_trigger_event CHECK (trigger_event IN ('INSERT','UPDATE','DELETE'))
);

CREATE TABLE script_execution_log (
    id               VARCHAR(36) PRIMARY KEY,
    tenant_id        VARCHAR(36) NOT NULL,
    script_id        VARCHAR(36) NOT NULL REFERENCES script(id),
    status           VARCHAR(20) NOT NULL,
    trigger_type     VARCHAR(30),
    record_id        VARCHAR(36),
    duration_ms      INTEGER,
    cpu_ms           INTEGER,
    queries_executed INTEGER     DEFAULT 0,
    dml_rows         INTEGER     DEFAULT 0,
    callouts         INTEGER     DEFAULT 0,
    error_message    TEXT,
    log_output       TEXT,
    executed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exec_status CHECK (status IN ('SUCCESS','FAILURE','TIMEOUT','GOVERNOR_LIMIT'))
);

CREATE INDEX idx_script_tenant ON script(tenant_id);
CREATE INDEX idx_script_trigger_script ON script_trigger(script_id);
CREATE INDEX idx_script_trigger_collection ON script_trigger(collection_id, trigger_event);
CREATE INDEX idx_script_exec_log ON script_execution_log(tenant_id, script_id, executed_at DESC);
```

### Entities

**Script** (`com.emf.controlplane.entity.Script`):
- Extends `BaseEntity`
- Fields: `tenantId`, `name`, `description`, `scriptType`, `language`, `sourceCode`, `active`, `version`, `createdBy`
- Relationships: `@OneToMany` to `ScriptTrigger` with cascade ALL, orphanRemoval

**ScriptTrigger** (`com.emf.controlplane.entity.ScriptTrigger`):
- Extends `BaseEntity`
- Fields: `triggerEvent`, `executionOrder`, `active`
- Relationships: `@ManyToOne` to `Script`, `@ManyToOne` to `Collection`

**ScriptExecutionLog** (`com.emf.controlplane.entity.ScriptExecutionLog`):
- Extends `BaseEntity`
- Fields: `tenantId`, `scriptId`, `status`, `triggerType`, `recordId`, `durationMs`, `cpuMs`, `queriesExecuted`, `dmlRows`, `callouts`, `errorMessage`, `logOutput`, `executedAt`

### Repositories

- `ScriptRepository`: `findByTenantIdOrderByNameAsc(tenantId)`, `findByTenantIdAndActiveTrue(tenantId)`
- `ScriptTriggerRepository`: `findByCollectionIdAndTriggerEventAndActiveTrueOrderByExecutionOrderAsc(collectionId, triggerEvent)`
- `ScriptExecutionLogRepository`: `findByTenantIdOrderByExecutedAtDesc(tenantId)`, `findByScriptIdOrderByExecutedAtDesc(scriptId)`

### DTOs

- `ScriptDto`: `fromEntity()` with nested `TriggerDto` list
- `CreateScriptRequest`: `name`, `description`, `scriptType`, `language?`, `sourceCode`, `active?`, `triggers[]`
- `ScriptExecutionLogDto`: `fromEntity()` for log display

### Service — `ScriptService`

- `listScripts(tenantId)` → List<Script>
- `getScript(id)` → Script
- `createScript(tenantId, CreateScriptRequest)` → Script (with @SetupAudited)
- `updateScript(id, CreateScriptRequest)` → Script (with @SetupAudited)
- `deleteScript(id)` (with @SetupAudited)
- `listExecutionLogs(tenantId)` → List<ScriptExecutionLog>
- `listExecutionLogsByScript(scriptId)` → List<ScriptExecutionLog>

### Controller — `ScriptController` at `/control/scripts`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/scripts?tenantId=` | List all scripts |
| GET | `/control/scripts/{id}` | Get script by ID |
| POST | `/control/scripts?tenantId=&userId=` | Create script |
| PUT | `/control/scripts/{id}` | Update script |
| DELETE | `/control/scripts/{id}` | Delete script |
| GET | `/control/scripts/logs?tenantId=` | List execution logs |
| GET | `/control/scripts/{id}/logs` | List logs for script |

---

## Stream B: Webhooks (5.2)

### Purpose
Push real-time event notifications to external systems when events occur in EMF. Supports event filtering, HMAC signing, and retry policies.

### Flyway Migration V31 — `V31__add_webhook_tables.sql`

```sql
CREATE TABLE webhook (
    id               VARCHAR(36)   PRIMARY KEY,
    tenant_id        VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name             VARCHAR(200)  NOT NULL,
    url              VARCHAR(2048) NOT NULL,
    events           JSONB         NOT NULL,
    collection_id    VARCHAR(36)   REFERENCES collection(id),
    filter_formula   TEXT,
    headers          JSONB         DEFAULT '{}',
    secret           VARCHAR(200),
    active           BOOLEAN       DEFAULT true,
    retry_policy     JSONB         DEFAULT '{"maxRetries": 3, "backoffSeconds": [10, 60, 300]}',
    created_by       VARCHAR(36)   NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook UNIQUE (tenant_id, name)
);

CREATE TABLE webhook_delivery (
    id               VARCHAR(36)   PRIMARY KEY,
    webhook_id       VARCHAR(36)   NOT NULL REFERENCES webhook(id) ON DELETE CASCADE,
    event_type       VARCHAR(50)   NOT NULL,
    payload          JSONB         NOT NULL,
    response_status  INTEGER,
    response_body    TEXT,
    attempt_count    INTEGER       DEFAULT 1,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    next_retry_at    TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED','RETRYING'))
);

CREATE INDEX idx_webhook_tenant ON webhook(tenant_id);
CREATE INDEX idx_webhook_collection ON webhook(collection_id);
CREATE INDEX idx_delivery_webhook ON webhook_delivery(webhook_id, created_at DESC);
CREATE INDEX idx_delivery_retry ON webhook_delivery(status, next_retry_at);
```

### Entities

**Webhook** (`com.emf.controlplane.entity.Webhook`):
- Extends `BaseEntity`
- Fields: `tenantId`, `name`, `url`, `events` (JSONB), `collectionId`, `filterFormula`, `headers` (JSONB), `secret`, `active`, `retryPolicy` (JSONB), `createdBy`
- Relationships: `@OneToMany` to `WebhookDelivery` with cascade ALL, orphanRemoval

**WebhookDelivery** (`com.emf.controlplane.entity.WebhookDelivery`):
- Extends `BaseEntity`
- Fields: `eventType`, `payload` (JSONB), `responseStatus`, `responseBody`, `attemptCount`, `status`, `nextRetryAt`, `deliveredAt`
- Relationships: `@ManyToOne` to `Webhook`

### Repositories

- `WebhookRepository`: `findByTenantIdOrderByNameAsc(tenantId)`, `findByTenantIdAndActiveTrueAndCollectionIdIsNullOrCollectionId(tenantId, collectionId)`
- `WebhookDeliveryRepository`: `findByWebhookIdOrderByCreatedAtDesc(webhookId)`, `findByStatusAndNextRetryAtBefore(status, timestamp)`

### DTOs

- `WebhookDto`: `fromEntity()` with nested `DeliveryDto` list (recent 10)
- `CreateWebhookRequest`: `name`, `url`, `events[]`, `collectionId?`, `filterFormula?`, `headers?`, `secret?`, `active?`, `retryPolicy?`
- `WebhookDeliveryDto`: `fromEntity()` for delivery history

### Service — `WebhookService`

- `listWebhooks(tenantId)` → List<Webhook>
- `getWebhook(id)` → Webhook
- `createWebhook(tenantId, CreateWebhookRequest)` → Webhook (with @SetupAudited)
- `updateWebhook(id, CreateWebhookRequest)` → Webhook (with @SetupAudited)
- `deleteWebhook(id)` (with @SetupAudited)
- `listDeliveries(webhookId)` → List<WebhookDelivery>
- `listDeliveriesByTenant(tenantId)` → List<WebhookDelivery>

### Controller — `WebhookController` at `/control/webhooks`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/webhooks?tenantId=` | List all webhooks |
| GET | `/control/webhooks/{id}` | Get webhook by ID |
| POST | `/control/webhooks?tenantId=&userId=` | Create webhook |
| PUT | `/control/webhooks/{id}` | Update webhook |
| DELETE | `/control/webhooks/{id}` | Delete webhook |
| GET | `/control/webhooks/{id}/deliveries` | List deliveries for webhook |
| GET | `/control/webhooks/deliveries?tenantId=` | List all deliveries |

---

## Stream C: Connected Apps (5.3)

### Purpose
Allow external applications to authenticate and access the EMF API with scoped permissions using OAuth2 client credentials.

### Flyway Migration V32 — `V32__add_connected_app_tables.sql`

```sql
CREATE TABLE connected_app (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    name                VARCHAR(200)  NOT NULL,
    description         VARCHAR(500),
    client_id           VARCHAR(100)  NOT NULL UNIQUE,
    client_secret_hash  VARCHAR(200)  NOT NULL,
    redirect_uris       JSONB         DEFAULT '[]',
    scopes              JSONB         DEFAULT '["api"]',
    ip_restrictions     JSONB         DEFAULT '[]',
    rate_limit_per_hour INTEGER       DEFAULT 10000,
    active              BOOLEAN       DEFAULT true,
    last_used_at        TIMESTAMP WITH TIME ZONE,
    created_by          VARCHAR(36)   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_connected_app UNIQUE (tenant_id, name)
);

CREATE TABLE connected_app_token (
    id                VARCHAR(36)   PRIMARY KEY,
    connected_app_id  VARCHAR(36)   NOT NULL REFERENCES connected_app(id) ON DELETE CASCADE,
    token_hash        VARCHAR(200)  NOT NULL,
    scopes            JSONB         NOT NULL,
    issued_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked           BOOLEAN       DEFAULT false,
    revoked_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_connected_app_tenant ON connected_app(tenant_id);
CREATE INDEX idx_connected_app_client ON connected_app(client_id);
CREATE INDEX idx_token_app ON connected_app_token(connected_app_id);
CREATE INDEX idx_token_hash ON connected_app_token(token_hash);
CREATE INDEX idx_token_expires ON connected_app_token(expires_at) WHERE revoked = false;
```

### Entities

**ConnectedApp** (`com.emf.controlplane.entity.ConnectedApp`):
- Extends `BaseEntity`
- Fields: `tenantId`, `name`, `description`, `clientId`, `clientSecretHash`, `redirectUris` (JSONB), `scopes` (JSONB), `ipRestrictions` (JSONB), `rateLimitPerHour`, `active`, `lastUsedAt`, `createdBy`

**ConnectedAppToken** (`com.emf.controlplane.entity.ConnectedAppToken`):
- Extends `BaseEntity`
- Fields: `tokenHash`, `scopes` (JSONB), `issuedAt`, `expiresAt`, `revoked`, `revokedAt`
- Relationships: `@ManyToOne` to `ConnectedApp`

### Repositories

- `ConnectedAppRepository`: `findByTenantIdOrderByNameAsc(tenantId)`, `findByClientId(clientId)`
- `ConnectedAppTokenRepository`: `findByConnectedAppIdAndRevokedFalseOrderByIssuedAtDesc(connectedAppId)`, `findByTokenHashAndRevokedFalse(tokenHash)`

### DTOs

- `ConnectedAppDto`: `fromEntity()` — NOTE: never expose `clientSecretHash`
- `CreateConnectedAppRequest`: `name`, `description?`, `redirectUris?`, `scopes?`, `ipRestrictions?`, `rateLimitPerHour?`, `active?`
- `ConnectedAppCreatedResponse`: includes generated `clientId` and plaintext `clientSecret` (shown only once)
- `ConnectedAppTokenDto`: `fromEntity()` — never expose `tokenHash`

### Service — `ConnectedAppService`

- `listApps(tenantId)` → List<ConnectedApp>
- `getApp(id)` → ConnectedApp
- `createApp(tenantId, CreateConnectedAppRequest)` → ConnectedAppCreatedResponse (generates clientId + clientSecret, stores hash) (with @SetupAudited)
- `updateApp(id, CreateConnectedAppRequest)` → ConnectedApp (with @SetupAudited)
- `deleteApp(id)` (with @SetupAudited)
- `rotateSecret(id)` → ConnectedAppCreatedResponse (generates new secret) (with @SetupAudited)
- `listTokens(connectedAppId)` → List<ConnectedAppToken>

### Controller — `ConnectedAppController` at `/control/connected-apps`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/connected-apps?tenantId=` | List all connected apps |
| GET | `/control/connected-apps/{id}` | Get app by ID |
| POST | `/control/connected-apps?tenantId=&userId=` | Create app (returns clientId + secret) |
| PUT | `/control/connected-apps/{id}` | Update app |
| DELETE | `/control/connected-apps/{id}` | Delete app |
| POST | `/control/connected-apps/{id}/rotate-secret` | Rotate client secret |
| GET | `/control/connected-apps/{id}/tokens` | List active tokens |

---

## Stream D: Bulk & Composite APIs (5.4)

### Purpose
Handle large-scale data operations efficiently. Bulk API processes records asynchronously in batches. Composite API executes multiple sub-requests in a single call.

### Flyway Migration V33 — `V33__add_bulk_job_tables.sql`

```sql
CREATE TABLE bulk_job (
    id                VARCHAR(36)   PRIMARY KEY,
    tenant_id         VARCHAR(36)   NOT NULL REFERENCES tenant(id),
    collection_id     VARCHAR(36)   NOT NULL REFERENCES collection(id),
    operation         VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    total_records     INTEGER       DEFAULT 0,
    processed_records INTEGER       DEFAULT 0,
    success_records   INTEGER       DEFAULT 0,
    error_records     INTEGER       DEFAULT 0,
    external_id_field VARCHAR(100),
    content_type      VARCHAR(50)   DEFAULT 'application/json',
    batch_size        INTEGER       DEFAULT 200,
    created_by        VARCHAR(36)   NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE,
    completed_at      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_bulk_operation CHECK (operation IN ('INSERT','UPDATE','UPSERT','DELETE')),
    CONSTRAINT chk_bulk_status CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED','ABORTED'))
);

CREATE TABLE bulk_job_result (
    id              VARCHAR(36)   PRIMARY KEY,
    bulk_job_id     VARCHAR(36)   NOT NULL REFERENCES bulk_job(id) ON DELETE CASCADE,
    record_index    INTEGER       NOT NULL,
    record_id       VARCHAR(36),
    status          VARCHAR(20)   NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_result_status CHECK (status IN ('SUCCESS','FAILURE'))
);

CREATE INDEX idx_bulk_job_tenant ON bulk_job(tenant_id, created_at DESC);
CREATE INDEX idx_bulk_job_status ON bulk_job(status);
CREATE INDEX idx_bulk_result_job ON bulk_job_result(bulk_job_id, record_index);
```

### Entities

**BulkJob** (`com.emf.controlplane.entity.BulkJob`):
- Extends `BaseEntity`
- Fields: `tenantId`, `collectionId`, `operation`, `status`, `totalRecords`, `processedRecords`, `successRecords`, `errorRecords`, `externalIdField`, `contentType`, `batchSize`, `createdBy`, `startedAt`, `completedAt`

**BulkJobResult** (`com.emf.controlplane.entity.BulkJobResult`):
- Extends `BaseEntity`
- Fields: `recordIndex`, `recordId`, `status`, `errorMessage`
- Relationships: `@ManyToOne` to `BulkJob`

### Repositories

- `BulkJobRepository`: `findByTenantIdOrderByCreatedAtDesc(tenantId)`, `findByStatus(status)`
- `BulkJobResultRepository`: `findByBulkJobIdOrderByRecordIndexAsc(bulkJobId)`, `findByBulkJobIdAndStatus(bulkJobId, status)`

### DTOs

- `BulkJobDto`: `fromEntity()` with progress fields
- `CreateBulkJobRequest`: `collectionId`, `operation`, `externalIdField?`, `batchSize?`, `records[]` (list of maps)
- `BulkJobResultDto`: `fromEntity()` per-record result
- `CompositeRequest`: `compositeRequest[]` each with `method`, `url`, `body?`, `referenceId`
- `CompositeResponse`: `compositeResponse[]` each with `referenceId`, `httpStatusCode`, `body`

### Service — `BulkJobService`

- `listJobs(tenantId)` → List<BulkJob>
- `getJob(id)` → BulkJob
- `createJob(tenantId, CreateBulkJobRequest)` → BulkJob (with @SetupAudited)
- `abortJob(id)` → BulkJob (with @SetupAudited)
- `getResults(jobId)` → List<BulkJobResult>
- `getErrorResults(jobId)` → List<BulkJobResult>

### Controller — `BulkJobController` at `/control/bulk-jobs`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/bulk-jobs?tenantId=` | List all bulk jobs |
| GET | `/control/bulk-jobs/{id}` | Get job status |
| POST | `/control/bulk-jobs?tenantId=&userId=` | Create & queue bulk job |
| POST | `/control/bulk-jobs/{id}/abort` | Abort running job |
| GET | `/control/bulk-jobs/{id}/results` | Get job results |
| GET | `/control/bulk-jobs/{id}/errors` | Get error results only |

### Controller — `CompositeApiController` at `/control/composite`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/control/composite?tenantId=` | Execute composite request |

---

## SDK Types (TypeScript)

All types go in `emf-web/packages/sdk/src/admin/types.ts`:

### Stream A Types
- `Script`, `ScriptTrigger`, `ScriptExecutionLog`
- `CreateScriptRequest`, `CreateScriptTriggerRequest`

### Stream B Types
- `Webhook`, `WebhookDelivery`
- `CreateWebhookRequest`

### Stream C Types
- `ConnectedApp`, `ConnectedAppToken`, `ConnectedAppCreatedResponse`
- `CreateConnectedAppRequest`

### Stream D Types
- `BulkJob`, `BulkJobResult`
- `CreateBulkJobRequest`, `CompositeRequest`, `CompositeSubRequest`, `CompositeResponse`, `CompositeSubResponse`

## SDK AdminClient Operations

All operations go in `emf-web/packages/sdk/src/admin/AdminClient.ts`:

- `scripts.list/get/create/update/delete/listLogs`
- `webhooks.list/get/create/update/delete/listDeliveries`
- `connectedApps.list/get/create/update/delete/rotateSecret/listTokens`
- `bulkJobs.list/get/create/abort/getResults/getErrors`
- `composite.execute`

## UI Pages

| Page | Route | Description |
|------|-------|-------------|
| ScriptsPage | `/scripts` | Manage server-side scripts and triggers |
| WebhooksPage | `/webhooks` | Manage webhooks and view delivery history |
| ConnectedAppsPage | `/connected-apps` | Manage connected apps and credentials |
| BulkJobsPage | `/bulk-jobs` | View and manage bulk job operations |

Each page follows the DashboardsPage pattern with full CRUD, modals, validation, and toast notifications.
