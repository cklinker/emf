# Kafka Events Migration - Remaining Tasks

## Completed ✅

1. ✅ Created `emf-platform/runtime/runtime-events` module
2. ✅ Created shared event classes in `com.emf.runtime.event` package:
   - ConfigEvent<T>
   - ChangeType enum
   - CollectionChangedPayload
   - AuthzChangedPayload (with nested RoutePolicyPayload and FieldPolicyPayload)
   - ServiceChangedPayload
   - EventFactory helper
3. ✅ Built and installed runtime-events to local Maven repo
4. ✅ Added runtime-events dependency to control-plane pom.xml
5. ✅ Added runtime-events dependency to gateway pom.xml
6. ✅ Created PayloadAdapter in control-plane to convert entities to shared payloads
7. ✅ Started updating ConfigEventPublisher to use shared events

## Remaining Tasks

### 1. Complete Control Plane Migration

**File: `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ConfigEventPublisher.java`**

- [ ] Remove the old `buildEvent()` method (lines ~180-195)
- [ ] Update remaining event publishing methods (UI, OIDC) to use EventFactory
- [ ] Verify all imports are from `com.emf.runtime.event`

**Files to update imports:**
- [ ] `CollectionService.java` - Change `com.emf.controlplane.event.ChangeType` to `com.emf.runtime.event.ChangeType`
- [ ] `AuthorizationService.java` - Update imports
- [ ] `ServiceService.java` - Update imports
- [ ] `OidcProviderService.java` - Update imports
- [ ] `UiConfigService.java` - Update imports
- [ ] All test files that reference event classes

**Delete old event classes:**
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ConfigEvent.java`
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ChangeType.java`
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/CollectionChangedPayload.java`
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/AuthzChangedPayload.java`
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/ServiceChangedPayload.java`
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/UiChangedPayload.java` (if exists)
- [ ] `emf-control-plane/app/src/main/java/com/emf/controlplane/event/OidcChangedPayload.java` (if exists)

### 2. Complete Gateway Migration

**File: `emf-gateway/src/main/java/com/emf/gateway/config/KafkaConfig.java`**

- [ ] Remove the type mapping code (lines with `typeMapping.put(...)`)
- [ ] Simplify to just trust `com.emf.runtime.event` package
- [ ] Update imports to use `com.emf.runtime.event.*`

**File: `emf-gateway/src/main/java/com/emf/gateway/listener/ConfigEventListener.java`**

- [ ] Update imports from `com.emf.gateway.event.*` to `com.emf.runtime.event.*`

**Delete old event classes:**
- [ ] `emf-gateway/src/main/java/com/emf/gateway/event/ConfigEvent.java`
- [ ] `emf-gateway/src/main/java/com/emf/gateway/event/ChangeType.java`
- [ ] `emf-gateway/src/main/java/com/emf/gateway/event/CollectionChangedPayload.java`
- [ ] `emf-gateway/src/main/java/com/emf/gateway/event/AuthzChangedPayload.java`
- [ ] `emf-gateway/src/main/java/com/emf/gateway/event/ServiceChangedPayload.java`

### 3. Build and Test

```bash
# Build control plane
cd emf-control-plane/app
mvn clean package -DskipTests

# Build gateway
cd emf-gateway
mvn clean package -DskipTests

# Run tests
cd emf-control-plane/app
mvn test

cd emf-gateway
mvn test
```

### 4. Integration Testing

```bash
# Restart services with new code
docker-compose up -d --build emf-control-plane emf-gateway

# Monitor logs for Kafka errors
docker-compose logs -f emf-gateway | grep -i "kafka\|deserializ"

# Test collection creation (should publish event)
curl -X POST http://localhost:8080/api/collections \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"test","serviceId":"..."}'

# Verify gateway receives and processes event
docker-compose logs emf-gateway | grep "Received collection changed event"
```

### 5. Update Sample Service (if it uses events)

If sample-service also consumes these events:

- [ ] Add runtime-events dependency to sample-service pom.xml
- [ ] Update imports in `CacheEventListener.java`
- [ ] Remove duplicate event classes

### 6. Documentation

- [ ] Update architecture diagrams to show shared events module
- [ ] Document event schemas in emf-docs
- [ ] Add migration notes to CHANGELOG

## Quick Commands

### Remove old control-plane event classes:
```bash
cd emf-control-plane/app/src/main/java/com/emf/controlplane/event
rm ConfigEvent.java ChangeType.java CollectionChangedPayload.java \
   AuthzChangedPayload.java ServiceChangedPayload.java \
   UiChangedPayload.java OidcChangedPayload.java 2>/dev/null || true
```

### Remove old gateway event classes:
```bash
cd emf-gateway/src/main/java/com/emf/gateway/event
rm ConfigEvent.java ChangeType.java CollectionChangedPayload.java \
   AuthzChangedPayload.java ServiceChangedPayload.java 2>/dev/null || true
```

### Find all files that need import updates:
```bash
# Control plane
grep -r "com.emf.controlplane.event" emf-control-plane/app/src --include="*.java"

# Gateway
grep -r "com.emf.gateway.event" emf-gateway/src --include="*.java"
```

## Verification Checklist

After completing migration:

- [ ] No compilation errors in control-plane
- [ ] No compilation errors in gateway
- [ ] All tests pass in control-plane
- [ ] All tests pass in gateway
- [ ] Gateway logs show no Kafka deserialization errors
- [ ] Events are successfully published and consumed
- [ ] Correlation IDs are preserved in events
- [ ] Integration tests pass

## Rollback Plan

If issues occur:

1. Revert pom.xml changes in both services
2. Restore old event classes from git:
   ```bash
   git checkout HEAD -- emf-control-plane/app/src/main/java/com/emf/controlplane/event/
   git checkout HEAD -- emf-gateway/src/main/java/com/emf/gateway/event/
   ```
3. Rebuild and redeploy services

## Benefits After Migration

1. ✅ No more Kafka deserialization errors
2. ✅ Single source of truth for event schemas
3. ✅ Easier to add new services that consume events
4. ✅ Version control for event schema changes
5. ✅ Compile-time type safety across all services
