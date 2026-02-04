# Integration with EMF Platform Runtime Core

## Overview

The local integration testing spec has been updated to use the **emf-platform/runtime-core** library for implementing the sample service. This provides a more realistic test environment that uses the actual EMF platform collection management system rather than custom JPA entities.

## Key Changes

### 1. Sample Service Architecture

**Before**: Custom JPA entities (Project, Task) with Spring Data repositories and custom controllers.

**After**: EMF runtime-core collections defined using `CollectionDefinition` and `FieldDefinition` builders, with automatic REST API via `DynamicCollectionRouter`.

### 2. Collection Definitions

The sample service now defines collections using the EMF runtime-core API:

```java
CollectionDefinition projects = new CollectionDefinitionBuilder()
    .name("projects")
    .displayName("Projects")
    .description("Project management collection")
    .addField(FieldDefinition.requiredString("name"))
    .addField(FieldDefinition.string("description"))
    .addField(FieldDefinition.enumField("status", 
        List.of("PLANNING", "ACTIVE", "COMPLETED", "ARCHIVED")))
    .storageConfig(new StorageConfig(StorageMode.PHYSICAL_TABLES, null))
    .apiConfig(new ApiConfig(true, true, true, true, true, "/api/collections/projects"))
    .build();
```

### 3. Automatic Table Creation

The runtime-core `StorageAdapter` automatically creates database tables based on collection definitions. No manual SQL schema creation is needed - the `initializeCollection()` method generates appropriate CREATE TABLE statements.

### 4. Automatic REST API

The `DynamicCollectionRouter` from runtime-core automatically provides REST endpoints for all registered collections:

- `GET /api/collections/{collectionName}` - List with pagination, sorting, filtering
- `GET /api/collections/{collectionName}/{id}` - Get single resource
- `POST /api/collections/{collectionName}` - Create resource
- `PUT /api/collections/{collectionName}/{id}` - Update resource
- `DELETE /api/collections/{collectionName}/{id}` - Delete resource

No custom controllers needed for basic CRUD operations.

### 5. Enhanced Router for Include Support

The sample service extends `DynamicCollectionRouter` to add JSON:API include parameter support:

```java
@RestController
@RequestMapping("/api/collections")
public class EnhancedCollectionRouter extends DynamicCollectionRouter {
    // Adds include parameter processing to GET /{id} endpoint
}
```

### 6. Event-Driven Caching

The sample service uses `CollectionEvent` from runtime-core to automatically cache resources in Redis:

```java
@Component
public class CacheEventListener implements CollectionChangeListener {
    @EventListener
    public void onCollectionEvent(CollectionEvent event) {
        // Cache on CREATE/UPDATE, invalidate on DELETE
    }
}
```

## Benefits

1. **Realistic Testing**: Tests the actual EMF platform collection system, not a mock implementation
2. **Less Code**: No need for JPA entities, repositories, or custom controllers
3. **Automatic Features**: Validation, pagination, sorting, filtering all provided by runtime-core
4. **Consistent Behavior**: Sample service behaves exactly like real EMF-based services
5. **Better Coverage**: Tests validate the runtime-core library itself

## Dependencies

The sample service now depends on:

```xml
<dependency>
    <groupId>com.emf</groupId>
    <artifactId>runtime-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This brings in:
- Spring Boot starters (web, validation, actuator, jdbc)
- PostgreSQL JDBC driver
- Spring Kafka
- Spring Data Redis
- Jackson for JSON
- Collection registry and storage adapters
- Query engine and validation engine
- Dynamic router and exception handling

## Testing Implications

### What Gets Tested

1. **Collection Definition**: Defining collections with fields, types, validation rules, relationships
2. **Storage Adapter**: Automatic table creation, CRUD operations, query execution
3. **Query Engine**: Pagination, sorting, filtering, field selection
4. **Validation Engine**: Required fields, type validation, unique constraints, enum values
5. **Dynamic Router**: REST API generation, request routing, response formatting
6. **Event Publishing**: Kafka events for collection lifecycle
7. **Registry**: Thread-safe collection registration and lookup

### Test Data Helpers

Test helpers now work with the runtime-core API:

```java
public String createProject(String name, String description, String status) {
    // POST to /api/collections/projects
    // Runtime-core handles validation, storage, events
}
```

### Property-Based Tests

Property tests validate universal behaviors of the runtime-core library:

- **Property 1**: All responses follow JSON:API format (validated by DynamicCollectionRouter)
- **Property 2**: Resources are cached correctly (validated by event listeners)
- **Property 3**: Required fields are validated (validated by ValidationEngine)
- **Property 21**: CRUD persistence works correctly (validated by StorageAdapter)

## Migration Path

The spec has been updated with:

1. **Design Document**: Updated Sample Service section with runtime-core architecture
2. **Tasks**: Updated task 4 (Implement Sample Service) with new subtasks
3. **Database Init**: Simplified to just enable extensions, tables created automatically

## Next Steps

When implementing the sample service:

1. Add runtime-core dependency to pom.xml
2. Create `CollectionInitializer` component to define and register collections
3. Create `ResourceCacheService` for Redis caching
4. Create `CacheEventListener` to cache resources on events
5. Create `EnhancedCollectionRouter` to add include parameter support
6. Configure `emf.storage.mode=PHYSICAL_TABLES` in application.yml
7. Let runtime-core auto-configuration handle the rest

The runtime-core library provides all the core functionality - the sample service just needs to define collections and add caching/include support.
