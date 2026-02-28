# EPIC: Workflow Engine — Visual State Machine with Runtime Modules

> A phased plan to evolve EMF's automation system into a visual, state-machine-driven workflow engine with durable execution, runtime-loadable modules, and rich observability.

---

## Current State

### What Exists Today

**Workflow Rules** (fully implemented):
- `WorkflowEngine` evaluates trigger-based rules against `RecordChangeEvent`
- 15+ `ActionHandler` implementations across 3 modules (core, integration, schema)
- 8 trigger types, retry logic, scheduled execution, before-save hooks
- DB tables: `workflow_rule`, `workflow_action`, `workflow_execution_log`, `workflow_action_log`, `workflow_action_type`, `workflow_pending_action`, `workflow_rule_version`
- UI: `WorkflowRulesPage.tsx` (form-based CRUD), `WorkflowActionTypesPage.tsx`

**Flows** (partially implemented):
- DB tables: `flow` (JSONB `definition`, `trigger_config`), `flow_execution` (JSONB `variables`, `current_node_id`)
- System collections: `flows`, `flow-executions`
- UI: `FlowsPage.tsx` with CRUD, execute, cancel, execution history
- **No backend execution engine exists**

**Module System**:
- `EmfModule` interface with `getId()`, `getName()`, `getVersion()`, `getActionHandlers()`, `getBeforeSaveHooks()`, `onStartup(ModuleContext)`
- `ModuleRegistry` discovers beans via Spring classpath scanning
- `ModuleContext` provides runtime services (QueryEngine, CollectionRegistry, FormulaEvaluator, ActionHandlerRegistry, extensions map)
- 3 compile-time modules: SchemaLifecycleModule, CoreActionsModule, IntegrationModule

### Key Decision: Flows Replace Workflow Rules

The existing Workflow Rules system will be **fully migrated to Flows** and then removed. A migration tool (Phase 3) will convert each `workflow_rule` + its `workflow_action` list into a Flow definition. After migration, the `WorkflowEngine`, `WorkflowStore`, `JdbcWorkflowStore`, and all `workflow_*` tables become dead code and are removed.

---

## Target Architecture

```
                  ┌──────────────────────────────────┐
                  │         Visual Builder UI         │
                  │       (React Flow canvas)         │
                  │  Drag-and-drop state composition  │
                  └──────────────┬───────────────────┘
                                 │ Flow Definition JSON
                                 ▼
                  ┌──────────────────────────────────┐
                  │          Flow REST API            │
                  │   CRUD, validate, execute, test   │
                  └──────────────┬───────────────────┘
                                 │
                                 ▼
┌─────────────┐   ┌──────────────────────────────────┐   ┌────────────────┐
│   Triggers   │──▶│     State Machine Engine          │──▶│  Execution DB  │
│ Kafka events │   │  ┌──────┐ ┌──────┐ ┌──────────┐ │   │  flow_execution│
│ Record CRUD  │   │  │ Task │ │Choice│ │ Parallel │ │   │  flow_step_log │
│ Scheduled    │   │  └──┬───┘ └──┬───┘ └────┬─────┘ │   │  flow_variables│
│ Manual / API │   │     │        │           │       │   └────────────────┘
└─────────────┘   │     ▼        ▼           ▼       │
                  │  ActionHandler Registry            │   ┌────────────────┐
                  │  ┌───────────────────────────┐    │──▶│   Metrics       │
                  │  │ Built-in  │ Runtime Modules│    │   │  Prometheus     │
                  │  │ handlers  │ (JAR-loaded)   │    │   └────────────────┘
                  │  └───────────────────────────┘    │
                  └──────────────────────────────────┘
```

### Core Principles

1. **Durable Execution** — Every state transition persists to PostgreSQL. Executions survive pod restarts and can resume from any checkpoint.
2. **State Data Flow** — Each step receives input state, produces output state. JSONPath expressions map data between steps (InputPath, OutputPath, ResultPath).
3. **Unified Handler SPI** — The existing `ActionHandler` interface remains unchanged. All 15+ existing handlers become available as Task state resources.
4. **Tenant-Scoped Runtime Modules** — JARs downloaded by URL, stored in S3, loaded via sandboxed ClassLoader, scoped to a tenant. Module lifecycle events (install, enable, disable, uninstall) propagate to all pods via Kafka (`emf.config.module.changed`) — no process restarts required. Modules expose new `ActionHandler` implementations.
5. **Visual-First Design** — Every flow is visually composable. The JSON definition is the source of truth; the visual builder is a bidirectional editor of that JSON.

---

## State Machine Model

### State Types

Inspired by AWS Step Functions, with additions for EMF's data platform context.

| State Type | Purpose | Key Properties |
|------------|---------|---------------|
| **Task** | Execute an ActionHandler | `resource` (handler key), `inputPath`, `outputPath`, `resultPath`, `retry`, `catch`, `timeoutSeconds` |
| **Choice** | Conditional branching | `choices[]` (rules with comparison operators), `default` (fallback state) |
| **Parallel** | Run branches concurrently | `branches[]` (each is a sub-state-machine), `resultPath` |
| **Map** | Iterate over array, run sub-flow per item | `itemsPath`, `iterator` (sub-state-machine), `maxConcurrency`, `resultPath` |
| **Wait** | Pause execution | `seconds`, `timestamp`, `timestampPath`, `eventName` (resume on external event) |
| **Pass** | Transform/inject data | `result` (literal value), `inputPath`, `outputPath`, `resultPath` |
| **Succeed** | Terminal success | *(no additional properties)* |
| **Fail** | Terminal failure | `error` (error code), `cause` (message) |

### State Data Flow

Every execution carries a **state object** (JSON). As the engine transitions between states:

1. **InputPath** — Selects a subset of the state as input to the current state (JSONPath, default `$` = entire state)
2. The state executes and produces a **result**
3. **ResultPath** — Places the result into the state at the given path (default `$` = replace entire state)
4. **OutputPath** — Selects a subset of the state to pass to the next state (default `$` = entire state)

Example:
```json
{
  "Type": "Task",
  "Resource": "HTTP_CALLOUT",
  "InputPath": "$.orderData",
  "ResultPath": "$.apiResponse",
  "OutputPath": "$",
  "Next": "CheckStatus"
}
```

### Error Handling

Each Task state can define:
- **Retry** — Retry on specific errors with interval, backoff rate, and max attempts
- **Catch** — On error, transition to a specific fallback state with the error in `$.error`

```json
{
  "Retry": [
    { "ErrorEquals": ["HttpTimeout", "Http5xx"], "IntervalSeconds": 5, "MaxAttempts": 3, "BackoffRate": 2.0 }
  ],
  "Catch": [
    { "ErrorEquals": ["States.ALL"], "Next": "HandleError", "ResultPath": "$.error" }
  ]
}
```

### Flow Definition JSON Structure

Top-level: `{ "Comment": "...", "StartAt": "<stateId>", "States": { ... }, "_metadata": { "nodePositions": {...} } }`

Each state has `"Type"` and either `"Next": "<stateId>"` or `"End": true`. Task states include `Resource`, data flow paths, and optional `Retry`/`Catch`. Choice states include `Choices[]` with comparison operators and `Default`. Parallel/Map states contain nested sub-state-machines in `Branches[]` or `Iterator`. The `_metadata.nodePositions` object stores canvas coordinates (not part of execution).

---

## Flow Triggers, Filters & Initial State Mapping

This section defines how flows are invoked, how source data is filtered to determine if the flow should run, and how source data is mapped into the flow's initial state.

### Trigger Types

Each flow has a `flow_type` and a `trigger_config` (JSONB) that together define when and how the flow is invoked. The `trigger_config` is set when the flow is created/edited and is evaluated by the appropriate listener.

#### 1. Record-Triggered Flow (`RECORD_TRIGGERED`)

Invoked when a record in a specific collection is created, updated, or deleted.

**trigger_config schema:**
```json
{
  "collection": "orders",
  "events": ["CREATED", "UPDATED"],
  "triggerFields": ["status", "amount"],
  "filterFormula": "status == 'ACTIVE' AND amount > 100",
  "synchronous": false
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `collection` | string | Yes | The collection name to monitor (e.g., `"orders"`, `"contacts"`) |
| `events` | string[] | Yes | Which change types trigger the flow: `"CREATED"`, `"UPDATED"`, `"DELETED"`. One or more. |
| `triggerFields` | string[] | No | For UPDATE events only: flow only fires if at least one of these fields changed. If omitted, any field change triggers the flow. |
| `filterFormula` | string | No | A formula expression evaluated against the current record data. If it evaluates to `false`, the flow does not start. Uses the existing `FormulaEvaluator`. Supports field references, operators (`==`, `!=`, `>`, `<`, `>=`, `<=`), boolean logic (`AND`, `OR`, `NOT`), and functions. |
| `synchronous` | boolean | No | Default `false`. If `true`, the flow executes synchronously during the save operation (before commit) and can return field updates. Used to replace `BEFORE_CREATE`/`BEFORE_UPDATE` workflow triggers. |

**How it works:**
1. `FlowEventListener` consumes `emf.record.changed` Kafka topic
2. For each event, queries all ACTIVE flows for the tenant where `flow_type = 'RECORD_TRIGGERED'` and `trigger_config.collection` matches the event's collection
3. Checks if the event's `changeType` is in `trigger_config.events`
4. If `triggerFields` is specified and event is UPDATE, checks intersection with `changedFields`
5. If `filterFormula` is specified, evaluates it against the record's current data
6. If all checks pass, starts a flow execution

**Initial state:** Source key = `record` containing `id`, `collectionName`, `data` (full record), `previousData` (changed fields only), `changedFields[]`. Steps reference: `$.record.data.status`, `$.record.previousData.status`, `$.record.id`, `$.context.tenantId`.

#### 2. Kafka Message Flow (`KAFKA_TRIGGERED`)

Invoked when a message arrives on a specific Kafka topic matching filter criteria.

**trigger_config schema:**
```json
{
  "topic": "external.payments.completed",
  "consumerGroup": "emf-flow-payments",
  "keyFilter": "tenant-123:*",
  "messageFilter": {
    "path": "$.payload.type",
    "operator": "equals",
    "value": "payment.success"
  },
  "payloadFormat": "JSON"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `topic` | string | Yes | Kafka topic to subscribe to |
| `consumerGroup` | string | No | Custom consumer group. Default: `emf-flow-{flowId}` |
| `keyFilter` | string | No | Glob pattern matched against the Kafka message key. If the key doesn't match, the message is skipped. |
| `messageFilter` | object | No | JSONPath-based filter on the message body. Only messages where the path matches the operator/value trigger the flow. |
| `messageFilter.path` | string | Yes (if messageFilter) | JSONPath into the message body |
| `messageFilter.operator` | string | Yes (if messageFilter) | One of: `equals`, `notEquals`, `contains`, `startsWith`, `exists`, `greaterThan`, `lessThan` |
| `messageFilter.value` | any | Yes (if messageFilter, except `exists`) | Value to compare against |
| `payloadFormat` | string | No | Default `"JSON"`. How to parse the message body: `"JSON"`, `"STRING"`, `"AVRO"` |

**How it works:**
1. `KafkaFlowTriggerManager` dynamically creates Kafka consumers for each active flow with `KAFKA_TRIGGERED` type
2. When a message arrives, applies key filter (glob match) and message filter (JSONPath + operator)
3. If filters pass, deserializes message body and starts a flow execution

**Initial state:** Source key = `message` containing `key`, `headers` (map), `payload` (parsed body). Steps reference: `$.message.payload.amount`, `$.message.key`, `$.message.headers.correlationId`.

#### 3. Scheduled Flow (`SCHEDULED`)

Invoked on a cron schedule.

**trigger_config schema:**
```json
{
  "cronExpression": "0 0 8 * * MON-FRI",
  "timezone": "America/Chicago",
  "inputData": {
    "reportType": "daily-summary",
    "recipients": ["admin@company.com"]
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `cronExpression` | string | Yes | Standard cron expression (6-field: sec min hour day month weekday) |
| `timezone` | string | No | IANA timezone. Default: `UTC` |
| `inputData` | object | No | Static JSON data to inject into the flow's initial state. Useful for parameterizing scheduled flows. |

**How it works:**
1. `ScheduledFlowExecutor` polls the `flow` table periodically (every 60s, configurable)
2. Finds all ACTIVE flows with `flow_type = 'SCHEDULED'`
3. Evaluates cron expression against current time + timezone
4. Uses optimistic locking (`last_scheduled_run` column) to prevent duplicate execution in multi-pod deployments
5. Starts a flow execution

**Initial state:** Source key = `input` containing static data from `trigger_config.inputData`. Steps reference: `$.input.reportType`, `$.input.recipients`.

#### 4. API-Invoked Flow (`AUTOLAUNCHED`)

Invoked via explicit API call. Used for webhook endpoints, manual triggers from the UI, or invocation from other flows.

**trigger_config schema:**
```json
{
  "inputSchema": {
    "type": "object",
    "required": ["orderId"],
    "properties": {
      "orderId": { "type": "string" },
      "priority": { "type": "string", "enum": ["LOW", "MEDIUM", "HIGH"], "default": "MEDIUM" },
      "options": { "type": "object" }
    }
  },
  "authentication": "TENANT_API_KEY",
  "webhookPath": "/hooks/process-order"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `inputSchema` | object | No | JSON Schema that validates the input payload provided at execution time. If specified, the API rejects payloads that don't conform. |
| `authentication` | string | No | Auth required to invoke: `"TENANT_API_KEY"` (default), `"WEBHOOK_SECRET"`, `"NONE"` (for internal-only flows) |
| `webhookPath` | string | No | If specified, creates a dedicated webhook URL: `POST /api/webhooks{webhookPath}` that triggers this flow. Enables external systems to invoke the flow without knowing the flow ID. |

**How it works:**
1. `POST /api/flows/{flowId}/execute` with request body `{ "input": { "orderId": "ord-123", "priority": "HIGH" } }`
2. Or, if `webhookPath` is set: `POST /api/webhooks/process-order` with the input as the request body
3. Input validated against `inputSchema` (if defined)
4. Flow execution starts with the provided input as state

**Initial state:** Source key = `input` containing caller-provided data from API request body. Steps reference: `$.input.orderId`, `$.input.priority`.

#### 5. Screen Flow (`SCREEN`)

Invoked from the UI as an interactive, guided process. **Not covered in initial phases** — placeholder for future implementation. Would support user-facing forms, approval steps, and interactive branching.

### Trigger Filter Summary

All trigger types support filtering to determine whether the flow should actually start:

| Trigger Type | Filter Mechanism | Evaluated Against |
|-------------|-----------------|-------------------|
| `RECORD_TRIGGERED` | `events[]` + `triggerFields[]` + `filterFormula` | RecordChangeEvent data |
| `KAFKA_TRIGGERED` | `keyFilter` (glob) + `messageFilter` (JSONPath + operator) | Kafka message key + body |
| `SCHEDULED` | Cron expression + timezone | Current timestamp |
| `AUTOLAUNCHED` | `inputSchema` (JSON Schema validation) | API request body |

### Initial State Structure (Canonical)

Every flow execution starts with a state object that follows a common envelope:

```json
{
  "trigger": {
    "type": "<RECORD_CHANGE|KAFKA_MESSAGE|SCHEDULED|API_INVOCATION>",
    "...trigger-specific metadata..."
  },
  "<source-specific-key>": {
    "...source data..."
  },
  "context": {
    "tenantId": "...",
    "userId": "...",
    "flowId": "...",
    "executionId": "..."
  }
}
```

| Trigger Type | Source Key | Contains |
|-------------|-----------|----------|
| `RECORD_TRIGGERED` | `record` | `id`, `collectionName`, `data` (full record), `previousData`, `changedFields` |
| `KAFKA_TRIGGERED` | `message` | `key`, `headers`, `payload` (parsed message body) |
| `SCHEDULED` | `input` | Static data from `trigger_config.inputData` |
| `AUTOLAUNCHED` | `input` | Caller-provided input from API request body |

This means the first step of every flow can use `InputPath` to extract exactly what it needs. For example:
- A record-triggered flow that only needs the record data: `"InputPath": "$.record.data"`
- A Kafka-triggered flow that needs the whole message: `"InputPath": "$.message.payload"`
- A scheduled flow: `"InputPath": "$.input"`

### Custom State Mapping (Optional InputMapping)

The trigger_config can include an `inputMapping` object that transforms source data before setting the initial state. Each key is the target state path, each value is a JSONPath expression resolved against the canonical envelope. Example: `{ "orderId": "$.record.id", "customerName": "$.record.data.customerName" }` flattens nested trigger data into clean top-level keys. If omitted, the full canonical envelope is used.

This flattens the source data into a clean top-level structure, making downstream step InputPath expressions simpler (e.g., `$.orderId` instead of `$.record.data.id`). The `inputMapping` is optional — if omitted, the full canonical envelope is used.

---

## Phase 1 — State Machine Execution Engine

**Goal**: Build the core state machine engine that interprets Flow Definition JSON and executes states with durable persistence. No UI changes — flows are created via the existing `FlowsPage.tsx` form (entering JSON manually) or the REST API.

### 1.1 Flow Definition Model (runtime-core)

New package: `com.emf.runtime.flow`

**Classes to create:**
- `FlowDefinition` — Parsed representation of the definition JSON (record with `startAt`, `comment`, `Map<String, StateDefinition> states`)
- `StateDefinition` — Sealed interface with implementations for each state type:
  - `TaskState`, `ChoiceState`, `ParallelState`, `MapState`, `WaitState`, `PassState`, `SucceedState`, `FailState`
- `ChoiceRule` — Comparison operators: `StringEquals`, `NumericGreaterThan`, `BooleanEquals`, `IsPresent`, `And`, `Or`, `Not`, etc.
- `RetryPolicy` — `errorEquals`, `intervalSeconds`, `maxAttempts`, `backoffRate`
- `CatchPolicy` — `errorEquals`, `resultPath`, `next`
- `FlowDefinitionParser` — Parses JSONB definition into `FlowDefinition` using Jackson
- `StateDataResolver` — Implements InputPath/OutputPath/ResultPath transforms using a JSONPath library (e.g., Jayway JsonPath, already available in Spring ecosystem)

**Key file to reuse:** `ActionHandler.java` — Task states dispatch to registered handlers via `ActionHandlerRegistry.getHandler(resource)`

### 1.2 State Machine Engine (runtime-core)

**Classes to create:**
- `FlowEngine` — Core execution engine (async by default):
  - `startExecution(tenantId, flowId, definition, initialInput, userId)` → creates `flow_execution` row, dispatches to a managed `ExecutorService` thread pool, returns `executionId` immediately. Kafka listener threads are never blocked.
  - `executeSynchronous(tenantId, flowId, definition, initialInput, userId)` → blocks until completion, returns final state data. Used for `synchronous: true` record-triggered flows (replaces BEFORE_CREATE/BEFORE_UPDATE triggers) where the caller needs the result before commit.
  - `resumeExecution(executionId)` → loads persisted state, resumes from `current_node_id` (async)
  - `cancelExecution(executionId)` → marks execution as CANCELLED
  - Thread pool: configurable `emf.flow.executor.pool-size` (default: 10), bounded queue with rejection policy that logs and marks execution as FAILED
  - **Flow-level execution timeout:** Each flow has optional `maxExecutionDurationSeconds` (default: 3600s). FlowEngine monitors execution age and cancels with status FAILED + error code `FlowTimeout` if exceeded. Prevents infinite loops.
  - **Graceful shutdown:** On SIGTERM, FlowEngine stops accepting new executions, waits for in-flight executions to reach a persist point (next state transition), persists them as WAITING with `resume_reason = 'POD_SHUTDOWN'`. `ScheduledFlowExecutor` picks them up on another pod.
  - Internal state dispatch loop: reads current state type → delegates to typed handler → persists transition → moves to next state
- `FlowExecutionContext` — Mutable execution context holding state data, execution ID, tenant, variables, step history
- `StateExecutor` — Interface with implementations per state type:
  - `TaskStateExecutor` — Resolves ActionHandler from registry, applies InputPath, executes, applies ResultPath/OutputPath, handles Retry/Catch
  - `ChoiceStateExecutor` — Evaluates choice rules against state data, selects next state
  - `ParallelStateExecutor` — Forks state into branches, executes via thread pool, merges results
  - `MapStateExecutor` — Extracts items array, executes sub-flow per item (with concurrency limit), collects results
  - `WaitStateExecutor` — Persists execution as WAITING, schedules resume via `flow_pending_resume` table
  - `PassStateExecutor` — Applies result/transforms, moves to next state
  - `SucceedStateExecutor` / `FailStateExecutor` — Terminal states, update execution status

**Key file to reuse:** `ActionHandlerRegistry.java` — Task states look up handlers by key

### 1.3 Flow Store (runtime-core + emf-worker)

**Interface:** `FlowStore` (in runtime-core)
- `createExecution(tenantId, flowId, userId, initialState)` → String executionId
- `loadExecution(executionId)` → FlowExecutionData
- `updateExecutionState(executionId, currentNodeId, stateData, status)`
- `logStepExecution(executionId, stateId, stateName, stateType, input, output, status, durationMs, error, attemptNumber)`
- `findWaitingExecutions()` → for scheduled resume
- `cancelExecution(executionId)`

**Implementation:** `JdbcFlowStore` (in emf-worker) — JDBC implementation against flow tables

### 1.4 Database Schema Changes

New migration: `V71__flow_engine_enhancements.sql`

```sql
-- Expand flow_type to include KAFKA_TRIGGERED
ALTER TABLE flow DROP CONSTRAINT chk_flow_type;
ALTER TABLE flow ADD CONSTRAINT chk_flow_type CHECK (flow_type IN (
    'RECORD_TRIGGERED', 'KAFKA_TRIGGERED', 'SCHEDULED', 'AUTOLAUNCHED', 'SCREEN'
));

-- Add scheduling columns for SCHEDULED flows
ALTER TABLE flow ADD COLUMN last_scheduled_run TIMESTAMP WITH TIME ZONE;

-- Add columns to flow_execution for state data persistence
ALTER TABLE flow_execution ADD COLUMN state_data JSONB DEFAULT '{}';
ALTER TABLE flow_execution ADD COLUMN step_count INTEGER DEFAULT 0;
ALTER TABLE flow_execution ADD COLUMN duration_ms INTEGER;
ALTER TABLE flow_execution ADD COLUMN initial_input JSONB;
ALTER TABLE flow_execution ADD COLUMN is_test BOOLEAN DEFAULT false;

-- Step-level execution log (replaces workflow_action_log for flows)
CREATE TABLE flow_step_log (
    id                VARCHAR(36) PRIMARY KEY,
    execution_id      VARCHAR(36) NOT NULL REFERENCES flow_execution(id) ON DELETE CASCADE,
    state_id          VARCHAR(100) NOT NULL,
    state_name        VARCHAR(200),
    state_type        VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    input_snapshot    JSONB,
    output_snapshot   JSONB,
    error_message     TEXT,
    error_code        VARCHAR(100),
    attempt_number    INTEGER DEFAULT 1,
    duration_ms       INTEGER,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_step_log_exec ON flow_step_log(execution_id);
CREATE INDEX idx_flow_step_log_state ON flow_step_log(execution_id, state_id);

-- Pending resume table for Wait states and scheduled resumptions
CREATE TABLE flow_pending_resume (
    id                VARCHAR(36) PRIMARY KEY,
    execution_id      VARCHAR(36) NOT NULL REFERENCES flow_execution(id) ON DELETE CASCADE,
    tenant_id         VARCHAR(36) NOT NULL,
    resume_at         TIMESTAMP WITH TIME ZONE,
    resume_event      VARCHAR(200),
    claimed_by        VARCHAR(100),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_pending_resume_time ON flow_pending_resume(resume_at) WHERE claimed_by IS NULL;
CREATE INDEX idx_flow_pending_resume_event ON flow_pending_resume(resume_event) WHERE claimed_by IS NULL;
```

### 1.5 Trigger Evaluation Engine (runtime-core)

**New classes:**
- `FlowTriggerEvaluator` — Evaluates whether a trigger event matches a flow's `trigger_config`:
  - `matchesRecordTrigger(RecordChangeEvent, triggerConfig)` → checks events[], triggerFields[], filterFormula
  - `matchesKafkaTrigger(kafkaKey, kafkaPayload, triggerConfig)` → checks keyFilter, messageFilter
  - Uses the existing `FormulaEvaluator` for filterFormula evaluation
- `InitialStateBuilder` — Constructs the canonical initial state envelope from trigger data:
  - `buildFromRecordEvent(RecordChangeEvent, triggerConfig, flowId, executionId)` → builds `{trigger, record, context}` object
  - `buildFromKafkaMessage(key, headers, payload, triggerConfig, flowId, executionId)` → builds `{trigger, message, context}` object
  - `buildFromSchedule(triggerConfig, flowId, executionId)` → builds `{trigger, input, context}` object
  - `buildFromApiInvocation(inputPayload, userId, flowId, executionId)` → builds `{trigger, input, context}` object
  - Applies optional `inputMapping` from trigger_config to flatten/transform the initial state
- `InputMappingResolver` — Resolves `inputMapping` expressions against the canonical state to produce a custom initial state

### 1.6 Worker Integration

**Modify:** `WorkflowConfig.java` → Add `FlowEngine`, `FlowStore`, `FlowTriggerEvaluator`, `InitialStateBuilder` beans alongside existing workflow beans (coexistence during migration)

**Modify:** Gateway route configuration → Add routes for `/api/flows/{flowId}/execute`, `/api/flows/executions/**`, `/api/webhooks/**` proxying to the worker service (following existing gateway → worker routing pattern)

**New classes in emf-worker:**
- `FlowExecutionController` — REST endpoints:
  - `POST /api/flows/{flowId}/execute` — Start execution with optional `{ "input": {...} }` body for AUTOLAUNCHED flows
  - `POST /api/flows/executions/{executionId}/cancel` — Cancel execution
  - `POST /api/flows/executions/{executionId}/resume` — Resume waiting execution (for external events)
  - `GET /api/flows/{flowId}/flow-executions` — List executions (existing)
  - `GET /api/flows/executions/{executionId}` — Get execution detail with state data
  - `GET /api/flows/executions/{executionId}/steps` — Get step-level log
  - `POST /api/webhooks/{path}` — Webhook endpoint that looks up flow by `webhookPath` in trigger_config. Supports HMAC signature verification via `X-EMF-Signature` header when `authentication = 'WEBHOOK_SECRET'` (secret stored per-flow, compared using constant-time comparison).
- `FlowEventListener` — Kafka listener for `emf.record.changed`:
  - Maintains an in-memory cache of active flow trigger configs per tenant (`ConcurrentHashMap<String, List<FlowTriggerConfig>>`), loaded from DB on startup and invalidated when flows are created/updated/deleted (via the existing system collection save hooks)
  - For each event, checks cached trigger configs — no DB query per event
  - Uses `FlowTriggerEvaluator.matchesRecordTrigger()` to filter
  - Uses `InitialStateBuilder.buildFromRecordEvent()` to construct initial state
  - Calls `FlowEngine.startExecution()` for each matching flow
- `KafkaFlowTriggerManager` — Manages dynamic Kafka consumers for `KAFKA_TRIGGERED` flows:
  - **On pod startup:** queries DB for all ACTIVE flows with `flow_type = 'KAFKA_TRIGGERED'`, creates a consumer for each
  - **Consumer group strategy:** each flow uses consumer group `emf-flow-{flowId}`. All pods join the same group, so Kafka distributes partitions — each message is processed by exactly one pod. No cross-pod activation events needed.
  - On flow activation (via API on this pod), creates a consumer for the configured topic
  - On message receipt, uses `FlowTriggerEvaluator.matchesKafkaTrigger()` to filter
  - Uses `InitialStateBuilder.buildFromKafkaMessage()` for initial state
  - On flow deactivation, closes the consumer on this pod. Other pods will close on next restart or when consumer group rebalances (the inactive flow's consumer will stop receiving messages since no new messages are acknowledged).
  - Tracks active consumers in `ConcurrentHashMap<String, KafkaConsumer>` (flowId → consumer)
- `ScheduledFlowExecutor` — Two responsibilities:
  - Polls `flow` table for SCHEDULED flows due to run (cron evaluation, optimistic locking)
  - Polls `flow_pending_resume` table for Wait states ready to resume

### 1.7 Built-in Task Resources

Map existing ActionHandlers to Task `resource` keys. The following are available immediately:

| Resource Key | Handler | Description |
|-------------|---------|-------------|
| `FIELD_UPDATE` | FieldUpdateActionHandler | Update fields on a record |
| `CREATE_RECORD` | CreateRecordActionHandler | Create a new record |
| `UPDATE_RECORD` | UpdateRecordActionHandler | Update any record |
| `DELETE_RECORD` | DeleteRecordActionHandler | Delete a record |
| `CREATE_TASK` | CreateTaskActionHandler | Create a task record |
| `HTTP_CALLOUT` | HttpCalloutActionHandler | Make HTTP request |
| `EMAIL_ALERT` | EmailAlertActionHandler | Send email |
| `SEND_NOTIFICATION` | SendNotificationActionHandler | Send in-app notification |
| `OUTBOUND_MESSAGE` | OutboundMessageActionHandler | Send webhook |
| `PUBLISH_EVENT` | PublishEventActionHandler | Publish Kafka event |
| `INVOKE_SCRIPT` | InvokeScriptActionHandler | Execute server-side script |
| `LOG_MESSAGE` | LogMessageActionHandler | Write to execution log |

New built-in resources to add:
| Resource Key | Description |
|-------------|-------------|
| `QUERY_RECORDS` | Query a collection with filters, return results as state data |
| `TRANSFORM_DATA` | Apply a JSONPath/template transformation to state data |
| `EMIT_KAFKA_EVENT` | Publish a custom event to a specified Kafka topic |

### 1.8 Action Handler Descriptors (UI Integration Contract)

Every action handler — whether built-in or runtime-loaded — can provide a **descriptor** that tells the UI how to render its configuration form, what inputs it expects, and what outputs it produces. This is the contract that makes runtime modules integrate as seamlessly as native capabilities.

**New interface:** `ActionHandlerDescriptor` (in runtime-core, alongside `ActionHandler`)

```java
public interface ActionHandlerDescriptor {
    /** JSON Schema defining the handler's configuration fields.
     *  The UI renders a form from this schema instead of showing raw JSON. */
    String getConfigSchema();

    /** JSON Schema defining the input this handler expects.
     *  Used by the UI for InputPath autocomplete suggestions. */
    String getInputSchema();

    /** JSON Schema defining the output this handler produces.
     *  Used by downstream steps for ResultPath/InputPath autocomplete.
     *  Used by Choice rules for variable path suggestions. */
    String getOutputSchema();

    /** Human-readable display name (e.g., "Create Charge") */
    String getDisplayName();

    /** Category for grouping in the resource selector (e.g., "Payment", "Data", "Communication") */
    String getCategory();

    /** Short description shown as tooltip in the resource selector */
    String getDescription();

    /** Icon identifier (optional, maps to lucide-react icon name) */
    default String getIcon() { return null; }
}
```

**How ActionHandler connects to its descriptor:**
Extend `ActionHandler` with a default method:
```java
public interface ActionHandler {
    // ... existing methods ...

    /** Returns the UI descriptor for this handler.
     *  Override to provide rich UI integration. */
    default ActionHandlerDescriptor getDescriptor() { return null; }
}
```

Handlers that return `null` get a generic form (raw JSON config editor). Handlers that return a descriptor get a rich, schema-driven form.

**Schemas are standard JSON Schema objects** with `type`, `properties`, `required`, `title`, `description`, `enum`, `default`, `format`, `minimum`, `maximum`. String fields support `${$.path}` template expressions resolved at execution time against state data (e.g., `"url": "https://api.example.com/${$.orderId}"`). The engine resolves these templates using `StateDataResolver` before passing config to the handler.

**REST API:** `GET /api/flows/resources` → Returns all available resources (built-in + runtime modules) with their descriptors including `key`, `displayName`, `category`, `description`, `icon`, `configSchema`, `inputSchema`, `outputSchema`, and `source` ("builtin" or "module:{moduleId}").

**Built-in handlers:** All 15+ existing handlers will be updated with descriptors.

### 1.9 Enterprise Guardrails

**RBAC:** Flow operations require permissions integrated with the existing EMF permission framework:
- `MANAGE_FLOWS` — create, edit, activate, deactivate, delete flows
- `EXECUTE_FLOWS` — manually trigger flow executions
- `VIEW_FLOW_EXECUTIONS` — view execution history and step logs
- Permissions checked in `FlowExecutionController` and flow CRUD endpoints

**Per-Tenant Rate Limiting:** Configurable max concurrent executions per tenant (`emf.flow.max-concurrent-per-tenant`, default: 50). `FlowEngine` tracks active execution count per tenant in an `AtomicInteger` map. Exceeding the limit → execution rejected with HTTP 429 / error code `TenantRateLimitExceeded`. Queue overflow → execution marked FAILED.

**Idempotency:** `FlowEventListener` deduplicates trigger events using a `(eventId, flowId)` composite key. Uses Redis SET with TTL (default: 5 minutes) for deduplication window. If Redis unavailable, falls back to a DB `flow_execution_dedup` table with periodic TTL cleanup. Prevents duplicate executions from Kafka at-least-once delivery.

**Emergency Pause (Kill Switch):** `POST /api/admin/flows/pause-all` — immediately stops all flow execution for a tenant. Sets a `flow_processing_paused` flag in the tenant config (persisted to DB, cached in-memory). All trigger listeners (`FlowEventListener`, `KafkaFlowTriggerManager`, `ScheduledFlowExecutor`) check this flag before starting new executions. In-flight executions complete but no new ones start. Resume via `POST /api/admin/flows/resume-all`. UI: Red "Pause All Flows" button on the Flow Settings page with confirmation dialog. Banner shown at top of Flows list when paused: "Flow processing is paused. No new executions will start."

**Audit Trail:** All flow lifecycle events logged to `flow_audit_log` table:
- Columns: `id`, `tenant_id`, `flow_id`, `action` (CREATED, UPDATED, ACTIVATED, DEACTIVATED, DELETED, EXECUTED, CANCELLED), `user_id`, `timestamp`, `details` (JSONB — captures before/after for edits)
- Added to V71 migration

**Database addition to V71:**
```sql
CREATE TABLE flow_audit_log (
    id          VARCHAR(36) PRIMARY KEY,
    tenant_id   VARCHAR(36) NOT NULL,
    flow_id     VARCHAR(36) NOT NULL,
    action      VARCHAR(30) NOT NULL,
    user_id     VARCHAR(36),
    details     JSONB,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_audit_tenant ON flow_audit_log(tenant_id, flow_id);

CREATE TABLE flow_execution_dedup (
    event_id    VARCHAR(100) NOT NULL,
    flow_id     VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, flow_id)
);
```

### 1.10 Testing Strategy

- Unit tests for `FlowDefinitionParser` — Parse various definition JSONs, validate structure
- Unit tests for each `StateExecutor` — Mock ActionHandlerRegistry, verify state transitions and data flow
- Unit tests for `StateDataResolver` — InputPath/OutputPath/ResultPath combinations
- Unit tests for `ChoiceStateExecutor` — All comparison operators
- Integration test: Full flow execution from start to terminal state with real DB
- Integration test: Wait state → persist → resume flow
- Integration test: Parallel branches → merge results
- Integration test: Error → Retry → Catch → fallback state

### 1.11 Deliverables

- [ ] FlowDefinition model and parser
- [ ] StateDataResolver (JSONPath transforms: InputPath, OutputPath, ResultPath)
- [ ] FlowEngine with all 8 state executors
- [ ] FlowStore interface and JdbcFlowStore implementation
- [ ] FlowTriggerEvaluator (record trigger filtering, Kafka trigger filtering)
- [ ] InitialStateBuilder (canonical state envelope for all trigger types)
- [ ] InputMappingResolver (optional trigger_config.inputMapping transforms)
- [ ] Database migration V71 (flow_type expansion, flow_step_log, flow_pending_resume)
- [ ] FlowExecutionController REST endpoints (execute, cancel, resume, webhook, steps)
- [ ] FlowEventListener for RECORD_TRIGGERED flows
- [ ] KafkaFlowTriggerManager for KAFKA_TRIGGERED flows (dynamic consumer management)
- [ ] ScheduledFlowExecutor for SCHEDULED flows and Wait state resumption
- [ ] QUERY_RECORDS, TRANSFORM_DATA, EMIT_KAFKA_EVENT task handlers
- [ ] ActionHandlerDescriptor interface and REST endpoint (`GET /api/flows/resources`)
- [ ] Descriptors for all 15+ built-in handlers (configSchema, inputSchema, outputSchema)
- [ ] Template expression resolver (`${$.path}` in config values)
- [ ] RBAC permission checks for flow operations (MANAGE_FLOWS, EXECUTE_FLOWS, VIEW_FLOW_EXECUTIONS)
- [ ] Per-tenant execution rate limiting with configurable concurrent execution limits
- [ ] Trigger event idempotency (Redis-based dedup with DB fallback)
- [ ] Graceful shutdown with in-flight execution persistence and cross-pod resume
- [ ] Flow-level execution timeout with automatic cancellation
- [ ] Audit trail logging (flow_audit_log table + write-through on all operations)
- [ ] Webhook HMAC signature verification
- [ ] Emergency pause/resume all flows API and tenant flag
- [ ] Comprehensive unit and integration tests

---

## Phase 2 — Visual Workflow Builder UI

**Goal**: Build a complete, intuitive admin experience for creating, configuring, and managing flows. Replace the raw JSON textarea in `FlowsPage.tsx` with a guided workflow that takes users from flow creation through trigger setup, visual state composition, and deployment — all through the admin UI.

### 2.1 Dependencies

- `@xyflow/react` (React Flow v12+) — Node-based graph editor (MIT, includes minimap, controls, background, edge types)

### 2.2 User Journey Overview

The flow builder follows a progressive disclosure pattern. Users don't see the full complexity upfront — they are guided through setup steps, and the visual canvas only appears after the basics are configured.

```
Flows List Page                Create/Edit Flow
─────────────                  ─────────────────
┌─────────────────┐            ┌────────────────────────────────────┐
│ My Flows        │            │ ① Properties (name, description)   │
│                 │            │ ② Trigger (what starts this flow?) │
│ [+ New Flow]    │──Create──▶ │ ③ Input Mapping (optional)         │
│                 │            │ ④ Canvas (visual state builder)    │
│ Order Process   │──Edit────▶ │ ⑤ Settings (error handling, etc.)  │
│ Daily Report    │            └────────────────────────────────────┘
│ Payment Webhook │                         │
│                 │            ┌─────────────▼──────────────────────┐
│ [Executions]    │──View────▶ │ Execution History & Debug Viewer   │
└─────────────────┘            └────────────────────────────────────┘
```

### 2.3 Flows List Page (Enhanced FlowsPage.tsx)

Replaces existing `FlowsPage.tsx` following `CollectionsPage.tsx` patterns.

**Columns:** Name (clickable → designer), Type (color-coded badge: Record/Scheduled/API/Kafka), Status (Active green/Draft gray/Disabled red dot), Health (sparkline showing last 24h success/failure ratio — green bar for healthy, yellow for degraded >5% failures, red for critical >20% failures; hover for tooltip with exact counts), Last Run (relative timestamp + status icon), Actions ("···" menu: Edit, Duplicate, View Executions, Enable/Disable, Delete).

**Flow failure notifications:** When a flow execution fails, an in-app notification is created for the flow's creator (and optionally configured recipients). Notification includes flow name, error summary, and link to the debug view. Configurable per-flow: "Notify on failure" toggle in flow settings with recipient list. Uses the existing `SEND_NOTIFICATION` infrastructure.

**Filters:** Search by name (debounced), Type filter dropdown, Status filter dropdown. Paginated.

**Soft delete / archive:** Deleting a flow sets `status = 'ARCHIVED'` (not hard delete). Archived flows are hidden from the default list view but accessible via "Show archived" toggle. Execution history is preserved. Archived flows can be restored or permanently deleted (admin-only, with confirmation). The `ON DELETE CASCADE` on `flow_step_log` and `flow_execution` foreign keys is changed to `ON DELETE SET NULL` so execution history survives flow deletion.

**Empty state:** Centered "No flows yet" with prominent "New Flow" CTA.

### 2.4 Flow Creation Wizard

Full-page, 3-step wizard (progressive disclosure — no blank canvas overwhelm):

**Step 1: Choose Flow Type** — Card selector with 4 options: Record Change, Scheduled, API Call/Webhook, Kafka Message. Each card has icon, title, and brief description. Selected card gets blue border.

**Step 2: Configure Trigger** — Adapts form based on Step 1 selection:

- **Record Change:** Collection (searchable dropdown), Events (checkboxes: Created/Updated/Deleted). Advanced (collapsed): triggerFields (multi-select from collection fields), filterFormula (formula editor with syntax highlighting), synchronous checkbox (with warning about execution time).
- **Scheduled:** Quick presets (Hourly/Daily/Weekly/Monthly) OR cron expression input with human-readable preview. Timezone dropdown. Optional static inputData key-value editor.
- **API Call/Webhook:** Optional webhook path input (shows full URL preview with copy-to-clipboard button). Authentication dropdown (Tenant API Key/Webhook Secret/None). When "Webhook Secret" is selected: auto-generated HMAC signing secret displayed once with copy button, "Regenerate Secret" action with confirmation. Optional input schema builder (field name, type, required — table with add/remove rows).
- **Kafka Message:** Topic (text input), Consumer group (auto-generated default, editable). Payload format (JSON/String/Avro). Advanced (collapsed): key filter (glob pattern), message body filter (path + operator + value).

**Step 3: Name & Description** — Name (required), Description (optional). "Open Designer" button creates the flow as draft and navigates to the visual designer.

### 2.5 Flow Designer Page (Full-Screen Editor)

**Route:** `/:tenantSlug/flows/:flowId/design`

Full-screen, three-panel layout (no sidebar, no max-width constraint):

| Panel | Width | Content |
|-------|-------|---------|
| **Steps Palette** (left) | 160px, collapsible | Draggable state type cards grouped: Actions (Task), Logic (Choice, Parallel, Map), Control (Wait, Pass), Terminal (Succeed, Fail). Drag to canvas to add. |
| **Canvas** (center) | Flex | React Flow instance with custom nodes, edges, minimap, zoom controls, background grid |
| **Properties** (right) | 320px, collapsible | Trigger summary (top), selected step config (middle), flow settings (bottom). Collapsible accordions. |

**Bottom bar:** Validation status — warning/error count, expandable error list linking to specific nodes.

### 2.6 Custom Node Components

Each state type gets a custom React Flow node with distinct visual styling that makes the flow readable at a glance.

| State Type | Shape | Color (Tailwind) | Icon (lucide-react) | Size |
|-----------|-------|-------------------|---------------------|------|
| Task | Rounded rectangle | `bg-blue-50 border-blue-300` | `Cog` | 180x60 |
| Choice | Diamond-shaped (rotated square) | `bg-amber-50 border-amber-300` | `GitBranch` | 120x120 |
| Parallel | Double-bordered rectangle | `bg-purple-50 border-purple-300` | `Columns` | 200x60 |
| Map | Rectangle with loop badge | `bg-teal-50 border-teal-300` | `Repeat` | 180x60 |
| Wait | Rectangle with clock badge | `bg-gray-50 border-gray-300` | `Clock` | 180x60 |
| Pass | Dashed-border rectangle | `bg-gray-50 border-gray-300 border-dashed` | `ArrowRight` | 160x50 |
| Succeed | Rounded pill / circle | `bg-green-50 border-green-400` | `CheckCircle` | 120x50 |
| Fail | Rounded pill / circle | `bg-red-50 border-red-400` | `XCircle` | 120x50 |

**Node content:**
- State name (bold, editable on double-click)
- State type label (small, muted text)
- For Task: resource name badge (e.g., "HTTP_CALLOUT" in a small pill)
- For Choice: shows number of rules (e.g., "3 rules")
- For Wait: shows duration (e.g., "5 min" or "until event")
- Selection: blue glow outline when selected
- Validation: red border pulse when invalid

**Edges:**
- Solid arrow for normal transitions (`Next`)
- Labeled edges for Choice branches (e.g., "status = ACTIVE" on the edge)
- Dashed red edges for Catch/error transitions
- Animated edges for currently-running step (in debug mode)

### 2.7 Properties Panel — Trigger & Step Configuration

**Trigger section** (always visible at top): Summary card showing trigger type, collection/topic, events, filter. "Edit Trigger" button opens Sheet with same forms as creation wizard.

**Flow Settings section** (collapsible accordion, below trigger):
- **Execution timeout:** Number input with unit dropdown (seconds/minutes/hours). Default: 1 hour. Tooltip explains this is the max total flow duration before automatic cancellation.
- **Sensitive fields:** Multi-input field for JSONPath expressions (e.g., `$.record.data.ssn`, `$.input.creditCard`). These fields are redacted in execution logs. Inline help: "Values matching these paths will be replaced with '***REDACTED***' in execution history."
- **Error handling default:** Dropdown (Stop on first error / Continue and log errors). Applied to Task states that don't have explicit Catch rules.
- **Max concurrent executions:** Optional per-flow override of tenant limit. Number input. Helps prevent a single high-volume flow from consuming the tenant's entire quota.
- **Failure notifications:** Toggle "Notify on failure" (default: on for flow creator). Recipient list: searchable user dropdown to add additional recipients. Uses existing in-app notification system (`SEND_NOTIFICATION` handler).

**Step configuration** (shown when a node is selected):

All step types share: Name (editable), Comment (optional).

- **Task:** Resource dropdown (grouped by category: Data, Communication, Integration, Runtime Modules). Data mapping: InputPath, ResultPath, OutputPath, Timeout. Handler Configuration: schema-driven form generated from `configSchema` — identical UX for built-in and runtime modules. All string fields support `${$.path}` template expressions with autocomplete from upstream output schemas. Falls back to raw JSON editor if no configSchema. Collapsible sections: Retry Rules (errorEquals, interval, maxAttempts, backoffRate), Error Handling / Catch Rules (errorEquals, next state, resultPath).
- **Choice:** Ordered rule list. Each rule: Variable (JSONPath), Operator (dropdown), Value, Then go to (state dropdown). Add/remove rules. Default state dropdown. Optional AND/OR grouping toggle for compound rules.
- **Wait:** Radio selection: Fixed duration (seconds input), Until timestamp (JSONPath to timestamp field), Until external event (event name + timeout + timeout-next-state).
- **Parallel:** Branch list with name, step count, "Open Branch Editor" (nested canvas with breadcrumb navigation). Add/remove branches. ResultPath input.
- **Map:** Items path (JSONPath to array), Max concurrency, "Open Iterator Editor" (nested canvas), ResultPath.
- **Pass:** Result (JSON editor for literal value), ResultPath.
- **Succeed/Fail:** Fail includes Error (code) and Cause (message) inputs.

### 2.8 Input Mapping Configuration

In flow settings: Radio choice between "Use default (full trigger envelope)" and "Custom mapping" (key-value editor: State Key → Source JSONPath, with live preview of resulting state object).

### 2.9 Flow Designer Toolbar

Top bar: Back button (with unsaved changes prompt), flow name + type + status + version. Buttons: JSON (toggle raw definition overlay), Validate, Test, Save (draft), Activate dropdown. Unsaved changes dot indicator.

**Activation confirmation:** Clicking "Activate" shows a confirmation dialog summarizing what will happen: "This flow will start processing [RECORD_TRIGGERED: 'Created' and 'Updated' events on 'orders' collection] [SCHEDULED: every weekday at 8:00 AM CST] [API: available at POST /api/flows/{id}/execute]. Are you sure?" Shows validation status (must pass) and warns if replacing a previous published version.

### 2.10 Canvas Interaction

- **Add states:** Drag from palette, or double-click canvas for quick-add menu
- **Connect states:** Drag between node handles. Choice nodes: each output handle maps to a rule.
- **Edit:** Click to select (properties panel), double-click name to inline edit, right-click context menu, Delete key removes
- **Navigation:** Scroll zoom, drag pan, minimap, fit-to-screen, Ctrl+Z/Y undo/redo, Ctrl+C/V copy/paste
- **Canvas-JSON sync:** Bidirectional on every change. Positions in `_metadata.nodePositions`. Edges derived from `Next`/`Choices[].Next`/`Default`/`Branches`.

### 2.11 Schema-Driven Autocomplete

All JSONPath fields offer autocomplete from available state data at that point in the flow:
1. UI loads resource descriptors from `GET /api/flows/resources` at startup
2. For each step, traces graph from StartAt computing available schema (trigger state + upstream outputSchemas merged at ResultPaths)
3. Dropdown suggests valid paths when focused. Falls back to free-form if no schema available.

Applies identically to built-in and runtime-loaded modules.

### 2.12 Validation

Real-time validation on every canvas change. Rules: StartAt references existing state, all states reachable, Choice has rules + default, Parallel has branches, Task references valid resource, no infinite loops without Wait, terminals have no Next, valid JSONPath expressions, complete trigger config. Display: bottom bar with warning/error count, expandable error list linking to specific nodes, red border pulse on invalid nodes.

### 2.13 Setup Navigation Integration

The Flows page is registered in the **Automation** category of the Setup Home Page alongside existing items:

```
Automation
├── Workflow Rules  → (removed in Phase 3, replaced by redirect to Flows)
├── Flows           → /flows  (promoted to primary position)
├── Approvals       → /approvals
├── Scheduled Jobs  → /scheduled-jobs
└── Scripts         → /scripts
```

After Phase 3 migration, "Workflow Rules" is removed and "Flows" becomes the single entry point for all automation.

### 2.14 Testing Strategy

- Component tests for each custom node (renders, selection, inline edit)
- Component tests for each property panel variant (Task, Choice, Parallel, Map, Wait, Pass)
- Component tests for trigger configuration forms (Record, Kafka, Scheduled, API)
- Unit tests for Canvas ↔ JSON sync (round-trip fidelity for all state types)
- Unit tests for validation engine (each rule)
- Unit tests for input mapping preview generation
- Integration test: Full creation wizard → designer → save → verify persisted definition
- Integration test: Load existing flow → modify node → add edge → save → verify changes
- Accessibility: Keyboard navigation through canvas, screen reader labels on all nodes and controls

### 2.15 Deliverables

- [ ] Enhanced FlowsPage.tsx (list view with filters, status badges, last run)
- [ ] Flow Creation Wizard (type selection → trigger config → name → open designer)
- [ ] FlowDesignerPage with three-panel layout
- [ ] React Flow integration with custom node components for all 8 state types
- [ ] Custom edge components (normal, choice-labeled, error/catch)
- [ ] Steps Palette (left panel, draggable state type cards)
- [ ] Properties Panel with type-specific forms for all 8 state types
- [ ] Trigger configuration forms (Record, Kafka, Scheduled, API) in properties panel and wizard
- [ ] Input Mapping configuration UI with live preview
- [ ] Flow Settings panel (execution timeout, sensitive fields, error handling default, max concurrent)
- [ ] Webhook secret management (generation, display, regenerate) in API/Webhook trigger config
- [ ] Canvas ↔ JSON bidirectional sync
- [ ] Real-time validation engine with visual error indicators
- [ ] Flow toolbar (save, validate, test, activate, JSON toggle)
- [ ] Undo/redo support
- [ ] Keyboard shortcuts and context menus
- [ ] Unsaved changes detection and confirmation dialogs
- [ ] Flow health indicators on list page (24h sparkline, success/failure ratio)
- [ ] Flow failure notification system (in-app notifications on execution failure)
- [ ] Activation confirmation dialog with trigger summary
- [ ] Soft delete / archive for flows with "Show archived" toggle
- [ ] Setup navigation integration
- [ ] Component, unit, and accessibility tests

---

## Phase 3 — Migrate Workflow Rules to Flows & Remove Legacy

**Goal**: Convert all existing Workflow Rules into Flow definitions, verify equivalence, then remove the legacy WorkflowEngine and related code.

### 3.1 Migration Tool

**New class:** `WorkflowRuleToFlowMigrator`

Converts a `WorkflowRuleData` + its `WorkflowActionData[]` list into a Flow Definition JSON:

**Mapping logic:**
1. Each `WorkflowActionData` becomes a Task state with `Resource = actionType`
2. Actions are chained sequentially: Action1 → Action2 → ... → Succeed
3. `DECISION` actions become Choice states with their condition mapped to Choice rules
4. Error handling:
   - `STOP_ON_ERROR` → each Task gets `"Catch": [{"ErrorEquals": ["States.ALL"], "Next": "FlowFailed"}]`
   - `CONTINUE_ON_ERROR` → each Task gets `"Catch": [{"ErrorEquals": ["States.ALL"], "Next": "<nextTask>", "ResultPath": "$.errors.<stateName>"}]`
5. Retry config: `retryCount`/`retryDelaySeconds`/`retryBackoff` → Task Retry policies
6. Trigger mapping:
   - `ON_CREATE`, `ON_UPDATE`, `ON_DELETE`, `ON_CREATE_OR_UPDATE` → flow_type `RECORD_TRIGGERED` with `trigger_config` JSONB containing event type, collection, filter formula, trigger fields
   - `SCHEDULED` → flow_type `SCHEDULED` with `trigger_config` containing cron, timezone
   - `MANUAL` → flow_type `AUTOLAUNCHED`
   - `BEFORE_CREATE`/`BEFORE_UPDATE` → flow_type `RECORD_TRIGGERED` with `trigger_config` containing `synchronous: true` flag

### 3.2 Migration Execution

- **Admin API endpoint:** `POST /api/admin/migrate-workflow-rules`
  - Reads all workflow_rules for a tenant
  - Converts each to a Flow definition
  - Creates Flow records with `active = false` (draft)
  - Returns migration report (success count, error count, details)
- **Dry-run mode:** `?dryRun=true` — validates conversion without persisting
- **Per-rule migration:** `POST /api/admin/migrate-workflow-rules/{ruleId}` — migrate a single rule

### 3.3 Verification

- Side-by-side execution: Run both the old rule and the new flow against the same event, compare results
- Migration report includes: original rule name, generated flow definition, validation status, any conversion warnings
- Test coverage: Each trigger type + error handling mode + retry config combination

### 3.4 Legacy Removal

After all tenants are migrated and verified:

**Remove from runtime-core:**
- `WorkflowEngine.java`
- `WorkflowStore.java`
- `WorkflowRuleData.java`, `WorkflowActionData.java`
- `JdbcWorkflowStore.java`

**Remove from emf-worker:**
- `WorkflowEventListener.java` (replaced by `FlowEventListener`)
- `ScheduledWorkflowExecutor.java` (replaced by `ScheduledFlowExecutor`)
- Workflow-related beans from `WorkflowConfig.java`

**Database migration:** `V72__drop_legacy_workflow_tables.sql`
- Drop: `workflow_rule`, `workflow_action`, `workflow_execution_log`, `workflow_action_log`, `workflow_action_type`, `workflow_pending_action`, `workflow_rule_version`

**Remove from UI:**
- `WorkflowRulesPage.tsx`
- `WorkflowActionTypesPage.tsx`

**Update system collections:**
- Remove `workflowRules()`, `workflowActions()`, `workflowActionTypes()`, `workflowPendingActions()`, `workflowExecutionLogs()`, `workflowActionLogs()`, `workflowRuleVersions()` from `SystemCollectionDefinitions.java`

### 3.5 Before-Save Hooks

Before-save hooks (`BeforeSaveHookRegistry`, `BeforeSaveHook`) are **NOT part of the workflow rule system** — they are registered by modules and used independently by `DefaultQueryEngine`. These remain unchanged.

The `BEFORE_CREATE`/`BEFORE_UPDATE` workflow triggers that call FIELD_UPDATE will be migrated to synchronous flow execution (flow_type=RECORD_TRIGGERED with `synchronous: true` in trigger_config). The FlowEngine will support a `executeSynchronous()` method that blocks until completion and returns field updates.

### 3.6 Deliverables

- [ ] WorkflowRuleToFlowMigrator (rule → flow definition conversion)
- [ ] Migration admin API endpoints (batch + per-rule + dry-run)
- [ ] Side-by-side verification tooling
- [ ] Remove WorkflowEngine and all legacy classes
- [ ] Database migration to drop legacy tables
- [ ] Remove legacy UI pages
- [ ] Update SystemCollectionDefinitions
- [ ] Full test coverage for migration conversions

---

## Phase 4 — Runtime Module Loading

**Goal**: Allow tenants to install modules (JARs) by URL. Modules are downloaded, loaded via sandboxed ClassLoader, and expose new ActionHandler implementations available as Task resources in flows.

### 4.1 Module Manifest

Each module JAR must contain a `META-INF/emf-module.json` manifest. The manifest declares the module's handlers and importantly their **UI descriptors** — the same `ActionHandlerDescriptor` contract used by built-in handlers (Section 1.7). This is what enables runtime modules to render rich configuration forms, provide output schema autocomplete, and integrate identically to native capabilities.

```json
{
  "id": "stripe-integration",
  "name": "Stripe Integration",
  "version": "1.2.0",
  "description": "Stripe payment processing actions",
  "author": "EMF Marketplace",
  "moduleClass": "com.stripe.emf.StripeModule",
  "minPlatformVersion": "1.0.0",
  "permissions": ["HTTP_OUTBOUND", "READ_RECORDS", "WRITE_RECORDS"],
  "actionHandlers": [
    {
      "key": "stripe:charge",
      "name": "Create Charge",
      "category": "Payment",
      "description": "Create a Stripe payment charge",
      "icon": "CreditCard",
      "configSchema": { "...JSON Schema for config form..." },
      "inputSchema": { "...JSON Schema for expected input..." },
      "outputSchema": { "...JSON Schema for handler output..." }
    },
    {
      "key": "stripe:refund",
      "name": "Create Refund",
      "category": "Payment",
      "description": "Refund a Stripe charge",
      "icon": "RotateCcw",
      "configSchema": { "..." },
      "inputSchema": { "..." },
      "outputSchema": { "..." }
    },
    {
      "key": "stripe:customer_create",
      "name": "Create Customer",
      "category": "CRM",
      "description": "Create a new Stripe customer",
      "icon": "UserPlus",
      "configSchema": { "..." },
      "inputSchema": { "..." },
      "outputSchema": { "..." }
    }
  ]
}
```

**The schemas serve three purposes in the UI:**

1. **configSchema** → The Properties Panel renders a typed form (text inputs, dropdowns, key-value editors) instead of raw JSON. Fields support `${$.path}` template expressions with autocomplete.

2. **inputSchema** → The UI validates that the step's InputPath resolves to data matching what the handler expects. Shows warnings if there's a schema mismatch.

3. **outputSchema** → Downstream steps get autocomplete suggestions for this handler's output fields. Choice rule variables, Task config template expressions, and InputPath fields all benefit from knowing what data is available.

**Programmatic alternative:** Module authors can also define descriptors in Java by implementing `getDescriptor()` on their `ActionHandler` implementations (see Section 1.7). If both manifest and programmatic descriptors exist, the programmatic one takes precedence (allows dynamic schema generation based on tenant configuration).

**Result:** A tenant who installs the Stripe module sees `stripe:charge` in the Task resource dropdown under a "Payment" category, with a form showing "Amount", "Currency", "Customer ID" fields — not a raw JSON editor. Downstream steps can autocomplete `$.paymentResult.chargeId`, `$.paymentResult.status`, etc. The experience is identical to built-in resources.

### 4.2 Module Storage (S3 + Database)

**JAR Storage in S3:**

When a tenant installs a module, the platform:
1. Downloads the JAR from the provided source URL
2. Verifies SHA-256 checksum
3. Uploads the JAR to the platform's configured S3 bucket under a tenant-scoped key
4. Records the S3 path in the database — the original source URL is stored for provenance but never used after initial download

**S3 key structure:**
```
s3://{emf-modules-bucket}/modules/{tenant_id}/{module_id}/{version}/{module_id}-{version}.jar
```

**New class:** `ModuleStorageService`
- `uploadModule(tenantId, moduleId, version, jarBytes)` → S3 key
- `downloadModule(tenantId, moduleId, version)` → InputStream (for ClassLoader loading)
- `deleteModule(tenantId, moduleId, version)` → removes from S3
- Uses AWS SDK v2 (`software.amazon.awssdk:s3`)
- Configurable bucket name via `emf.modules.s3.bucket`
- Configurable prefix via `emf.modules.s3.prefix` (default: `modules/`)
- Supports S3-compatible stores (MinIO for local dev) via endpoint override

**Worker startup:** On pod startup, the `RuntimeModuleManager` reads all ACTIVE modules from the database, downloads their JARs from S3, and loads them into sandboxed ClassLoaders. This ensures modules are available after pod restarts without re-downloading from external URLs.

**Database migration:** `V73__runtime_module_tables.sql`

```sql
CREATE TABLE tenant_module (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL REFERENCES tenant(id),
    module_id       VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    description     VARCHAR(1000),
    source_url      VARCHAR(2000) NOT NULL,
    s3_key          VARCHAR(500) NOT NULL,
    jar_checksum    VARCHAR(64) NOT NULL,
    jar_size_bytes  BIGINT,
    module_class    VARCHAR(500) NOT NULL,
    manifest        JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'INSTALLED',
    installed_by    VARCHAR(36) NOT NULL,
    installed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_module UNIQUE (tenant_id, module_id),
    CONSTRAINT chk_module_status CHECK (status IN ('INSTALLING', 'INSTALLED', 'ACTIVE', 'DISABLED', 'FAILED', 'UNINSTALLING'))
);

CREATE TABLE tenant_module_action (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_module_id VARCHAR(36) NOT NULL REFERENCES tenant_module(id) ON DELETE CASCADE,
    action_key      VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50),
    config_schema   JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_module_tenant ON tenant_module(tenant_id);
CREATE INDEX idx_tenant_module_status ON tenant_module(tenant_id, status);
CREATE INDEX idx_tenant_module_action_key ON tenant_module_action(tenant_module_id);
```

### 4.3 Sandboxed ClassLoader

**New class:** `SandboxedModuleClassLoader extends URLClassLoader`

Restrictions:
- **No filesystem access** — Cannot call `java.io.File`, `java.nio.file.*` (except through provided APIs)
- **No network access** — Cannot open sockets directly; must use the provided `RestTemplate` from `ModuleContext`
- **No reflection on platform classes** — Cannot reflectively access EMF internals beyond the provided API
- **No System.exit, Runtime.exec** — Blocked system calls
- **No thread creation** — Cannot spawn threads; Parallel/Map execution is handled by the engine
- **Class allow-list** — Can load: `java.*`, `javax.*`, `com.fasterxml.jackson.*`, `org.slf4j.*`, and the module's own packages. Cannot load EMF internal packages except `com.emf.runtime.workflow.ActionHandler`, `com.emf.runtime.workflow.ActionContext`, `com.emf.runtime.workflow.ActionResult`, `com.emf.runtime.workflow.module.EmfModule`, `com.emf.runtime.workflow.module.ModuleContext`

Implementation approach:
- Override `loadClass()` to check allow-list before delegating to parent
- Wrap `ModuleContext` to provide controlled access to platform services
- Each tenant's modules loaded in a separate ClassLoader hierarchy for isolation

### 4.4 Module Lifecycle

```
Install → Download JAR from source URL → Verify SHA-256 checksum → Upload to S3 →
Parse manifest from JAR → Update DB status → Publish Kafka event →
All pods: receive event → Download JAR from S3 → Load via sandboxed ClassLoader →
Validate EmfModule interface → Register handlers → Set status ACTIVE
```

**On pod startup:**
```
Read ACTIVE modules from DB → Download JARs from S3 → Load ClassLoaders → Register handlers
```

**New class:** `RuntimeModuleManager`

- `installModule(tenantId, jarUrl, checksum, installedBy)` → Downloads JAR from source URL, verifies checksum, uploads to S3, parses manifest, updates DB, publishes `ModuleChangedEvent` to Kafka
- `uninstallModule(tenantId, moduleId)` → Updates DB status, publishes `ModuleChangedEvent` (UNINSTALLED). All pods react: deregister handlers, close ClassLoader. Originating pod deletes JAR from S3 after event is published.
- `enableModule(tenantId, moduleId)` / `disableModule(tenantId, moduleId)` → Updates DB status, publishes `ModuleChangedEvent` (ENABLED/DISABLED). All pods react: load/unload ClassLoader, register/deregister handlers.
- `loadModule(tenantId, moduleId)` → Downloads JAR from S3, loads via sandboxed ClassLoader, registers handlers locally (called by event listener)
- `unloadModule(tenantId, moduleId)` → Deregisters handlers, closes ClassLoader locally (called by event listener)
- `loadAllActiveModules(tenantId)` → Called on startup, loads all ACTIVE modules from S3
- `listModules(tenantId)` → Returns all installed modules with status
- `getAvailableActions(tenantId)` → Returns all action keys (built-in + runtime modules)

**Handler Registry Enhancement:**
Extend `ActionHandlerRegistry` to support tenant-scoped handlers:
- `registerTenantHandler(tenantId, actionKey, handler)`
- `getTenantHandler(tenantId, actionKey)` → Checks tenant handlers first, then falls back to global
- `removeTenantHandlers(tenantId, moduleId)`

### 4.5 Kafka-Based Module Event Propagation

Module lifecycle events propagate to **all running worker pods** without process restarts, following the existing `CollectionConfigEventPublisher` → `CollectionSchemaListener` Kafka pattern.

**Kafka topic:** `emf.config.module.changed`

**New event payload (runtime-events):** `ModuleChangedPayload` — fields: `id`, `tenantId`, `moduleId`, `name`, `version`, `s3Key`, `moduleClass`, `manifest` (JSON), `changeType` (INSTALLED/ENABLED/DISABLED/UNINSTALLED). Uses `ConfigEvent<ModuleChangedPayload>` envelope via `EventFactory.createEvent()`. Kafka key: `tenantId:moduleId`.

**New classes:**
- `ModuleConfigEventPublisher` (emf-worker) — Publishes to `emf.config.module.changed` after DB updates. Uses `KafkaTemplate<String, String>` + `ObjectMapper` (same pattern as `CollectionConfigEventPublisher`).
- `ModuleEventListener` (emf-worker) — `@KafkaListener` on `emf.config.module.changed`. On INSTALLED/ENABLED: calls `runtimeModuleManager.loadModule()`. On DISABLED/UNINSTALLED: calls `runtimeModuleManager.unloadModule()`. S3 cleanup only by originating pod.
- `ModuleConfigEventListener` (emf-gateway) — `@KafkaListener` that invalidates `resourceDescriptorCache` so `GET /api/flows/resources` returns updated list.

**Design constraints:**
1. **Fan-out consumer groups:** Each pod uses unique group ID (`${emf.worker.id}-modules`) so every pod receives every event
2. **Idempotent operations:** `loadModule`/`unloadModule` are no-ops if already in target state
3. **Thread safety:** `ConcurrentHashMap<String, Map<String, LoadedModule>>` (tenantId → moduleId → LoadedModule)
4. **DB as source of truth:** On pod startup, loads from DB. Kafka only for real-time propagation.
5. **Ordering:** Kafka key partitioning (`tenantId:moduleId`) ensures in-order processing per module
6. **Cache invalidation:** Gateway invalidates resource descriptors so UI autocomplete stays current

### 4.6 Module REST API

**New controller:** `ModuleController`

- `GET /api/modules` — List installed modules for current tenant
- `POST /api/modules/install` — Install module from URL `{ "jarUrl": "https://...", "checksum": "sha256:..." }`
- `POST /api/modules/{moduleId}/enable` — Enable module
- `POST /api/modules/{moduleId}/disable` — Disable module
- `DELETE /api/modules/{moduleId}` — Uninstall module
- `GET /api/modules/{moduleId}/actions` — List action handlers provided by module
- `GET /api/modules/actions` — List all available actions (built-in + modules)

### 4.7 Module Management UI

New page: `ModulesPage.tsx` — registered in the **Integration** category of Setup Home Page.

**Route:** `/:tenantSlug/modules`

**List View:**
```
┌──────────────────────────────────────────────────────────────────┐
│ Modules                                        [Install Module]  │
├──────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ 🔌 Stripe Integration                        v1.2.0         │ │
│ │ Payment processing actions                   ● Active        │ │
│ │ 3 actions: stripe:charge, stripe:refund, stripe:customer     │ │
│ │ Installed Feb 15, 2026              [Disable] [Uninstall]    │ │
│ ├──────────────────────────────────────────────────────────────┤ │
│ │ 📦 Netsuite Connector                        v2.0.1         │ │
│ │ Netsuite ERP integration                     ○ Disabled      │ │
│ │ 5 actions: netsuite:create_invoice, ...      [Enable]        │ │
│ │ Installed Jan 30, 2026              [ Enable] [Uninstall]    │ │
│ ├──────────────────────────────────────────────────────────────┤ │
│ │ ☁️ Salesforce Sync                            v1.0.0         │ │
│ │ Salesforce data synchronization              ⚠ Failed        │ │
│ │ Error: ClassNotFoundException...                              │ │
│ │ Installed Feb 20, 2026              [Retry ] [Uninstall]     │ │
│ └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

Each module is a card (not a table row) showing: name, description, version, status badge, action count, install date, and action buttons.

**Install Module Dialog (Sheet):**
```
┌─ Install Module ───────────────────┐
│                                    │
│ JAR URL *                          │
│ [https://modules.example.com/    ] │
│ [stripe-emf-1.2.0.jar           ] │
│                                    │
│ SHA-256 Checksum *                 │
│ [a1b2c3d4e5f6...                ] │
│                                    │
│ The module will be downloaded,     │
│ verified, and stored securely.     │
│ Only HTTPS URLs are accepted.      │
│                                    │
│           [Cancel] [Install]       │
│                                    │
│ ── Installation Progress ──        │
│ ✓ Downloading JAR...      Done     │
│ ✓ Verifying checksum...   Done     │
│ ✓ Uploading to storage... Done     │
│ ◎ Loading module...       Running  │
│ ○ Registering actions...  Pending  │
└────────────────────────────────────┘
```

**Module Detail View (click module card to expand):**
Shows the full list of action handlers provided by the module, with their keys, names, categories, and configuration schemas. Each action shows a "Used in X flows" count.

**Flow Designer Integration:**
When a runtime module is active, its action handlers appear in the Task resource dropdown under a "Runtime Modules" group (see Section 2.7). If a module is disabled/uninstalled, flows using its handlers show a warning badge on affected Task nodes.

### 4.8 Security Considerations

- JAR download from source URL over HTTPS only
- SHA-256 checksum verification required before S3 upload
- Module JAR size limit (configurable, default 50MB)
- S3 bucket access via IAM roles (no credentials in config); bucket is private, no public access
- S3 server-side encryption (SSE-S3 or SSE-KMS) for JARs at rest
- Admin-only permission for module installation
- Audit logging for all module lifecycle events (install, enable, disable, uninstall)
- Module execution timeout (configurable per handler)
- Resource limits: memory allocation tracking per ClassLoader
- Ability to disable runtime module loading entirely via config (`emf.modules.runtime.enabled=false`)
- Module version immutability — once a version is uploaded to S3, it cannot be overwritten (install new version instead)

### 4.9 Deliverables

- [ ] Module manifest schema and parser
- [ ] SandboxedModuleClassLoader with security restrictions
- [ ] RuntimeModuleManager (install, uninstall, enable, disable lifecycle with loadModule/unloadModule)
- [ ] Tenant-scoped handler registration in ActionHandlerRegistry
- [ ] Database migration V73
- [ ] ModuleController REST endpoints
- [ ] ModulesPage.tsx UI
- [ ] ModuleStorageService (S3 upload/download/delete with tenant-scoped keys)
- [ ] JAR download from source URL, checksum verification, size limits
- [ ] S3 bucket configuration (bucket name, prefix, endpoint override for MinIO)
- [ ] Pod startup module loading from S3 (database as source of truth)
- [ ] `ModuleChangedPayload` event class (runtime-events)
- [ ] `ModuleConfigEventPublisher` — publishes to `emf.config.module.changed` topic
- [ ] `ModuleEventListener` (emf-worker) — Kafka listener that loads/unloads modules on each pod
- [ ] `ModuleConfigEventListener` (emf-gateway) — Kafka listener that invalidates resource descriptor cache
- [ ] Fan-out consumer group pattern (each pod receives all module events)
- [ ] Idempotent load/unload with ConcurrentHashMap-based module tracking
- [ ] Audit logging for module events
- [ ] Security tests (verify sandbox prevents forbidden operations)
- [ ] Integration tests (install module → S3 upload → Kafka event → all pods load ClassLoader → execute flow using module handler)
- [ ] S3 integration test with MinIO (testcontainers)
- [ ] Kafka event propagation test (verify multi-pod load/unload without restart)

---

## Phase 5 — Visual Debugging & Execution Replay

**Goal**: Provide a visual execution viewer that overlays execution data onto the flow canvas, showing the path taken, state data at each step, timing, and errors.

### 5.1 Designer Tab Navigation

The Flow Designer page (from Phase 2) gains a tab bar that switches between Design mode and Execution/Debug mode:

```
┌────────────────────────────────────────────────────────────────────────┐
│ ← Flows  │  Order Processing Flow              │  [JSON]  [Validate] │
│           │  Record-Triggered · Active · v3      │  [▶ Test] [Save]   │
├────────────────────────────────────────────────────────────────────────┤
│   [Design]    [Executions]    [Debug: exec-abc123]                    │
├────────────────────────────────────────────────────────────────────────┤
```

- **Design** — The visual builder (Phase 2)
- **Executions** — List of all executions for this flow, with filtering
- **Audit Log** — Chronological log of all changes to this flow: who edited, activated, deactivated, and when. Each entry shows user, action, timestamp, and diff summary. Filterable by action type and date range.
- **Debug: {id}** — Opens when a user clicks "View" on an execution, or when a test execution completes. Multiple debug tabs can be open (one per execution). Close with X button.

### 5.2 Executions Tab

Shows a filterable list of all executions for the current flow:

```
┌──────────────────────────────────────────────────────────────────┐
│ Executions for: Order Processing Flow                            │
├──────────────────────────────────────────────────────────────────┤
│ Status: [All ▾]  │  Date: [Last 7 days ▾]  │  🔍 Execution ID   │
├──────────────────────────────────────────────────────────────────┤
│ ID         │ Status    │ Trigger    │ Steps │ Duration │ Started │
│────────────────────────────────────────────────────────────────── │
│ exec-abc1  │ ✓ Success │ Record upd │  5/5  │  1.2s    │ 2m ago  │
│ exec-abc2  │ ✕ Failed  │ Record cre │  3/5  │  0.8s    │ 5m ago  │
│ exec-abc3  │ ● Running │ API call   │  2/5  │  —       │ 12s ago │
│ exec-abc4  │ ◷ Waiting │ Kafka msg  │  4/5  │  —       │ 1h ago  │
│────────────────────────────────────────────────────────────────── │
│ Click any row to open the visual debug view in a new tab         │
└──────────────────────────────────────────────────────────────────┘
```

**Filters:**
- Status: All, Success, Failed, Running, Waiting, Cancelled
- Date range: Last hour, Last 24 hours, Last 7 days, Last 30 days, Custom range
- Search by execution ID (useful when correlating with logs)
- **Search by error:** Free-text search across error messages and error codes in step logs. Matches execution IDs where any step's `error_message` or `error_code` contains the search term.
- Test executions toggle: "Show test runs" (hidden by default)

### 5.3 Visual Debug View (Execution Replay)

Read-only debug mode: same canvas layout as designer, with execution data overlaid. Three-panel layout:

**Left — State Summary:** Execution status, duration, step count, trigger info, trace ID (copyable, with optional link to external tracing UI like Jaeger/Grafana Tempo if `emf.tracing.ui-url` is configured), initial/final state (collapsible JSON viewers).

**Center — Canvas with debug decorations:**
- Traversed edges: Green (success) or Red (failed). Untaken: gray dashed.
- Node badges: status icon + duration. Green check, red X (click for error/stack trace), blue pulse (running), yellow clock (waiting), gray (not reached).
- Failed nodes: red glow + error banner on node. Click any node to select and show step detail in right panel.

**Right — Step Detail:** Tabs: Input (state data after InputPath), Output (after ResultPath/OutputPath), Error (code + message + stack trace), Retries (attempt list), Raw (full flow_step_log record).

### 5.4 Execution Timeline

Horizontal bar at bottom: each step as colored segment proportional to duration (green/red/blue/yellow/gray). Parallel branches as stacked tracks. Hover for tooltip, click to select node. Scrubber for replay.

### 5.5 Live Execution Tracking

For running executions: auto-refresh as steps complete. Worker publishes `FlowStepCompletedEvent` to Kafka topic `emf.flow.step.completed` on each transition. Gateway subscribes and pushes to UI via SSE at `GET /api/flows/executions/{executionId}/events`. Canvas animates: current node pulsates, completed nodes transition to green check.

### 5.6 Failed Execution Management & Retry

**Manual retry of failed executions** — two modes:
- **Retry from beginning:** Creates a new execution with the same initial state as the original. Available for any FAILED or CANCELLED execution. API: `POST /api/flows/executions/{executionId}/retry?mode=full`
- **Retry from failed step:** Creates a new execution starting at the failed step, using the state data snapshot from the step before the failure. Only available if `flow_step_log` captured the input snapshot. API: `POST /api/flows/executions/{executionId}/retry?mode=from-failure`

**UI:** "Retry" dropdown button on failed execution rows in the Executions tab and Debug view. Options: "Retry from beginning", "Retry from failed step" (grayed out if no state snapshot available).

**Failed execution dashboard:** Cross-flow view accessible from the Flows list page — shows all FAILED executions across all flows for the tenant, filterable by flow, date range, error code. Supports bulk "Retry from beginning" for selected executions.

### 5.7 Database Changes

Migration `V74__flow_execution_viewer_support.sql`: Add `parent_execution_id`, `branch_index` columns to `flow_step_log`. Add index on `(execution_id, started_at)`.

### 5.8 Deliverables

- [ ] FlowDesignerPage tab navigation (Design, Executions, Debug tabs)
- [ ] Executions tab with filterable execution list (status, date range, error text search)
- [ ] FlowExecutionViewer component (read-only canvas with execution overlay)
- [ ] Custom debug-mode node decorations (status badges, duration labels, error banners)
- [ ] Step detail panel (Input/Output/Error/Retries/Raw tabs)
- [ ] State summary panel (trigger info, initial/final state, trace ID with external link)
- [ ] Audit Log tab in flow designer (chronological change history per flow)
- [ ] Execution timeline component with click-to-select and scrubber
- [ ] Live execution tracking via SSE
- [ ] `emf.flow.step.completed` Kafka topic and gateway SSE endpoint
- [ ] Manual retry API endpoints (retry from beginning, retry from failed step)
- [ ] Retry UI in execution viewer and executions tab
- [ ] Failed execution dashboard (cross-flow view with bulk retry)
- [ ] Database migration V74
- [ ] Component tests for debug view nodes, timeline, retry, and step detail panels

---

## Phase 6 — Extended Metrics, Testing & Polish

**Goal**: Add comprehensive Prometheus metrics for workflow executions, a simulation/testing mode, and final polish across the system.

### 6.1 Prometheus Metrics

New metrics registered in `FlowMetricsConfig`:

**Counters:**
- `emf_flow_execution_total{tenant, flow_id, flow_name, status}` — Total executions by outcome
- `emf_flow_step_total{tenant, flow_id, state_type, resource, status}` — Total step executions
- `emf_flow_error_total{tenant, flow_id, error_code}` — Errors by type

**Histograms:**
- `emf_flow_execution_duration_seconds{tenant, flow_id, flow_name}` — Execution duration distribution (p50, p95, p99)
- `emf_flow_step_duration_seconds{tenant, flow_id, state_type, resource}` — Step duration distribution

**Gauges:**
- `emf_flow_execution_active{tenant}` — Currently running executions per tenant
- `emf_flow_execution_waiting{tenant}` — Executions in Wait state
- `emf_flow_modules_active{tenant}` — Active runtime modules per tenant

**Summary metrics for dashboards:**
- Flow success rate (7d rolling window)
- Average execution time (by flow)
- Most-failed flows
- Slowest steps
- Module usage counts

**Default alerting rule templates** (Prometheus AlertManager YAML):
- Flow failure rate > 5% over 15m window
- Execution duration > 2x rolling average
- Execution queue depth > 80% capacity
- Module load failures
- Tenant rate limit rejections > 10/minute

### 6.2 Testing / Simulation Mode

**New capability:** Execute a flow in test mode with simulated inputs.

**Test Mode Features:**
- `POST /api/flows/{flowId}/test` — Execute with `{ "input": {...}, "mockHandlers": { "stripe:charge": { "result": {...} } } }`
- Mock handler responses — Specify expected outputs for Task states without actually calling the handler
- Step-through mode — Execute one step at a time, inspect state, then continue
- Dry-run validation — Parse and validate definition without executing
- Test execution marked with `is_test = true` in flow_execution (excluded from metrics)

**UI:**
- "Test" button in FlowDesignerPage toolbar opens a test configuration sheet:
  - **For RECORD_TRIGGERED flows:** "Pick a test record" — searchable dropdown of records from the trigger collection. Selecting a record populates the test input with that record's data as the initial state. Also supports manual JSON entry.
  - **For SCHEDULED/AUTOLAUNCHED flows:** JSON editor with schema-based autocomplete for inputData.
  - **For KAFKA_TRIGGERED flows:** JSON editor for simulated message payload with key/headers/body fields.
  - Mock handler configuration panel: toggle per-Task to use real handler or mock response. For each mocked handler, JSON editor for the mock result.
- Step-through controls (Next Step, Run to End, Reset) — displayed as a floating toolbar at the bottom of the canvas during test execution
- **Test result display:** When test completes, automatically opens a Debug tab for the test execution. Test executions are visually distinguished (dashed border on debug tab, "TEST" badge). Test runs are hidden by default in the Executions tab (toggle "Show test runs" to see them).

### 6.3 Flow Versioning & Draft/Published Workflow

**Draft/Published separation:** Flows have two key states:
- **Draft version** — the work-in-progress definition being edited in the designer. Saved on every "Save" click.
- **Published version** — the definition that actually executes in production. Set when the user clicks "Activate" or "Publish".
- The designer always edits the draft. The published version continues to run for incoming events until a new version is published. This prevents accidental production changes from in-progress edits.
- UI indicator: "Editing draft v4 — Published: v3" shown in the toolbar.

**Version history:**
- Each publish creates a new version (stored in `flow_version` table)
- Executions record which version they ran against
- Rollback to previous version: "Revert to v2" creates a new draft from that version's definition
- **Diff viewer:** Accessible from version history list. Shows structural diff on a split-pane canvas — left canvas shows version A, right shows version B, with added nodes (green), removed nodes (red), and modified nodes (yellow) highlighted. Also shows textual JSON diff below for detailed comparison.

**Migration:** `V75__flow_versioning.sql`

```sql
CREATE TABLE flow_version (
    id              VARCHAR(36) PRIMARY KEY,
    flow_id         VARCHAR(36) NOT NULL REFERENCES flow(id) ON DELETE CASCADE,
    version_number  INTEGER NOT NULL,
    definition      JSONB NOT NULL,
    change_summary  VARCHAR(500),
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_flow_version UNIQUE (flow_id, version_number)
);

ALTER TABLE flow_execution ADD COLUMN flow_version INTEGER;
ALTER TABLE flow ADD COLUMN published_version INTEGER;
```

### 6.4 Flow Templates / Marketplace Hooks

- Pre-built flow templates for common patterns:
  - Record approval workflow
  - Scheduled data sync
  - Webhook-triggered pipeline
  - Error notification flow
- Template gallery in the UI
- Export/import flow definitions as JSON
- Foundation for future module marketplace

### 6.5 Distributed Tracing (OpenTelemetry)

- Propagate trace context through flow execution. Each flow execution creates a root span; each step creates a child span with `state_type`, `resource`, `duration_ms`, and `status` attributes.
- HTTP callout handler (`HTTP_CALLOUT`) injects trace headers into outbound requests.
- Kafka publish handlers inject trace headers into outbound messages.
- Inbound triggers (Kafka listener, webhook) extract trace context from incoming requests/messages if present.
- Trace ID stored in `flow_execution` table for correlation with external systems.

### 6.6 Data Retention & Sensitivity

**Execution data retention:** Configurable per-tenant TTL for `flow_execution`, `flow_step_log`, and `flow_audit_log` records. Default: 90 days. Background cleanup job (`FlowDataRetentionJob`) runs daily, deletes expired records in batches. Option to archive to S3 before deletion (configurable `emf.flow.retention.archive-to-s3`).

**Data masking in execution logs:** Optional per-flow config `sensitiveFields[]` — listed JSONPath expressions are redacted in `flow_step_log` `input_snapshot` and `output_snapshot` (replaced with `"***REDACTED***"`). Applied at write time so sensitive data is never persisted. Configurable in flow settings UI.

### 6.7 Usage Quotas

Per-tenant configurable limits tracked in a `tenant_quota` table:
- `max_active_flows` (default: 100)
- `max_executions_per_day` (default: 10,000)
- `max_installed_modules` (default: 20)
- `max_flow_definition_size_kb` (default: 256)

Enforcement at API layer — exceeding a quota returns HTTP 403 with error code and current/limit values. Daily execution count reset via scheduled job. Quotas editable via admin API.

### 6.8 Flow Administration UI

New page: **Flow Settings** — registered in the **Automation** category of Setup Home Page (alongside Flows).

**Route:** `/:tenantSlug/flow-settings`

**Tabs:**

**Quotas & Limits tab:**
- Current usage vs. limits displayed as progress bars: active flows (e.g., "23 / 100"), executions today ("4,521 / 10,000"), installed modules ("3 / 20")
- Editable limit fields (admin-only) with save button
- Per-flow concurrent execution override list (flow name → max concurrent)
- Visual warning when approaching limits (yellow at 80%, red at 95%)

**Data Retention tab:**
- Execution history TTL: dropdown (30 / 60 / 90 / 180 / 365 days) or custom input
- Step log retention: same options (can differ from execution retention)
- Audit log retention: same options
- Archive to S3 toggle with bucket configuration status
- "Preview" button showing estimated record counts affected by current settings
- Manual purge button with confirmation ("Delete all execution data older than X days")

**Rate Limiting tab:**
- Max concurrent executions per tenant: number input (default: 50)
- Max executions per minute: number input (optional, default: unlimited)
- Queue overflow behavior: dropdown (Reject immediately / Queue with timeout)
- Current active execution count displayed as live gauge

**Audit Log tab:**
- Cross-flow audit log viewer: all flow changes across the tenant in one chronological view
- Columns: Timestamp, Flow Name, Action (CREATED/UPDATED/ACTIVATED/etc.), User, Details (expandable)
- Filters: Action type, Flow name, User, Date range
- Export as CSV button for compliance reporting

**Permissions tab:**
- Shows which roles have MANAGE_FLOWS, EXECUTE_FLOWS, VIEW_FLOW_EXECUTIONS permissions
- Quick-assign dropdown to add permissions to existing roles
- Integrates with existing EMF role/permission management system

**Dependencies tab:**
- **Collection → Flow impact analysis:** Shows which flows are triggered by or reference each collection. When editing a collection's schema (adding/removing/renaming fields), a warning banner shows affected flows. Example: "3 flows reference field 'status' on collection 'orders': Order Processing, Status Notification, Daily Report."
- **Flow → Flow dependencies:** Shows which flows invoke other flows (via AUTOLAUNCHED triggers or Kafka events). Visualized as a simple directed graph.
- **Module → Flow dependencies:** Shows which flows use action handlers from each runtime module. Warning before disabling/uninstalling a module: "This module provides handlers used by 5 active flows."

### 6.9 Polish & UX

- Keyboard shortcuts reference panel
- Auto-layout algorithm for imported/migrated flows
- Copy/paste states between flows
- Flow search and organization (folders/tags)
- Execution retention policy (configurable TTL for old execution logs)
- Bulk operations (enable/disable multiple flows)
- Flow dependency graph (which flows trigger other flows)

### 6.10 Deliverables

- [ ] FlowMetricsConfig with all Prometheus metrics
- [ ] Default Prometheus alerting rule templates
- [ ] Test execution mode (mock handlers, step-through, dry-run)
- [ ] Flow versioning with diff viewer
- [ ] Flow templates (3-5 built-in templates)
- [ ] Export/import flow definitions
- [ ] OpenTelemetry distributed tracing integration (spans per execution + step)
- [ ] Data retention job with configurable TTL and optional S3 archival
- [ ] Data masking for sensitive fields in execution logs
- [ ] Per-tenant usage quotas (flows, executions/day, modules, definition size)
- [ ] Flow Administration page with Quotas, Data Retention, Rate Limiting, Audit Log, Permissions, and Dependencies tabs
- [ ] Cross-flow audit log viewer with export capability
- [ ] Dependency analysis (collection→flow, flow→flow, module→flow impact)
- [ ] Emergency pause UI with banner notification
- [ ] Auto-layout for imported flows
- [ ] Comprehensive documentation

