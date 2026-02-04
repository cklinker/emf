# Service Architecture Implementation

## Overview

This document describes the implementation of the multi-service architecture for the EMF platform. This addresses the critical issue where Physical Table collections were not creating actual database tables because there was no way to identify which domain service should execute the DDL.

## Problem Statement

When creating a Physical Table collection through the Control Plane UI:
1. ✅ Collection metadata was saved to the Control Plane database
2. ✅ Kafka event was published (`config.collection.changed`)
3. ❌ **No physical table was created** because:
   - No domain service was running to consume the event
   - Even if a service was running, it couldn't determine if it owned the collection
   - No `serviceId` field existed to route DDL execution

## Architecture

### Before (Broken)
```
UI → Control Plane → Kafka Event → ??? (Which service should create the table?)
     (metadata only)                (No ownership information)
```

### After (Fixed)
```
UI → Control Plane → Kafka Event → Domain Service (filtered by serviceId)
     (metadata + serviceId)         (executes CREATE TABLE in its own DB)
```

## Implementation Details

### 1. Service Entity

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/entity/Service.java`

Represents a domain service (microservice) that hosts collections:

```java
@Entity
@Table(name = "service")
public class Service extends BaseEntity {
    private String name;              // Unique service identifier
    private String displayName;       // Human-readable name
    private String description;       // Service description
    private String basePath;          // API base path (e.g., "/api")
    private String environment;       // dev/qa/stage/prod
    private String databaseUrl;       // Service's database connection
    private boolean active;           // Soft delete flag
    private List<Collection> collections; // Collections owned by this service
}
```

**Key Features**:
- Each service has its own database (identified by `databaseUrl`)
- Services can host multiple collections
- Soft delete support (active flag)
- Environment tracking for multi-environment deployments

### 2. Collection Entity Updates

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/entity/Collection.java`

**Changes**:
- Added `@ManyToOne` relationship to `Service`
- Added `service_id` foreign key column
- Updated constructor to require `Service` parameter

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "service_id", nullable = false)
private Service service;
```

**Impact**: Every collection now has a clear owner (domain service).

### 3. DTOs and Request Objects

**Files**:
- `ServiceDto.java` - Response DTO for Service API
- `CreateServiceRequest.java` - Request to create a new service
- `UpdateServiceRequest.java` - Request to update a service
- `CollectionDto.java` - Updated to include `serviceId` and `serviceName`
- `CreateCollectionRequest.java` - Updated to require `serviceId`

**Key Changes**:
- `CreateCollectionRequest` now requires `serviceId` (validated as `@NotBlank`)
- `CollectionDto` includes service information for API responses
- Service name validation: lowercase letters, numbers, and hyphens only

### 4. Repository Layer

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/repository/ServiceRepository.java`

Standard JPA repository with:
- Find active services
- Search by name or description
- Check existence by name
- Pagination support

### 5. Service Layer

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/service/ServiceService.java`

Business logic for service management:
- CRUD operations with validation
- Duplicate name checking
- Event publishing on changes
- Soft delete support

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/service/CollectionService.java`

**Changes**:
- Injected `ServiceRepository`
- Updated `createCollection()` to:
  - Verify service exists
  - Associate collection with service
  - Include service info in events

### 6. REST Controller

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/controller/ServiceController.java`

New REST endpoints:
- `GET /control/services` - List all services (paginated, filterable)
- `POST /control/services` - Create a new service
- `GET /control/services/{id}` - Get service by ID
- `PUT /control/services/{id}` - Update service
- `DELETE /control/services/{id}` - Soft-delete service

**Security**: All endpoints require `ADMIN` role.

### 7. Event Publishing

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ConfigEventPublisher.java`

**New Event**: `config.service.changed`

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ServiceChangedPayload.java`

Payload includes:
- Service ID, name, display name
- Base path, environment, database URL
- Change type (CREATED, UPDATED, DELETED)
- Timestamp

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/event/CollectionChangedPayload.java`

**Enhanced with**:
- `serviceId` - Which service owns this collection
- `serviceName` - Service name for logging/debugging
- `displayName` - Collection display name
- `storageMode` - PHYSICAL_TABLE or JSONB

**Critical**: Domain services can now filter Kafka events by `serviceId` to only process collections they own.

### 8. Kafka Configuration

**File**: `emf-control-plane/app/src/main/java/com/emf/controlplane/config/ControlPlaneProperties.java`

**New Topic**: `config.service.changed` (default topic name)

Configuration:
```yaml
emf:
  control-plane:
    kafka:
      enabled: true
      topics:
        service-changed: config.service.changed
        collection-changed: config.collection.changed
        # ... other topics
```

### 9. Database Migration

**File**: `emf-control-plane/app/src/main/resources/db/migration/V003__add_service_table.sql`

**Changes**:
1. Creates `service` table with all required columns
2. Creates indexes on `name` and `environment`
3. Creates a default service (`default-service`) for existing collections
4. Adds `service_id` column to `collection` table
5. Backfills existing collections with default service ID
6. Adds foreign key constraint
7. Creates indexes for performance

**Migration Strategy**:
- Zero downtime: Adds column as nullable first
- Backfills data before making NOT NULL
- Creates default service to avoid breaking existing collections

## How It Works

### Creating a Service

1. Admin creates a service via UI or API:
```json
POST /control/services
{
  "name": "customer-service",
  "displayName": "Customer Service",
  "description": "Manages customer data",
  "basePath": "/api",
  "environment": "production",
  "databaseUrl": "jdbc:postgresql://customer-db:5432/customers"
}
```

2. Control Plane:
   - Validates the request
   - Creates service entity
   - Publishes `config.service.changed` event to Kafka

3. Domain services can subscribe to service events to register themselves

### Creating a Collection with Service Ownership

1. Admin creates a collection via UI:
```json
POST /control/collections
{
  "serviceId": "abc-123-def",
  "name": "customers",
  "description": "Customer records"
}
```

2. Control Plane:
   - Verifies service exists
   - Creates collection linked to service
   - Publishes `config.collection.changed` event with:
     - `serviceId`: "abc-123-def"
     - `serviceName`: "customer-service"
     - `storageMode`: "PHYSICAL_TABLE"

3. Domain Service (customer-service):
   - Subscribes to `config.collection.changed` topic
   - Filters events: `event.serviceId == myServiceId`
   - Receives event for "customers" collection
   - Uses `SchemaMigrationEngine` from `emf-platform` runtime
   - Executes `CREATE TABLE customers (...)` in its own database

### Event Filtering Pattern

Domain services should filter events like this:

```java
@KafkaListener(topics = "config.collection.changed")
public void handleCollectionChanged(ConfigEvent<CollectionChangedPayload> event) {
    CollectionChangedPayload payload = event.getPayload();
    
    // Only process collections owned by this service
    if (!payload.getServiceId().equals(myServiceId)) {
        return; // Ignore - not our collection
    }
    
    // This collection belongs to us - execute DDL
    if ("PHYSICAL_TABLE".equals(payload.getStorageMode())) {
        schemaMigrationEngine.createTable(payload);
    }
}
```

## Benefits

### 1. Clear Ownership
- Each collection has exactly one owner (domain service)
- No ambiguity about which service should create tables
- Supports multi-service deployments

### 2. Database Isolation
- Each service has its own database
- Collections are created in the correct database
- Supports microservice architecture patterns

### 3. Event Routing
- Services can filter Kafka events by `serviceId`
- Reduces unnecessary event processing
- Enables selective consumption

### 4. Multi-Environment Support
- Services can be tagged with environment (dev/qa/prod)
- Supports environment-specific configurations
- Enables proper promotion workflows

### 5. Scalability
- Multiple domain services can coexist
- Each service independently manages its collections
- Horizontal scaling per service

## Migration Path

### For Existing Deployments

1. **Apply Database Migration**: V003 creates service table and backfills data
2. **Default Service**: All existing collections are assigned to "default-service"
3. **No Breaking Changes**: Existing collections continue to work
4. **Gradual Migration**: Create new services and reassign collections as needed

### For New Deployments

1. **Create Services First**: Define domain services before collections
2. **Assign Collections**: Always specify `serviceId` when creating collections
3. **Deploy Domain Services**: Each service subscribes to Kafka and executes DDL

## Testing

### Manual Testing

1. **Create a Service**:
```bash
curl -X POST http://localhost:8080/control/services \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "test-service",
    "displayName": "Test Service",
    "description": "Test domain service",
    "environment": "development"
  }'
```

2. **Create a Collection**:
```bash
curl -X POST http://localhost:8080/control/collections \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "serviceId": "<service-id-from-step-1>",
    "name": "test-collection",
    "description": "Test collection"
  }'
```

3. **Verify Kafka Event**: Check that event includes `serviceId` and `serviceName`

### Integration Testing

Domain services should:
1. Subscribe to `config.collection.changed` topic
2. Filter by their `serviceId`
3. Execute DDL for Physical Table collections
4. Verify table creation in their database

## Next Steps

### Immediate
1. ✅ Service entity and repository created
2. ✅ Collection updated with service relationship
3. ✅ REST API endpoints implemented
4. ✅ Kafka events enhanced with service info
5. ✅ Database migration created
6. ⏭️ Update UI to select service when creating collections
7. ⏭️ Update SDK types to include service fields
8. ⏭️ Create example domain service that consumes events

### Short-term
1. Add service health monitoring
2. Implement service discovery mechanism
3. Add service-to-service authentication
4. Create service deployment templates (Helm charts)

### Long-term
1. Implement service mesh integration
2. Add cross-service collection references
3. Implement distributed transactions
4. Add service-level metrics and monitoring

## API Reference

### Service Endpoints

#### List Services
```
GET /control/services?filter=customer&page=0&size=20
Authorization: Bearer <token>
```

#### Create Service
```
POST /control/services
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "customer-service",
  "displayName": "Customer Service",
  "description": "Manages customer data",
  "basePath": "/api",
  "environment": "production",
  "databaseUrl": "jdbc:postgresql://db:5432/customers"
}
```

#### Get Service
```
GET /control/services/{id}
Authorization: Bearer <token>
```

#### Update Service
```
PUT /control/services/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "displayName": "Updated Customer Service",
  "description": "Updated description"
}
```

#### Delete Service
```
DELETE /control/services/{id}
Authorization: Bearer <token>
```

### Collection Endpoints (Updated)

#### Create Collection (Now Requires serviceId)
```
POST /control/collections
Authorization: Bearer <token>
Content-Type: application/json

{
  "serviceId": "abc-123-def",
  "name": "customers",
  "description": "Customer records"
}
```

## Kafka Events

### config.service.changed

```json
{
  "eventId": "evt-123",
  "eventType": "config.service.changed",
  "correlationId": "req-456",
  "timestamp": "2026-01-26T12:00:00Z",
  "payload": {
    "serviceId": "svc-789",
    "serviceName": "customer-service",
    "displayName": "Customer Service",
    "description": "Manages customer data",
    "basePath": "/api",
    "environment": "production",
    "databaseUrl": "jdbc:postgresql://db:5432/customers",
    "active": true,
    "changeType": "CREATED",
    "timestamp": "2026-01-26T12:00:00Z"
  }
}
```

### config.collection.changed (Enhanced)

```json
{
  "eventId": "evt-123",
  "eventType": "config.collection.changed",
  "correlationId": "req-456",
  "timestamp": "2026-01-26T12:00:00Z",
  "payload": {
    "id": "col-789",
    "serviceId": "svc-789",
    "serviceName": "customer-service",
    "name": "customers",
    "displayName": "Customers",
    "description": "Customer records",
    "storageMode": "PHYSICAL_TABLE",
    "active": true,
    "currentVersion": 1,
    "fields": [...],
    "changeType": "CREATED",
    "createdAt": "2026-01-26T12:00:00Z",
    "updatedAt": "2026-01-26T12:00:00Z"
  }
}
```

## Conclusion

This implementation solves the critical architectural gap where collections had no clear ownership. With the Service entity and proper event routing, domain services can now:

1. **Identify their collections** via `serviceId`
2. **Execute DDL in the correct database** using the service's `databaseUrl`
3. **Scale independently** with clear boundaries
4. **Support multi-tenant deployments** with service-level isolation

The architecture now properly supports the "without restart" requirement by enabling domain services to dynamically consume configuration changes and execute DDL online.

---

**Implementation Date**: January 26, 2026  
**Status**: ✅ COMPLETE (Backend)  
**Next**: UI and SDK updates required
