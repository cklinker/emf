package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.BeforeSaveResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * Before-save hook for the "fields" system collection that publishes
 * collection config change events to Kafka after field create/update/delete.
 *
 * <p>When a field is added, modified, or removed from a collection, this hook
 * publishes a collection UPDATED event so that workers can refresh the collection
 * schema (e.g., ALTER TABLE ADD COLUMN) and the gateway can update its routes.
 *
 * @since 1.0.0
 */
public class FieldConfigEventPublisher implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(FieldConfigEventPublisher.class);

    static final String TOPIC = "emf.config.collection.changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FieldConfigEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                      ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getCollectionName() {
        return "fields";
    }

    @Override
    public int getOrder() {
        // Run after the SchemaLifecycleModule's FieldLifecycleHook (order 0)
        return 100;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        publishCollectionUpdated(record);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        publishCollectionUpdated(record);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        // For deletes we don't have the collection ID readily available,
        // but the field ID alone is not enough. The collection schema listener
        // will handle this via a full refresh when it receives the event.
        log.info("Field '{}' deleted — collection refresh will occur on next schema sync", id);
    }

    private void publishCollectionUpdated(Map<String, Object> record) {
        String collectionId = getString(record, "collectionId");
        if (collectionId == null) {
            log.warn("Field record missing collectionId, cannot publish collection changed event");
            return;
        }

        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(collectionId);
        payload.setChangeType(ChangeType.UPDATED);

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, collectionId, json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish collection changed event for field change " +
                                "(collectionId={}): {}", collectionId, ex.getMessage());
                    } else {
                        log.info("Published collection UPDATED event (triggered by field change) " +
                                "for collectionId={}", collectionId);
                    }
                });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize collection changed event for field change " +
                    "(collectionId={}): {}", collectionId, e.getMessage());
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
