# EMF Platform

Shared runtime libraries for the EMF platform. These modules provide the core abstractions used by `emf-gateway`, `emf-worker`, and downstream services.

## Modules

```
emf-platform/runtime/
├── runtime-core                 # Core runtime library
├── runtime-events               # Shared Kafka event classes
├── runtime-jsonapi              # JSON:API model classes
├── runtime-module-core          # Workflow action handlers (CRUD, tasks, decisions)
├── runtime-module-integration   # Integration action handlers (HTTP, email, scripts)
└── runtime-module-schema        # Schema lifecycle hooks for system collections
```

### runtime-core

The foundational library providing dynamic, runtime-configurable collection management.

**Key capabilities:**
- **Collection model** -- `CollectionDefinition`, `FieldDefinition`, `FieldType` (26 types), `ValidationRules`, `ReferenceConfig`
- **Registry** -- Thread-safe `ConcurrentCollectionRegistry` with copy-on-write semantics and change listeners
- **Storage** -- `StorageAdapter` interface with two implementations:
  - `PhysicalTableStorageAdapter` (Mode A) -- real database tables per collection
  - `JsonbStorageAdapter` (Mode B) -- JSONB document store in a single table
  - `SchemaMigrationEngine` for ALTER TABLE operations
- **Query engine** -- `QueryEngine` with filtering (`FilterCondition`, 14 operators), sorting, pagination, and field selection
- **Validation engine** -- `DefaultValidationEngine` with field constraints, custom rules, and type coercion
- **Formula engine** -- `FormulaEvaluator` with compiled AST caching, built-in functions, and SQL translation
- **DataPath resolution** -- Relationship traversal with prefix optimization for batch resolves
- **Workflow engine** -- `ActionHandler` interface, `BeforeSaveHook` lifecycle, `WorkflowEngine` for rule execution
- **Module system** -- `EmfModule` plugin architecture for extending workflows
- **Event publishing** -- Async Kafka events via `KafkaEventPublisher` for configuration and record changes
- **REST router** -- `DynamicCollectionRouter` auto-exposes JSON:API endpoints for registered collections

**Configuration properties:**
- `emf.storage.mode` -- `PHYSICAL_TABLES` or `JSONB_STORE`
- `emf.query.default-page-size`, `emf.query.max-page-size`
- `emf.events.enabled`, `emf.events.topic-prefix`

### runtime-events

Shared Kafka event classes used across EMF services.

- `ConfigEvent<T>` -- Generic base event for configuration changes
- `RecordChangeEvent` -- Record-level lifecycle events
- `ChangeType` -- Event type enumeration (CREATE, UPDATE, DELETE)
- `EventFactory` -- Event creation helper

### runtime-jsonapi

Shared JSON:API model classes and parsing.

- `JsonApiDocument` -- Top-level response structure
- `ResourceObject` -- Resource representation with attributes, relationships, links
- `ResourceIdentifier`, `Relationship`, `JsonApiError`
- `JsonApiParser` -- JSON:API parsing logic

### runtime-module-core

Workflow action handlers for core operations.

- `FieldUpdateActionHandler` -- Update fields on the triggering record
- `CreateRecordActionHandler`, `UpdateRecordActionHandler`, `DeleteRecordActionHandler` -- Record CRUD
- `CreateTaskActionHandler` -- Task creation
- `DecisionActionHandler` -- Conditional branching
- `TriggerFlowActionHandler` -- Subflow invocation
- `LogMessageActionHandler` -- Execution logging

### runtime-module-integration

Integration action handlers for external communication.

- `HttpCalloutActionHandler` -- HTTP requests with response capture
- `OutboundMessageActionHandler` -- Webhook messages
- `PublishEventActionHandler` -- Kafka event publishing
- `EmailAlertActionHandler` -- Email notifications
- `InvokeScriptActionHandler` -- Server-side script execution
- `DelayActionHandler` -- Workflow delays
- `SendNotificationActionHandler` -- In-app notifications

SPI interfaces: `EmailService`, `ScriptExecutor`, `PendingActionStore`

### runtime-module-schema

Schema lifecycle hooks for system collections.

- `CollectionLifecycleHook` -- Collection validation and defaults
- `FieldLifecycleHook` -- Field validation and defaults
- `TenantLifecycleHook`, `UserLifecycleHook`, `ProfileLifecycleHook`

## Module Dependencies

```
runtime-core ──────► runtime-events
runtime-module-core ──────► runtime-core
runtime-module-integration ──────► runtime-core
runtime-module-schema ──────► runtime-core
runtime-jsonapi (standalone)
```

## Build

```bash
mvn clean install -DskipTests -f emf-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B
```

## Tech Stack

- Java 21
- Spring Boot 3.2.2
- Spring Kafka, Spring Data Redis, Spring Data JDBC
- PostgreSQL, Jackson
- Mockito 5.21, Testcontainers 1.19.3, jqwik 1.8.2
