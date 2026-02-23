# Comprehensive Workflow Automation System

## Context

The EMF platform has a **definition-only** workflow system — users can create workflow rules with triggers and actions via the UI/API, but **nothing executes**. There is no event listener, no execution engine, and no action handlers. The 7 defined action types are stored in the database but have zero runtime behavior. There is also no record-level event system — existing Kafka events cover config changes only, not data record CRUD.

This plan transforms the workflow system into a robust, enterprise-grade automation engine inspired by Salesforce Flow, Dynamics 365, and ServiceNow. The design is built around a **pluggable action handler SPI** so new action types (Slack, Teams, SMS, etc.) can be added in the future by simply implementing an interface and registering in the database — no core engine changes required.

### Key Design Principles
1. **Pluggable Action SPI** — all actions (built-in and custom) implement the same `ActionHandler` interface, are registered in the database, and discovered via classpath scanning by workers
2. **Reusable DataPath system** — a core component for traversing collection relationships to any depth, resolving field values at runtime, and building data payloads. Used across workflows, email templates, formulas, webhooks, and any feature needing cross-collection data access
3. **Live reload** — any workflow rule or action type change publishes a Kafka event; all services consume it and invalidate caches immediately (no restarts)
4. **Performance-first** — workflow rule cache per tenant/collection, batch event processing, configurable concurrency, async execution with dedicated thread pools
5. **Full UI coverage** — every feature is surfaced in the admin UI for create/read/update/delete

---

## Phase A — Record Event Pipeline & Execution Engine Foundation

### A1. Record Change Event Infrastructure

**Problem:** `DefaultQueryEngine` (runtime-core) performs all record CRUD but publishes no events.

**Changes:**

1. **New class in `runtime-events`:** `RecordChangeEvent`
   - Fields: `tenantId`, `collectionId`, `collectionName`, `recordId`, `changeType` (CREATED/UPDATED/DELETED), `data` (Map — current record), `previousData` (Map — for updates, null for create/delete), `changedFields` (List — computed diff for updates), `userId`, `timestamp`

2. **New interface in `runtime-core`:** `RecordEventPublisher`
   - Single method: `publish(RecordChangeEvent event)`
   - Optional dependency in `DefaultQueryEngine` (null-safe, like existing `encryptionService`)

3. **Modify `DefaultQueryEngine`:**
   - Add `RecordEventPublisher` as optional constructor param
   - After `create()` → publish CREATED event with full record data
   - After `update()` → publish UPDATED event with new data + previous data (already fetched on line 201) + computed changedFields
   - After `delete()` → fetch record before delete, then publish DELETED event

4. **New Kafka implementation in `emf-worker`:** `KafkaRecordEventPublisher`
   - Publishes to topic `emf.record.changed`, keyed by `tenantId:collectionId` for partition ordering
   - Uses `sendAfterCommit` pattern from `ConfigEventPublisher` for consistency

**Files:**
- `emf-platform/runtime/runtime-events/.../RecordChangeEvent.java` (new)
- `emf-platform/runtime/runtime-core/.../RecordEventPublisher.java` (new interface)
- `emf-platform/runtime/runtime-core/.../DefaultQueryEngine.java` (modify)
- `emf-worker/.../KafkaRecordEventPublisher.java` (new)

### A2. Reusable DataPath Resolution System

**This is a core platform component** used everywhere that needs cross-collection data access: workflows (data payloads for actions), email templates (merge fields), formula fields, webhooks, and future features. It lives in `runtime-core` alongside `FormulaEvaluator` and `QueryEngine`.

**Problem:** Today, the platform has no way to traverse collection relationships. `FormulaEvaluator` only accesses flat field values from the current record. `IncludeResolver` (gateway) handles one level of JSON:API includes but has no schema awareness. Email templates store raw HTML with no merge field rendering. Workflow actions cannot reference data from related records.

**Example use case:** A workflow triggers on an order line item and needs the customer's email address. The path is: `order_id.customer_id.email` — follow the `order_id` LOOKUP to the orders collection, then follow `customer_id` LOOKUP to the customers collection, then read the `email` field.

#### DataPath Model (runtime-core)

```java
// A single segment in a data path traversal
public record DataPathSegment(
    String fieldName,           // field in the current collection (e.g., "order_id")
    DataPathSegmentType type    // FIELD (terminal), RELATIONSHIP (traversal), SCRIPT (external)
) {
    public enum DataPathSegmentType { FIELD, RELATIONSHIP, SCRIPT }
}

// A complete path from root collection to a terminal value
public record DataPath(
    String rootCollectionId,         // starting collection UUID
    List<DataPathSegment> segments,  // ordered hops + terminal field
    String expression                // serialized form: "order_id.customer_id.email"
) {
    // Parse from dot-notation string
    public static DataPath parse(String expression, String rootCollectionId);
    // Serialize to dot-notation string for DB storage
    public String toExpression();
    // Get the terminal (last) segment
    public DataPathSegment terminal();
    // Get relationship segments (all except last)
    public List<DataPathSegment> relationships();
}
```

**Stored as:** A simple dot-separated string (e.g., `"order_id.customer_id.email"`) in any JSONB config column. Compact, human-readable, and easy to serialize/deserialize.

#### DataPathResolver (runtime-core)

The runtime resolver that follows a `DataPath` against live data:

```java
public class DataPathResolver {
    private final QueryEngine queryEngine;
    private final CollectionDefinitionProvider collectionProvider;

    // Resolve a single path to its terminal value
    public Object resolve(DataPath path, Map<String,Object> sourceRecord,
                          CollectionDefinition sourceCollection);

    // Resolve multiple paths, optimizing for shared prefixes
    // Returns Map<pathExpression, resolvedValue>
    public Map<String,Object> resolveAll(List<DataPath> paths,
                                          Map<String,Object> sourceRecord,
                                          CollectionDefinition sourceCollection);
}
```

**`CollectionDefinitionProvider`** — new interface in `runtime-core`:
```java
public interface CollectionDefinitionProvider {
    CollectionDefinition getByName(String collectionName);
    CollectionDefinition getById(String collectionId);
}
```
Implemented by the worker's existing `CollectionRegistry` + `CollectionOnDemandLoader`, and by the control-plane's `CollectionService`.

**Resolution algorithm for `resolve("order_id.customer_id.email", record, orderLinesDef)`:**
1. Split expression on `.` → `["order_id", "customer_id", "email"]`
2. For segment `"order_id"`:
   - Find `FieldDefinition` in `orderLinesDef` where `name == "order_id"`
   - Verify `fieldDef.type().isRelationship()` (LOOKUP or MASTER_DETAIL)
   - Read FK value: `fkValue = record.get("order_id")` → `"uuid-of-order"`
   - If `fkValue` is null → return null (short-circuit)
   - Get target: `targetCollection = fieldDef.referenceConfig().targetCollection()` → `"orders"`
   - Fetch target record: `queryEngine.getById(ordersDefinition, fkValue)` → order record
3. For segment `"customer_id"`: same process on the order record against the orders collection
4. For terminal segment `"email"`: return `customerRecord.get("email")`

**Optimizations in `resolveAll()`:**
- Groups paths by common prefix to avoid re-fetching the same intermediate records
- Example: `["order_id.customer_id.email", "order_id.customer_id.name", "order_id.total"]` → fetches the order record once, the customer record once
- Uses a tree structure internally: group by first segment, then recurse

**Error handling:**
- Null FK value at any hop → return null (graceful)
- Missing target record → return null + log warning
- Field not found in collection definition → throw `InvalidDataPathException` (config error, caught at save time)
- Non-relationship field used as intermediate hop → throw `InvalidDataPathException`
- Max depth: 10 hops (configurable, prevents runaway traversals)

#### DataPathValidator (runtime-core)

Validates paths against collection schemas at **save time** (when a workflow rule or email template is configured):

```java
public class DataPathValidator {
    private final CollectionDefinitionProvider collectionProvider;

    // Validates a path and returns metadata about the terminal field
    public DataPathValidationResult validate(DataPath path);
}

public record DataPathValidationResult(
    boolean valid,
    String errorMessage,              // null if valid
    String terminalFieldName,         // e.g., "email"
    FieldType terminalFieldType,      // e.g., STRING
    String terminalCollectionName     // e.g., "customers"
) {}
```

#### DataPayloadBuilder (control-plane)

Builds a complete data payload for workflow action execution from a list of named DataPaths:

```java
public class DataPayloadBuilder {
    private final DataPathResolver resolver;

    // Build a named map of resolved values from path definitions
    // Input: list of {name, dataPath} pairs
    // Output: {"customerEmail": "john@example.com", "orderTotal": 150.00}
    public Map<String,Object> buildPayload(
        List<DataPayloadField> fields,
        Map<String,Object> sourceRecord,
        CollectionDefinition sourceCollection);
}

public record DataPayloadField(
    String name,        // output key: "customerEmail"
    DataPath path       // resolution path: "order_id.customer_id.email"
) {}
```

This is what workflow actions use to assemble data for sending emails, calling APIs, etc.

#### MergeFieldRenderer (control-plane)

Renders merge tags in text templates using DataPath resolution:

```java
public class MergeFieldRenderer {
    private final DataPathResolver resolver;

    // Render "Hello {{customer_id.name}}, your order {{id}} is ready"
    // → "Hello John, your order ORD-001 is ready"
    public String render(String template, Map<String,Object> sourceRecord,
                         CollectionDefinition sourceCollection);
}
```

**Merge tag syntax:** `{{path.expression}}` — double curly braces containing a DataPath expression. Simple field references like `{{name}}` are single-segment paths (no traversal needed).

Used by:
- `EmailAlertActionHandler` — render email subject/body
- `OutboundMessageActionHandler` — render webhook body template
- `SendNotificationActionHandler` — render notification message
- Future: `EmailTemplateService` rendering

#### Script-Based Data Segments

For external API data or complex computed values, a special `SCRIPT` segment type:

**Config stored on the DataPath segment:**
```json
{
  "type": "SCRIPT",
  "scriptId": "uuid-of-script",
  "outputField": "creditScore"
}
```

**Resolution:** When the resolver encounters a SCRIPT segment:
1. Invoke `ScriptService.execute(scriptId, currentRecord)` with the current traversal record as input
2. The script returns a `Map<String,Object>` of additional data
3. The resolver reads `outputField` from the script result
4. Subsequent segments (if any) continue from the script output

**Example:** `customer_id.SCRIPT[credit-check].creditScore` — navigate to customer, run the credit-check script with customer data, return `creditScore` from the result.

#### DataPath REST Endpoints (control-plane)

For the UI to discover traversable paths:

- `GET /control/collections/{id}/data-paths?depth=3` — returns the field tree for the collection, expanding relationship fields to the specified depth
  - Response: nested tree of fields with `type`, `isRelationship`, `targetCollectionId`, and `children` (for expanded relationship fields)
  - Default depth: 2 (root + one level of expansion)
  - Max depth: 5
  - Permission: `VIEW_COLLECTIONS`

- `POST /control/data-paths/validate` — validates a DataPath expression against a root collection
  - Body: `{ "rootCollectionId": "...", "expression": "order_id.customer_id.email" }`
  - Response: `DataPathValidationResult`
  - Permission: `VIEW_COLLECTIONS`

#### DataPath UI Component

**`DataPathPicker`** — reusable React component for visually building a DataPath:

- Starts from a root collection (passed as prop)
- Displays all fields in the collection as a tree
- Relationship fields (LOOKUP, MASTER_DETAIL) show an expand arrow
- On expand, loads the target collection's fields via `GET /control/collections/{id}/data-paths`
- User clicks a terminal field to select it
- Component outputs the DataPath expression string (e.g., `"order_id.customer_id.email"`)
- Shows the resolved path as breadcrumbs: `Order Line → Order → Customer → Email`
- Supports multi-select mode (for DataPayload definitions where multiple fields are needed)

Used in:
- Workflow action config editors (data payload builder)
- Email template merge field inserter
- Webhook body template builder
- Any future feature needing field selection across collections

**Files:**
- `emf-platform/runtime/runtime-core/.../datapath/DataPath.java` (new)
- `emf-platform/runtime/runtime-core/.../datapath/DataPathSegment.java` (new)
- `emf-platform/runtime/runtime-core/.../datapath/DataPathResolver.java` (new)
- `emf-platform/runtime/runtime-core/.../datapath/DataPathValidator.java` (new)
- `emf-platform/runtime/runtime-core/.../datapath/CollectionDefinitionProvider.java` (new interface)
- `emf-control-plane/.../service/datapath/DataPayloadBuilder.java` (new)
- `emf-control-plane/.../service/datapath/MergeFieldRenderer.java` (new)
- `emf-control-plane/.../controller/DataPathController.java` (new)
- `emf-web/.../components/DataPathPicker.tsx` (new)
- `emf-ui/.../components/DataPathPicker.tsx` (new, imports from emf-web)

### A3. Pluggable Action Handler SPI & Registry

**This is the core extensibility mechanism.** New action types are added by:
1. Implementing the `ActionHandler` interface
2. Adding a row to the `workflow_action_type` database table
3. Dropping the handler JAR on the classpath — workers discover it automatically

**New database table: `workflow_action_type`**

```sql
CREATE TABLE workflow_action_type (
    id              VARCHAR(36)   PRIMARY KEY,
    key             VARCHAR(50)   NOT NULL UNIQUE,  -- e.g. 'FIELD_UPDATE', 'SLACK_MESSAGE'
    name            VARCHAR(100)  NOT NULL,          -- display name: 'Field Update'
    description     VARCHAR(500),
    category        VARCHAR(50)   NOT NULL,          -- 'DATA', 'COMMUNICATION', 'INTEGRATION', 'FLOW_CONTROL'
    config_schema   JSONB,                           -- JSON Schema for validating action config
    icon            VARCHAR(50),                     -- UI icon identifier
    handler_class   VARCHAR(255)  NOT NULL,          -- fully qualified class name
    active          BOOLEAN       DEFAULT true,
    built_in        BOOLEAN       DEFAULT true,      -- false for custom/plugin actions
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

**Seed data** — all 7 existing action types + new ones added in later phases.

**New interfaces/classes:**

```java
// The SPI — any action handler implements this
public interface ActionHandler {
    String getActionTypeKey();  // matches workflow_action_type.key
    ActionResult execute(ActionContext context);
    void validate(String configJson);  // validate config at save time
}

// Context passed to every handler
public record ActionContext(
    String tenantId, String collectionId, String collectionName,
    String recordId, Map<String,Object> data, Map<String,Object> previousData,
    List<String> changedFields, String userId, String actionConfigJson,
    String workflowRuleId, String executionLogId,
    Map<String,Object> resolvedData,        // data payload built by DataPayloadBuilder
    MergeFieldRenderer mergeFieldRenderer   // for rendering templates with {{path}} tokens
) {}

// Result returned by every handler
public record ActionResult(
    boolean success, String errorMessage, Map<String,Object> outputData
) {}
```

**`ActionHandlerRegistry`** — Spring component that:
- On startup, scans classpath for all `ActionHandler` beans (`@Component`)
- Builds a map of `actionTypeKey -> ActionHandler`
- Cross-references against `workflow_action_type` table to log warnings for unregistered handlers
- Exposes `getHandler(String actionTypeKey): ActionHandler`
- Listens on `emf.config.workflow.changed` Kafka topic and refreshes when action types change

**Action type REST endpoints** (for UI):
- `GET /control/workflow-action-types` — list all registered action types (populates UI dropdowns)
- `GET /control/workflow-action-types/{key}` — get config schema for action type
- Permissions: `MANAGE_WORKFLOWS`

**Remove the `CHECK` constraint on `workflow_action.action_type`** — the constraint currently hardcodes 7 values. Replace with a FK to `workflow_action_type.key` so new types are automatically valid.

### A4. Workflow Execution Engine

**New service: `WorkflowEngine`** — the core orchestrator:

1. **`WorkflowEventListener`** — `@KafkaListener` on `emf.record.changed`
   - Deserializes `RecordChangeEvent`
   - Sets `TenantContextHolder` for the request
   - Delegates to `WorkflowEngine.evaluate(event)`

2. **`WorkflowEngine.evaluate(RecordChangeEvent event)`:**
   - Queries cached workflow rules for tenant + collection + matching trigger type
   - For each matching rule (ordered by `executionOrder`):
     - Evaluates `filterFormula` against record data (using `FormulaEvaluator`)
     - If filter passes:
       - Uses `DataPayloadBuilder` to resolve all DataPath expressions defined in the rule's actions (shared prefix optimization minimizes record fetches)
       - Creates `ActionContext` with `resolvedData` map and `MergeFieldRenderer` instance
       - Executes actions in order via `ActionHandlerRegistry`, each action receives resolved data
     - Logs execution to `workflow_execution_log` + `workflow_action_log`
   - Handles error modes: STOP_ON_ERROR (default) or CONTINUE_ON_ERROR

3. **Workflow Rule Cache** — Redis-backed, keyed by `workflow-rules:{tenantId}:{collectionId}`
   - Populated on first access, invalidated via Kafka event on rule changes
   - TTL: 5 minutes (safety net), primary invalidation is event-driven
   - Pre-loads actions eagerly to avoid N+1

**Files:**
- `emf-control-plane/.../service/workflow/WorkflowEngine.java` (new)
- `emf-control-plane/.../service/workflow/WorkflowEventListener.java` (new)
- `emf-control-plane/.../service/workflow/ActionHandler.java` (new interface)
- `emf-control-plane/.../service/workflow/ActionHandlerRegistry.java` (new)
- `emf-control-plane/.../service/workflow/ActionContext.java` (new record)
- `emf-control-plane/.../service/workflow/ActionResult.java` (new record)

### A5. Core Action Handler Implementations

| Action Type Key | Handler Class | Category | Description |
|---|---|---|---|
| `FIELD_UPDATE` | `FieldUpdateActionHandler` | DATA | Updates fields on the triggering record. Config: `{ "updates": [{"field":"status","value":"Approved"}] }`. Values can use `{{path}}` merge syntax resolved via `MergeFieldRenderer`. Calls worker API. |
| `EMAIL_ALERT` | `EmailAlertActionHandler` | COMMUNICATION | Sends email. Config: `{ "templateId":"...", "to":"{{customer_id.email}}", "subject":"...", "body":"..." }`. Subject/body/to use `MergeFieldRenderer` for `{{path}}` substitution. Uses `EmailTemplateService`. |
| `CREATE_RECORD` | `CreateRecordActionHandler` | DATA | Creates record in target collection. Config: `{ "targetCollectionId":"...", "fieldMappings":[{"targetField":"name", "sourcePath":"customer_id.name"}] }`. Source paths are DataPath expressions resolved via `DataPayloadBuilder`. Calls worker API. |
| `INVOKE_SCRIPT` | `InvokeScriptActionHandler` | INTEGRATION | Executes script via `ScriptService`. Config: `{ "scriptId":"...", "inputPayload": [...] }`. Input payload built using `DataPayloadBuilder` from DataPath expressions. |
| `OUTBOUND_MESSAGE` | `OutboundMessageActionHandler` | INTEGRATION | HTTP webhook. Config: `{ "url":"...", "method":"POST", "headers":{}, "bodyTemplate":"...", "dataPayload": [...] }`. Body template uses `MergeFieldRenderer`. Data payload built via `DataPayloadBuilder`. Follows `WebhookService` HMAC pattern. |
| `CREATE_TASK` | `CreateTaskActionHandler` | DATA | Creates task record. Config: `{ "subject":"...", "assignTo":"...", "dueDate":"..." }`. Fields use `{{path}}` merge syntax. |
| `PUBLISH_EVENT` | `PublishEventActionHandler` | INTEGRATION | Publishes custom Kafka event. Config: `{ "topic":"...", "dataPayload": [...] }`. Payload fields defined as DataPath expressions, resolved at execution time. Uses `KafkaTemplate`. |

**Files:** One class per handler in `emf-control-plane/.../service/workflow/handlers/`

### A6. Enhanced Execution Logging

**New table: `workflow_action_log`** (per-action execution detail):

```sql
CREATE TABLE workflow_action_log (
    id               VARCHAR(36) PRIMARY KEY,
    execution_log_id VARCHAR(36) NOT NULL REFERENCES workflow_execution_log(id) ON DELETE CASCADE,
    action_id        VARCHAR(36) REFERENCES workflow_action(id),
    action_type      VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL,  -- SUCCESS, FAILURE, SKIPPED
    error_message    TEXT,
    input_snapshot   JSONB,
    output_snapshot  JSONB,
    duration_ms      INTEGER,
    executed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

**Add `error_handling` to `workflow_rule`:** VARCHAR(30) DEFAULT 'STOP_ON_ERROR'
- Values: STOP_ON_ERROR, CONTINUE_ON_ERROR

### A7. Workflow Config Change Events & Live Reload

When a workflow rule is created/updated/deleted:
1. `WorkflowRuleService` publishes `emf.config.workflow.changed` Kafka event (via `ConfigEventPublisher`)
2. All control-plane instances listen and evict Redis workflow rule cache for affected tenant/collection
3. No restart required — next evaluation fetches fresh rules into cache

**New Kafka topic:** `emf.config.workflow.changed`

**Add to `ConfigEventPublisher`:** `publishWorkflowChanged(WorkflowRule rule, ChangeType changeType)`

**New listener:** `WorkflowConfigListener` — `@KafkaListener` on workflow changed topic, evicts cache

**Follows exact same pattern as:**
- `ConfigEventPublisher.publishCollectionChanged()` → `sendAfterCommit()` (control-plane)
- `ConfigEventListener` in gateway consuming collection events

### A8. UI Updates (Phase A)

- **Action type dropdown** — populated from `GET /control/workflow-action-types` (dynamic, not hardcoded)
- **Action config editor** — dynamic form generated from `config_schema` JSON Schema per action type
- **DataPathPicker component** — reusable field selector that traverses collection relationships; used in action config editors for selecting data fields
- **Data payload builder UI** — within action config, define named fields using DataPathPicker to build the data payload for each action
- **Per-action execution log** — expandable rows in logs modal showing `workflow_action_log` detail (status, duration, error, input/output)
- **Error handling selector** — dropdown on rule form: STOP_ON_ERROR / CONTINUE_ON_ERROR
- **Action type management page** — list all registered action types, active/inactive toggle

**Migration:** `V59__workflow_engine_foundation.sql`

---

## Phase B — Enhanced Triggers & Conditions

### B1. Field-Level Change Detection

**Add `trigger_fields` (JSONB) column to `workflow_rule`:**
- When populated (e.g. `["status","priority"]`), rule only fires if one of those fields is in `RecordChangeEvent.changedFields`
- When null/empty, fires on any change (backward compatible)
- `WorkflowEngine` checks this before evaluating `filterFormula`

### B2. Scheduled Triggers

**Add trigger type `SCHEDULED`** to constraint.

**New columns on `workflow_rule`:**
- `cron_expression` VARCHAR(100)
- `timezone` VARCHAR(50)
- `last_scheduled_run` TIMESTAMP WITH TIME ZONE

**New service: `ScheduledWorkflowExecutor`**
- Spring `@Scheduled` polling (configurable interval, default 60s)
- Queries records matching `filterFormula` for each due scheduled rule
- Executes actions for each matching record
- Updates `last_scheduled_run` after each execution

### B3. API/Manual Triggers

**Add trigger type `MANUAL`.**

**New endpoint:** `POST /control/workflow-rules/{id}/execute`
- Body: `{ "recordIds": ["...", "..."] }`
- Executes the workflow for specified records
- Permission: `MANAGE_WORKFLOWS`
- Returns list of execution log IDs

### B4. Before-Save Triggers (Synchronous)

**Add trigger types `BEFORE_CREATE` and `BEFORE_UPDATE`:**
- Run synchronously within the record save pipeline
- Only support `FIELD_UPDATE` actions (safe for synchronous context)
- Worker calls control-plane: `POST /internal/workflow/before-save`
- Returns field updates to apply before persist
- Configurable timeout (default 5s) to prevent slow saves

### B5. UI Updates (Phase B)
- **Trigger field picker** — multi-select field chooser (populated from collection fields) when trigger is ON_UPDATE or ON_CREATE_OR_UPDATE
- **Cron expression builder** — visual cron schedule builder for SCHEDULED triggers
- **Manual execute button** — on rule detail page, multi-select records and execute workflow
- **Before-save indicator** — visual badge on rules with synchronous triggers

**Migration:** `V60__workflow_enhanced_triggers.sql`

---

## Phase C — Flow Control & Advanced Actions

### C1. Conditional Actions (If/Else Branching)

**New action type `DECISION`:**
- Config: `{ "condition": "record.status == 'High'", "trueActions": [...], "falseActions": [...] }`
- Nested action definitions within the JSONB config
- Engine evaluates condition via `FormulaEvaluator`, executes appropriate branch
- Logged as parent action with child action logs

### C2. Delayed / Scheduled Actions

**New action type `DELAY`:**
- Config: `{ "delayMinutes": 60 }` or `{ "delayUntilField": "dueDate" }` or `{ "delayUntilTime": "..." }`

**New table: `workflow_pending_action`:**

```sql
CREATE TABLE workflow_pending_action (
    id                VARCHAR(36) PRIMARY KEY,
    tenant_id         VARCHAR(36) NOT NULL,
    execution_log_id  VARCHAR(36) NOT NULL,
    workflow_rule_id  VARCHAR(36) NOT NULL,
    action_index      INTEGER NOT NULL,       -- resume point in action list
    record_id         VARCHAR(36) NOT NULL,
    record_snapshot   JSONB,                  -- record state at delay time
    scheduled_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, EXECUTED, CANCELLED
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

**New service: `PendingActionExecutor`** — polls for due pending actions, resumes workflow from saved action index.

### C3. New Action Types

| Action Type Key | Handler Class | Category | Description |
|---|---|---|---|
| `UPDATE_RECORD` | `UpdateRecordActionHandler` | DATA | Update record in any collection (cross-collection). Config: `{ "targetCollectionId":"...", "recordIdExpression":"...", "updates":[...] }`. Record ID and update values support DataPath expressions and `{{path}}` merge syntax. |
| `DELETE_RECORD` | `DeleteRecordActionHandler` | DATA | Delete a record. Config: `{ "targetCollectionId":"...", "recordIdExpression":"..." }`. Record ID supports DataPath expression (e.g., `"order_id.customer_id"` to resolve a related record's ID). |
| `SEND_NOTIFICATION` | `SendNotificationActionHandler` | COMMUNICATION | In-app notification. Config: `{ "userId":"...", "title":"...", "message":"..." }`. All text fields use `MergeFieldRenderer` for `{{path}}` substitution. |
| `HTTP_CALLOUT` | `HttpCalloutActionHandler` | INTEGRATION | Generic HTTP with response capture. Config: `{ "url":"...", "method":"...", "headers":{}, "body":"...", "dataPayload":[...], "responseVariable":"..." }`. URL/body use merge syntax; data payload built via `DataPayloadBuilder`. |
| `TRIGGER_FLOW` | `TriggerFlowActionHandler` | FLOW_CONTROL | Invoke another workflow (subflow). Config: `{ "workflowRuleId":"..." }`. Loop depth counter prevents infinite recursion (max depth: 5). Passes resolved data from parent context. |
| `LOG_MESSAGE` | `LogMessageActionHandler` | DATA | Write to audit/execution log. Config: `{ "message":"...", "level":"INFO" }`. Message uses `MergeFieldRenderer` for `{{path}}` substitution. |

Each new handler registered in `workflow_action_type` via migration seed data.

### C4. Action Config Validation

**`WorkflowActionValidator`** — validates action config JSON against `config_schema` from `workflow_action_type` at save time.
- Called from `WorkflowRuleService.createRule()` and `updateRule()`
- Returns descriptive errors: `"Action 'FIELD_UPDATE' config missing required field 'updates'"`

### C5. UI Updates (Phase C)
- **Decision builder** — visual if/else editor with nested action lists per branch
- **Delay configuration** — time duration picker or field reference selector
- **New action type forms** — config editors for each new action type
- **Subflow selector** — searchable dropdown to pick target workflow for TRIGGER_FLOW
- **Validation error display** — inline errors on action config fields at save time

**Migration:** `V61__workflow_flow_control.sql`

---

## Phase D — Enterprise Features & Performance

### D1. Retry Policies

**Add to `workflow_action` table:**
- `retry_count` INTEGER DEFAULT 0 — max retries (0 = no retry)
- `retry_delay_seconds` INTEGER DEFAULT 60
- `retry_backoff` VARCHAR(20) DEFAULT 'FIXED' — FIXED or EXPONENTIAL

Engine retries failed actions per config. Each retry logged in `workflow_action_log` with attempt number.

### D2. Workflow Versioning

**New table: `workflow_rule_version`:**

```sql
CREATE TABLE workflow_rule_version (
    id                VARCHAR(36) PRIMARY KEY,
    workflow_rule_id  VARCHAR(36) NOT NULL REFERENCES workflow_rule(id),
    version_number    INTEGER NOT NULL,
    snapshot          JSONB NOT NULL,   -- full rule + actions serialized
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    CONSTRAINT uq_rule_version UNIQUE (workflow_rule_id, version_number)
);
```

- Auto-incremented on each rule update
- Execution logs reference `version_number` active at execution time
- UI shows version history with diff view and rollback

### D3. Performance Optimization

**Workflow Rule Caching:**
- Redis cache: `workflow-rules:{tenantId}:{collectionId}` → list of active rules with actions pre-loaded
- Evicted on `emf.config.workflow.changed` Kafka event (event-driven, no polling)
- TTL: 5 minutes as safety net
- Add `WORKFLOW_RULES_CACHE` to `CacheConfig.java` alongside existing `COLLECTIONS_CACHE`

**Batch Event Processing:**
- `WorkflowEventListener` uses `@KafkaListener` with `batch = true`
- Groups events by tenant + collection, evaluates rules once per group
- Configurable: `emf.workflow.batch-size: 100`, `emf.workflow.batch-timeout-ms: 500`

**Dedicated Thread Pool:**
- Separate thread pool for workflow execution: `emf.workflow.thread-pool-size: 10`
- Prevents workflow processing from starving HTTP request threads or Kafka consumer threads
- Uses `ThreadPoolTaskExecutor` with bounded queue and caller-runs rejection policy

**Formula Compilation Cache:**
- Compiled filter formula expressions cached in-memory per rule ID
- Avoids re-parsing on every event evaluation
- Evicted when rule cache is evicted

**Connection Pooling for Outbound Actions:**
- Dedicated `RestTemplate` with connection pool for HTTP-based action handlers
- Configurable: `emf.workflow.http.max-connections: 50`, `emf.workflow.http.connect-timeout-ms: 5000`

**Parallel Action Execution:**
- Add `execution_mode` VARCHAR(20) DEFAULT 'SEQUENTIAL' to `workflow_rule`
- Values: SEQUENTIAL (default), PARALLEL
- When PARALLEL: independent actions run concurrently via `CompletableFuture.allOf()`
- Engine waits for all to complete, aggregates results

### D4. Execution Analytics

**New endpoints:**
- `GET /control/workflow-rules/analytics` — aggregate stats: execution counts by rule/status/time, avg duration, failure rate, top errors
- `GET /control/workflow-rules/{id}/analytics` — per-rule stats: execution timeline, action breakdown, error trends
- Query params: `startDate`, `endDate`, `status`, `limit`
- Powered by aggregation queries on `workflow_execution_log` + `workflow_action_log`

### D5. Execution Log Retention

**Scheduled cleanup job:**
- Configurable: `emf.workflow.log-retention-days: 90`
- Registered in `scheduled_job` table via existing `ScheduledJobService`
- Deletes `workflow_action_log` and `workflow_execution_log` older than retention period

### D6. UI Updates (Phase D)
- **Retry config** — per-action retry count, delay, backoff type selector in action editor
- **Version history** — timeline view with diff between versions, rollback button
- **Analytics dashboard** — charts for execution counts, success/failure rates, duration trends, top errors
- **Parallel execution toggle** — on rule form
- **Log retention settings** — admin settings page
- **Performance monitoring** — real-time view of pending/executing workflows

**Migration:** `V62__workflow_enterprise_features.sql`

---

## Database Migrations Summary

| Migration | Phase | Contents |
|-----------|-------|----------|
| `V59__workflow_engine_foundation.sql` | A | `workflow_action_type` table with seed data; `workflow_action_log` table; add `error_handling` to `workflow_rule`; drop CHECK constraint on `workflow_action.action_type`, replace with FK to `workflow_action_type.key` |
| `V60__workflow_enhanced_triggers.sql` | B | Add `trigger_fields`, `cron_expression`, `timezone`, `last_scheduled_run` to `workflow_rule`; expand `trigger_type` constraint with SCHEDULED, MANUAL, BEFORE_CREATE, BEFORE_UPDATE |
| `V61__workflow_flow_control.sql` | C | Seed new action types into `workflow_action_type`; `workflow_pending_action` table |
| `V62__workflow_enterprise_features.sql` | D | Add retry columns to `workflow_action`; `workflow_rule_version` table; add `execution_mode` to `workflow_rule` |

---

## Live Reload Architecture

```
User updates workflow rule in UI
  -> WorkflowRuleService saves to DB
  -> ConfigEventPublisher.publishWorkflowChanged() -- Kafka event after commit
  -> All control-plane instances receive event via WorkflowConfigListener
  -> Redis cache evicted for affected tenant/collection key
  -> Next record event triggers fresh rule load from DB into cache
  -> Zero downtime, no restarts
```

Same pattern for action type changes — `workflow_action_type` updates publish event, registries refresh.

Follows the exact `sendAfterCommit` + `@KafkaListener` pattern already proven in:
- `ConfigEventPublisher.publishCollectionChanged()` (control-plane)
- `ConfigEventListener` consuming events (gateway)
- `CollectionSchemaListener` consuming events (worker)

---

## Key Integration Points

| Feature | Reuses | File Path |
|---------|--------|-----------|
| Record events | `ConfigEvent<T>`, `sendAfterCommit` pattern | `runtime-events/.../ConfigEvent.java`, `ConfigEventPublisher.java` |
| DataPath resolution | `QueryEngine.getById()`, `CollectionDefinition`, `FieldDefinition`, `ReferenceConfig` | `runtime-core/.../QueryEngine.java`, `runtime-core/.../model/ReferenceConfig.java` |
| DataPath validation | `FieldDefinition.type().isRelationship()`, `ReferenceConfig.targetCollection()` | `runtime-core/.../model/FieldDefinition.java` |
| Collection metadata | `CollectionService`, `FieldService`, `CollectionRegistry` | `controlplane/.../CollectionService.java`, `runtime-core/.../CollectionRegistry.java` |
| Filter formulas | `FormulaEvaluator` | `runtime-core/.../FormulaEvaluator.java` |
| Email sending | `EmailTemplateService` + `MergeFieldRenderer` | `controlplane/.../EmailTemplateService.java` |
| Webhooks | `WebhookService` delivery/HMAC pattern | `controlplane/.../WebhookService.java` |
| Scripts | `ScriptService` (also used for SCRIPT DataPath segments) | `controlplane/.../ScriptService.java` |
| Cache | `CacheConfig` + Redis | `controlplane/.../CacheConfig.java` |
| Permissions | `SecurityService.hasPermission` | `controlplane/.../SecurityService.java` |
| Audit | `@SetupAudited` AOP | `controlplane/.../SetupAuditAspect.java` |
| Multi-tenancy | `TenantScopedEntity`, `TenantContextHolder` | `controlplane/.../TenantScopedEntity.java` |
| Scheduled jobs | `ScheduledJobService` | `controlplane/.../ScheduledJobService.java` |

---

## Implementation Order

| # | Task | Phase | Delivers |
|---|------|-------|----------|
| 1 | A1 — Record Change Events | A | Records publish events on CRUD |
| 2 | A2 — DataPath Resolution System | A | Core reusable component for traversing relationships, resolving field values, rendering merge templates. DataPath model + resolver + validator + payload builder + merge renderer + REST endpoints + UI DataPathPicker component |
| 3 | A3 — Action Handler SPI & Registry | A | Pluggable action framework + DB table + REST endpoints |
| 4 | A4 — Workflow Execution Engine | A | Engine listens for events, evaluates rules, runs actions (with DataPath-resolved payloads) |
| 5 | A5 — Core Action Handlers (7) | A | All existing action types work end-to-end with DataPath data access |
| 6 | A6 — Enhanced Execution Logging | A | Per-action detail in logs |
| 7 | A7 — Live Reload via Kafka | A | Rule changes propagate instantly, no restarts |
| 8 | A8 — UI: action types, config editor, DataPathPicker, log detail | A | Full UI for Phase A features |
| 9 | B1 — Field-Level Change Detection | B | Trigger only on specific field changes |
| 10 | B2 — Scheduled Triggers | B | Cron-based recurring workflows |
| 11 | B3 — Manual/API Triggers | B | On-demand execution via API/UI |
| 12 | B4 — Before-Save Triggers | B | Synchronous pre-persist workflows |
| 13 | B5 — UI: trigger field picker, cron builder | B | Full UI for Phase B features |
| 14 | C1 — Conditional Actions (DECISION) | C | If/else branching |
| 15 | C2 — Delayed Actions (DELAY) | C | Time-deferred execution |
| 16 | C3 — New Action Types (6) | C | UPDATE_RECORD, DELETE_RECORD, SEND_NOTIFICATION, HTTP_CALLOUT, TRIGGER_FLOW, LOG_MESSAGE |
| 17 | C4 — Action Config Validation | C | Schema validation at save time |
| 18 | C5 — UI: decision builder, delay config | C | Full UI for Phase C features |
| 19 | D1 — Retry Policies | D | Automatic retry with backoff |
| 20 | D2 — Workflow Versioning | D | Version history with diff and rollback |
| 21 | D3 — Performance Optimization | D | Caching, batching, thread pools, parallel execution |
| 22 | D4 — Execution Analytics | D | Dashboard data + API endpoints |
| 23 | D5 — Log Retention | D | Automatic cleanup of old logs |
| 24 | D6 — UI: analytics dashboard, version history | D | Full UI for Phase D features |

---

## Testing Strategy

- **Unit tests** for `DataPath` (parsing, serialization, validation of dot-notation expressions)
- **Unit tests** for `DataPathResolver` (single-hop, multi-hop, null FK handling, missing records, max depth enforcement, shared prefix optimization in `resolveAll`)
- **Unit tests** for `DataPathValidator` (valid paths, invalid field names, non-relationship intermediate hops, terminal field type detection)
- **Unit tests** for `DataPayloadBuilder` (build payload from multiple DataPaths, handle nulls)
- **Unit tests** for `MergeFieldRenderer` (template rendering with `{{path}}` tokens, missing values, nested paths, no tokens, escaped braces)
- **Unit tests** for each `ActionHandler` (mock dependencies, verify config parsing, verify DataPath/merge field usage, edge cases)
- **Unit tests** for `WorkflowEngine` (mock handlers, verify rule matching, filter evaluation, execution ordering, error handling modes, data payload resolution)
- **Unit tests** for `RecordChangeEvent` publishing in `DefaultQueryEngine`
- **Unit tests** for `ActionHandlerRegistry` (classpath scanning, missing handler detection)
- **Unit tests** for `WorkflowActionValidator` (config schema validation)
- **Integration tests** for DataPath end-to-end: create collections with relationships -> create records -> resolve multi-hop paths -> verify correct values returned
- **Integration tests** for full workflow flow: define rule with DataPath payload -> create record -> verify action executed with resolved data -> verify execution + action logs
- **Integration tests** for each trigger type (create, update, delete, field-change, scheduled, manual, before-save)
- **Integration tests** for live reload: update rule -> verify cache evicted -> verify new rule applied on next event
- **Integration tests** for action type registry: add handler -> verify discoverable -> verify executable
- **Performance tests** for batch event processing throughput
- **Performance tests** for DataPath resolution with deep traversals (measure latency at depth 5, 10)
- **Edge cases**: no matching rules, inactive rules, filter rejects, action failure with stop vs continue, circular subflow detection (max depth 5), concurrent rule updates during execution, null FK at various depths, circular relationship paths, SCRIPT segment failures

## Verification

1. `mvn clean install -DskipTests -f emf-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events -am -B`
2. `mvn verify -f emf-control-plane/pom.xml -B` — all tests pass
3. `mvn verify -f emf-gateway/pom.xml -B` — all tests pass
4. `cd emf-web && npm run lint && npm run typecheck && npm run format:check && npm run test:coverage`
5. `cd emf-ui/app && npm run lint && npm run format:check && npm run test:run`
6. Deploy to K8s, create workflow rule via UI -> create record in collection -> verify execution logs show actions ran successfully
