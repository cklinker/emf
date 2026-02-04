# Kafka Events Deserialization Error - Solution Summary

## Problem

The gateway was experiencing continuous Kafka deserialization errors:

```
ClassNotFoundException: com.emf.controlplane.event.ConfigEvent
```

**Root Cause:**
- Control plane published Kafka events with Jackson type information pointing to `com.emf.controlplane.event.*` classes
- Gateway had duplicate event classes at `com.emf.gateway.event.*`
- When Jackson tried to deserialize, it looked for the control plane classes which didn't exist in gateway's classpath
- Type mapping workaround was attempted but is fragile and error-prone

## Solution: Shared Event Library

Created a new shared module in emf-platform that all services depend on:

### Architecture

```
emf-platform/runtime/runtime-events
├── com.emf.runtime.event
│   ├── ConfigEvent<T>              # Base event wrapper
│   ├── ChangeType                  # CREATED/UPDATED/DELETED enum
│   ├── CollectionChangedPayload    # Collection events
│   ├── AuthzChangedPayload         # Authorization events
│   ├── ServiceChangedPayload       # Service events
│   └── EventFactory                # Helper for creating events
```

**Published as:** `com.emf:runtime-events:1.0.0-SNAPSHOT`

### Benefits

1. **Single Source of Truth** - Event schemas defined once
2. **No Deserialization Errors** - All services use identical classes
3. **Type Safety** - Compile-time checking across services
4. **Version Control** - Schema changes versioned with platform
5. **Easier Onboarding** - New services just add one dependency

## Implementation Status

### ✅ Completed

1. Created `runtime-events` module in emf-platform
2. Implemented all shared event classes
3. Built and installed to local Maven repository
4. Added dependencies to control-plane and gateway pom.xml
5. Created `PayloadAdapter` in control-plane for entity-to-payload conversion
6. Created migration documentation and scripts

### 🔄 In Progress

The following manual steps are required to complete the migration:

#### Control Plane
- Update all imports from `com.emf.controlplane.event.*` to `com.emf.runtime.event.*`
- Update `ConfigEventPublisher` to use `EventFactory` and `PayloadAdapter`
- Update service classes (CollectionService, AuthorizationService, etc.)
- Delete old event classes after verification

#### Gateway
- Update all imports from `com.emf.gateway.event.*` to `com.emf.runtime.event.*`
- Simplify `KafkaConfig` (remove type mapping - no longer needed!)
- Update `ConfigEventListener` imports
- Delete old event classes after verification

## Migration Guide

See detailed instructions in:
- **KAFKA_EVENTS_MIGRATION_GUIDE.md** - Complete migration guide
- **KAFKA_EVENTS_MIGRATION_TODO.md** - Task checklist
- **scripts/migrate-kafka-events.sh** - Helper script

## Quick Start

```bash
# 1. Run migration helper script
./scripts/migrate-kafka-events.sh

# 2. Update imports in control-plane
# Change: import com.emf.controlplane.event.*;
# To:     import com.emf.runtime.event.*;

# 3. Update imports in gateway
# Change: import com.emf.gateway.event.*;
# To:     import com.emf.runtime.event.*;

# 4. Build services
cd emf-control-plane/app && mvn clean package -DskipTests
cd emf-gateway && mvn clean package -DskipTests

# 5. Restart services
docker-compose up -d --build emf-control-plane emf-gateway

# 6. Verify no errors
docker-compose logs -f emf-gateway | grep -i "kafka\|error"
```

## Testing

After migration, verify:

1. ✅ No compilation errors
2. ✅ No Kafka deserialization errors in logs
3. ✅ Events are published successfully
4. ✅ Events are consumed successfully
5. ✅ Correlation IDs are preserved
6. ✅ Integration tests pass

## Files Created

### emf-platform
- `runtime/runtime-events/pom.xml`
- `runtime/runtime-events/src/main/java/com/emf/runtime/event/ConfigEvent.java`
- `runtime/runtime-events/src/main/java/com/emf/runtime/event/ChangeType.java`
- `runtime/runtime-events/src/main/java/com/emf/runtime/event/CollectionChangedPayload.java`
- `runtime/runtime-events/src/main/java/com/emf/runtime/event/AuthzChangedPayload.java`
- `runtime/runtime-events/src/main/java/com/emf/runtime/event/ServiceChangedPayload.java`
- `runtime/runtime-events/src/main/java/com/emf/runtime/event/EventFactory.java`

### emf-control-plane
- `app/src/main/java/com/emf/controlplane/event/PayloadAdapter.java` (adapter for entity conversion)

### Documentation
- `KAFKA_EVENTS_MIGRATION_GUIDE.md`
- `KAFKA_EVENTS_MIGRATION_TODO.md`
- `KAFKA_EVENTS_SOLUTION_SUMMARY.md` (this file)
- `scripts/migrate-kafka-events.sh`

## Dependencies Updated

### emf-control-plane/app/pom.xml
```xml
<dependency>
    <groupId>com.emf</groupId>
    <artifactId>runtime-events</artifactId>
    <version>${emf-platform.version}</version>
</dependency>
```

### emf-gateway/pom.xml
```xml
<dependency>
    <groupId>com.emf</groupId>
    <artifactId>runtime-events</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Future Services

Any new EMF service that needs to publish or consume configuration events should:

1. Add dependency on `com.emf:runtime-events`
2. Import from `com.emf.runtime.event`
3. Use `EventFactory.createEvent()` to publish events
4. Configure Kafka deserializer to trust `com.emf.runtime.event` package

## Example Usage

### Publishing Events (Control Plane)

```java
import com.emf.runtime.event.*;

// Create payload using adapter
CollectionChangedPayload payload = PayloadAdapter.toCollectionPayload(collection, ChangeType.CREATED);

// Create event with metadata
ConfigEvent<CollectionChangedPayload> event = EventFactory.createEvent(
    "emf.config.collection.changed",
    correlationId,
    payload
);

// Publish to Kafka
kafkaTemplate.send(topic, collectionId, event);
```

### Consuming Events (Gateway)

```java
import com.emf.runtime.event.*;

@KafkaListener(topics = "collection-changed")
public void handleCollectionChanged(ConfigEvent<CollectionChangedPayload> event) {
    CollectionChangedPayload payload = event.getPayload();
    // Process event...
}
```

## Rollback Plan

If issues occur:

1. Revert pom.xml changes
2. Restore old event classes from git
3. Rebuild and redeploy

```bash
git checkout HEAD -- emf-control-plane/app/pom.xml
git checkout HEAD -- emf-gateway/pom.xml
git checkout HEAD -- emf-control-plane/app/src/main/java/com/emf/controlplane/event/
git checkout HEAD -- emf-gateway/src/main/java/com/emf/gateway/event/
```

## Next Steps

1. Complete import updates in control-plane and gateway
2. Test locally with docker-compose
3. Run integration test suite
4. Deploy to dev environment
5. Monitor for any Kafka errors
6. Delete old event classes once verified
7. Update documentation and architecture diagrams

## Support

For questions or issues:
- Review migration guide: `KAFKA_EVENTS_MIGRATION_GUIDE.md`
- Check task list: `KAFKA_EVENTS_MIGRATION_TODO.md`
- Run helper script: `./scripts/migrate-kafka-events.sh`
