# kelta-worker

Primary service for the Kelta platform. Owns database migrations (Flyway), workflow execution, collection lifecycle, and all business logic.

## Package Layout

```
io.kelta.worker/
  config/          ← Spring @Configuration (Kafka, Cerbos, S3, scheduler, tenant datasource)
  controller/      ← REST controllers (thin — delegate to services)
  event/           ← KafkaRecordEventPublisher
  filter/          ← Servlet filters (tenant context, MDC, metrics, login tracking)
  flow/            ← JdbcFlowStore (workflow persistence)
  handler/         ← Action handlers (e.g., SubmitForApprovalActionHandler)
  interceptor/     ← Cerbos authorization advice
  listener/        ← Kafka listeners + BeforeSaveHooks (config event publishing)
  module/          ← Runtime module management
  repository/      ← JDBC repositories (raw SQL, NOT JPA)
  scim/            ← SCIM 2.0 protocol (controller, model, service, filter)
  service/         ← Business logic (45+ services)
    email/         ← Email providers (SMTP, templates)
    push/          ← Push notification providers (FCM)
    sms/           ← SMS providers
```

## Key Patterns

### Models
Models are Java records with static factory methods — NOT JPA entities:
```java
public record Conversation(UUID id, String tenantId, ...) {
    public static Conversation create(String tenantId, ...) { ... }
}
```

### Repositories
Custom JDBC repositories using `JdbcTemplate` with raw SQL — NOT Spring Data JPA:
```java
@Repository
public class ApprovalRepository {
    private final JdbcTemplate jdbcTemplate;
    // Raw SQL queries via jdbcTemplate.query(), queryForObject(), update()
}
```

### BeforeSaveHook → Kafka Event Publishing
When a system collection config changes, broadcast via Kafka so all pods update:
1. Create a `BeforeSaveHook` implementation
2. In after-create/update/delete, publish to a Kafka topic
3. All pods consume the event and refresh local state

**Reference**: `CollectionConfigEventPublisher.java` (topic: `kelta.config.collection.changed`)
**Reference**: `FieldConfigEventPublisher.java` (topic: `kelta.config.field.changed`)

Never call `lifecycleManager.refreshX()` directly — that only updates the local pod.

### Kafka Listeners
```java
@KafkaListener(topics = "kelta.config.collection.changed", groupId = "kelta-worker-collections")
```
**Reference**: `CollectionSchemaListener.java`, `FlowEventListener.java`

### Flyway Migrations
Location: `src/main/resources/db/migration/`
Current range: V1–V99. Use the next sequential number.
```bash
ls src/main/resources/db/migration/ | tail -3  # Check latest version
```

### Error Handling
- `ScimException` → SCIM endpoints (400, 404, 409 with scimType)
- `EmailDeliveryException` → Email send failures
- `SmsDeliveryException` → SMS send failures
- `PushDeliveryException` → Push notification failures
- Runtime errors caught by Spring `@ExceptionHandler` in `ScimExceptionHandler`

## When Creating a New Endpoint

1. Add a `@RestController` in `controller/` — keep thin, delegate to service
2. Create or update a service class in `service/`
3. If it needs persistence, add methods to existing repository or create new `@Repository` with `JdbcTemplate`
4. Add a test: `src/test/java/io/kelta/worker/controller/MyControllerTest.java`

**Reference**: `ModuleController.java` + `ScimUserControllerTest.java`

## When Adding a BeforeSaveHook

1. Create a class in `listener/` implementing `BeforeSaveHook`
2. Specify the system collection name it hooks into
3. In after-create/update/delete, publish a Kafka event via `KafkaTemplate`
4. Create a corresponding `@KafkaListener` if other pods need to react

**Reference**: `CollectionConfigEventPublisher.java`, `ValidationRuleRefreshHook.java`

## Reference Implementations

| Pattern | File |
|---------|------|
| REST controller | `controller/ModuleController.java` |
| SCIM controller | `scim/controller/ScimUserController.java` |
| BeforeSaveHook | `listener/CollectionConfigEventPublisher.java` |
| Kafka listener | `listener/CollectionSchemaListener.java` |
| JDBC repository | `repository/ApprovalRepository.java` |
| Service with Kafka | `service/CollectionLifecycleManager.java` |
| Unit test | `scim/service/ScimUserServiceTest.java` |
| Controller test | `scim/controller/ScimUserControllerTest.java` |
| Action handler | `handler/SubmitForApprovalActionHandler.java` |

## Running Tests

```bash
mvn test                           # All tests
mvn test -Dtest=ScimUserServiceTest    # Single class
mvn test -Dtest=ScimUserServiceTest#getUserNotFound  # Single method
mvn test -Dtest="Scim*"            # Pattern match
mvn verify -Pintegration-tests     # Include integration tests
```

## Build Commands

```bash
make build     # mvn clean package -DskipTests
make test      # mvn test
make verify    # mvn verify -Pintegration-tests
make dev       # mvn spring-boot:run
make lint      # mvn checkstyle:check
make format    # mvn spotless:check
```

## Test Fixtures

Use `TestFixtures.java` in `src/test/java/io/kelta/worker/` for pre-built domain objects. Leverage `CollectionDefinitionBuilder` and `FieldDefinitionBuilder` from runtime-core for collection/field fixtures.
