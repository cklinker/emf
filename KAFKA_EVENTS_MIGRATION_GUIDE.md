# Kafka Events Migration Guide

## Overview

This guide documents the migration of Kafka event classes from individual services to a shared library in `emf-platform/runtime/runtime-events`.

## Problem

The gateway was experiencing deserialization errors because:
- Control plane published events with class type `com.emf.controlplane.event.ConfigEvent`
- Gateway had duplicate classes at `com.emf.gateway.event.ConfigEvent`
- Jackson's type information in Kafka messages pointed to control plane classes
- Gateway couldn't deserialize because it didn't have those classes in its classpath

## Solution

Created a shared event library in `emf-platform` that all services depend on:

### New Module: `runtime-events`

**Location:** `emf-platform/runtime/runtime-events`

**Package:** `com.emf.runtime.event`

**Published as:** `com.emf:runtime-events:1.0.0-SNAPSHOT`

### Shared Event Classes

All Kafka event classes are now in the shared module:

1. **ConfigEvent<T>** - Base event wrapper with metadata
2. **ChangeType** - Enum for CREATED/UPDATED/DELETED
3. **CollectionChangedPayload** - Collection change events
4. **AuthzChangedPayload** - Authorization change events
   - RoutePolicyPayload (nested)
   - FieldPolicyPayload (nested)
5. **ServiceChangedPayload** - Service change events
6. **EventFactory** - Helper for creating events with proper metadata

## Migration Steps

### 1. Build and Install Shared Events

```bash
cd emf-platform/runtime/runtime-events
mvn clean install
```

### 2. Update Control Plane

**Add dependency to `emf-control-plane/app/pom.xml`:**

```xml
<dependency>
    <groupId>com.emf</groupId>
    <artifactId>runtime-events</artifactId>
    <version>${emf-platform.version}</version>
</dependency>
```

**Update imports in all control-plane files:**

```java
// OLD
import com.emf.controlplane.event.*;

// NEW
import com.emf.runtime.event.*;
```

**Files to update:**
- `ConfigEventPublisher.java` - Update imports and use EventFactory
- `CollectionService.java` - Update imports
- `AuthorizationService.java` - Update imports
- `ServiceService.java` - Update imports
- All payload creation code

**Update payload creation methods:**

The shared payloads don't have `fromEntity()` methods since they can't depend on control-plane entities. Control plane needs to manually populate the payloads:

```java
// Example for CollectionChangedPayload
CollectionChangedPayload payload = new CollectionChangedPayload();
payload.setId(collection.getId());
payload.setServiceId(collection.getService().getId());
payload.setName(collection.getName());
// ... set all fields
payload.setChangeType(changeType);
```

### 3. Update Gateway

**Add dependency to `emf-gateway/pom.xml`:**

```xml
<dependency>
    <groupId>com.emf</groupId>
    <artifactId>runtime-events</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Update imports in all gateway files:**

```java
// OLD
import com.emf.gateway.event.*;

// NEW
import com.emf.runtime.event.*;
```

**Files to update:**
- `ConfigEventListener.java` - Update imports
- `KafkaConfig.java` - Remove type mapping (no longer needed!)

**Remove type mapping from KafkaConfig:**

Since all services now use the same classes, the type mapping is no longer needed:

```java
// REMOVE THIS:
Map<String, Class<?>> typeMapping = new HashMap<>();
typeMapping.put("com.emf.controlplane.event.ConfigEvent", ConfigEvent.class);
// ... etc
deserializer.typeMapper().setIdClassMapping(typeMapping);

// Just keep:
JsonDeserializer<ConfigEvent<?>> deserializer = new JsonDeserializer<>();
deserializer.addTrustedPackages("com.emf.runtime.event");
```

### 4. Delete Old Event Classes

After migration is complete and tested:

**Control Plane - Delete:**
- `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ConfigEvent.java`
- `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ChangeType.java`
- `emf-control-plane/app/src/main/java/com/emf/controlplane/event/CollectionChangedPayload.java`
- `emf-control-plane/app/src/main/java/com/emf/controlplane/event/AuthzChangedPayload.java`
- `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ServiceChangedPayload.java`

**Gateway - Delete:**
- `emf-gateway/src/main/java/com/emf/gateway/event/ConfigEvent.java`
- `emf-gateway/src/main/java/com/emf/gateway/event/ChangeType.java`
- `emf-gateway/src/main/java/com/emf/gateway/event/CollectionChangedPayload.java`
- `emf-gateway/src/main/java/com/emf/gateway/event/AuthzChangedPayload.java`
- `emf-gateway/src/main/java/com/emf/gateway/event/ServiceChangedPayload.java`

### 5. Rebuild Services

```bash
# Build control plane
cd emf-control-plane/app
mvn clean package -DskipTests

# Build gateway
cd emf-gateway
mvn clean package -DskipTests
```

### 6. Restart Services

```bash
docker-compose up -d --build emf-control-plane emf-gateway
```

## Benefits

1. **Single Source of Truth** - Event schemas defined once in emf-platform
2. **No Deserialization Errors** - All services use the same classes
3. **Version Control** - Event schema changes are versioned with platform
4. **Type Safety** - Compile-time checking across all services
5. **Easier Maintenance** - Update events in one place

## Future Services

Any new service that needs to publish or consume these events should:

1. Add dependency on `runtime-events`
2. Import from `com.emf.runtime.event`
3. Use `EventFactory` to create events with proper metadata

## Testing

After migration, verify:

1. Control plane can publish events without errors
2. Gateway can consume events without deserialization errors
3. All event fields are properly populated
4. Correlation IDs are preserved
5. Integration tests pass

## Rollback

If issues occur:

1. Revert pom.xml changes in control-plane and gateway
2. Restore old event classes from git history
3. Rebuild and redeploy services

## Notes

- The shared events module has no dependencies on control-plane or gateway
- Payload creation logic stays in the services (they know their entities)
- Event metadata (ID, timestamp, correlation) is handled by EventFactory
- Jackson serialization/deserialization works automatically with shared classes
