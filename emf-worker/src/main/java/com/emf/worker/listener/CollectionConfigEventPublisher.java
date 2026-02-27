package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.EventFactory;
import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.BeforeSaveResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * Before-save hook for the "collections" system collection that publishes
 * config change events to Kafka after collection create/update/delete.
 *
 * <p>This hook bridges the gap between the CRUD API (which creates/updates
 * collection metadata in the database) and the Kafka-based notification system
 * that triggers schema changes on workers and route updates on the gateway.
 *
 * <p>Events are published to the {@code emf.config.collection.changed} topic,
 * consumed by:
 * <ul>
 *   <li>{@link CollectionSchemaListener} — triggers table creation/migration on workers</li>
 *   <li>Gateway ConfigEventListener — updates route registry</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class CollectionConfigEventPublisher implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(CollectionConfigEventPublisher.class);

    static final String TOPIC = "emf.config.collection.changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CollectionConfigEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                           ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getCollectionName() {
        return "collections";
    }

    @Override
    public int getOrder() {
        // Run after the SchemaLifecycleModule's CollectionLifecycleHook (order 0)
        return 100;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        publishEvent(record, ChangeType.CREATED);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        publishEvent(record, ChangeType.UPDATED);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(id);
        payload.setChangeType(ChangeType.DELETED);
        sendToKafka(payload);
    }

    private void publishEvent(Map<String, Object> record, ChangeType changeType) {
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(getString(record, "id"));
        payload.setName(getString(record, "name"));
        payload.setDisplayName(getString(record, "displayName"));
        payload.setDescription(getString(record, "description"));
        payload.setStorageMode(getString(record, "storageMode"));
        payload.setChangeType(changeType);

        Object active = record.get("active");
        if (active instanceof Boolean b) {
            payload.setActive(b);
        } else {
            payload.setActive(true);
        }

        Object version = record.get("currentVersion");
        if (version instanceof Number n) {
            payload.setCurrentVersion(n.intValue());
        }

        sendToKafka(payload);
    }

    private void sendToKafka(CollectionChangedPayload payload) {
        try {
            ConfigEvent<CollectionChangedPayload> event =
                    EventFactory.createEvent(TOPIC, payload);
            String json = objectMapper.writeValueAsString(event);
            String key = payload.getId();

            kafkaTemplate.send(TOPIC, key, json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish collection changed event for '{}' (id={}): {}",
                            payload.getName(), payload.getId(), ex.getMessage());
                    } else {
                        log.info("Published collection {} event for '{}' (id={}) to topic '{}'",
                            payload.getChangeType(), payload.getName(), payload.getId(), TOPIC);
                    }
                });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize collection changed event for '{}' (id={}): {}",
                payload.getName(), payload.getId(), e.getMessage());
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
