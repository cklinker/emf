# Kafka Events - Quick Fix Guide

This is the fastest path to fix the Kafka deserialization errors.

## The Problem

Gateway logs show:
```
ClassNotFoundException: com.emf.controlplane.event.ConfigEvent
```

## The Fix (3 Steps)

### Step 1: Update Gateway KafkaConfig (2 minutes)

**File:** `emf-gateway/src/main/java/com/emf/gateway/config/KafkaConfig.java`

**Replace the imports:**
```java
// OLD
import com.emf.gateway.event.AuthzChangedPayload;
import com.emf.gateway.event.CollectionChangedPayload;
import com.emf.gateway.event.ConfigEvent;
import com.emf.gateway.event.ServiceChangedPayload;

// NEW
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.ServiceChangedPayload;
```

**Simplify the consumerFactory() method:**
```java
@Bean
public ConsumerFactory<String, ConfigEvent<?>> consumerFactory() {
    Map<String, Object> config = new HashMap<>();
    
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    
    // Configure JSON deserializer to trust the shared event package
    JsonDeserializer<ConfigEvent<?>> deserializer = new JsonDeserializer<>();
    deserializer.addTrustedPackages("com.emf.runtime.event");
    
    return new DefaultKafkaConsumerFactory<>(
        config,
        new StringDeserializer(),
        deserializer
    );
}
```

**Remove all the type mapping code** (the lines with `typeMapping.put(...)` and `deserializer.typeMapper()...`)

### Step 2: Update Gateway ConfigEventListener (1 minute)

**File:** `emf-gateway/src/main/java/com/emf/gateway/listener/ConfigEventListener.java`

**Replace the imports:**
```java
// OLD
import com.emf.gateway.event.AuthzChangedPayload;
import com.emf.gateway.event.ChangeType;
import com.emf.gateway.event.CollectionChangedPayload;
import com.emf.gateway.event.ConfigEvent;
import com.emf.gateway.event.ServiceChangedPayload;

// NEW
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.ServiceChangedPayload;
```

### Step 3: Rebuild and Restart (2 minutes)

```bash
# Rebuild gateway
cd emf-gateway
mvn clean package -DskipTests

# Restart gateway
docker-compose up -d --build emf-gateway

# Check logs - should see no more ClassNotFoundException
docker-compose logs -f emf-gateway
```

## Verification

After restart, you should see:
- ✅ No `ClassNotFoundException` errors
- ✅ Gateway starts successfully
- ✅ Events are consumed without errors

Look for log messages like:
```
Received collection changed event: eventId=..., correlationId=...
```

## If It Still Fails

1. Check that runtime-events is installed:
   ```bash
   ls ~/.m2/repository/com/emf/runtime-events/1.0.0-SNAPSHOT/
   ```

2. Rebuild runtime-events if needed:
   ```bash
   cd emf-platform/runtime/runtime-events
   mvn clean install
   ```

3. Check gateway pom.xml has the dependency:
   ```xml
   <dependency>
       <groupId>com.emf</groupId>
       <artifactId>runtime-events</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

## What About Control Plane?

Control plane also needs to be updated to use the shared events, but it's not causing the immediate error. The gateway fix above will stop the deserialization errors.

For complete migration of control plane, see `KAFKA_EVENTS_MIGRATION_TODO.md`.

## Why This Works

Before:
- Control plane publishes events with type info: `com.emf.controlplane.event.ConfigEvent`
- Gateway tries to deserialize but only has `com.emf.gateway.event.ConfigEvent`
- Jackson can't find the class → ClassNotFoundException

After:
- Both services use the same shared class: `com.emf.runtime.event.ConfigEvent`
- Jackson deserializes successfully because the class exists in gateway's classpath
- No type mapping needed because the classes are identical

## Time to Fix

- **Immediate fix (gateway only):** ~5 minutes
- **Complete migration (both services):** ~30 minutes

Start with the immediate fix above to stop the errors, then complete the full migration when convenient.
