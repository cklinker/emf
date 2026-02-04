package com.emf.runtime.events;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.EventsConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Objects;

/**
 * Kafka-based implementation of EventPublisher.
 * 
 * <p>Publishes collection lifecycle events to Kafka topics. Topic names follow
 * the pattern: {@code {prefix}.{collectionName}.{eventType}}
 * 
 * <p>Event publishing is conditional on the collection's events configuration.
 * If events are disabled, no events are published.
 * 
 * <p>Failures are handled gracefully - event publishing failures are logged
 * but do not cause the main operation to fail.
 * 
 * @since 1.0.0
 */
public class KafkaEventPublisher implements EventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultTopicPrefix;
    
    /**
     * Creates a new KafkaEventPublisher.
     * 
     * @param kafkaTemplate the Kafka template for sending messages
     * @param objectMapper the object mapper for JSON serialization
     * @param defaultTopicPrefix the default topic prefix (used if not specified in collection config)
     */
    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, 
                               ObjectMapper objectMapper,
                               String defaultTopicPrefix) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.defaultTopicPrefix = defaultTopicPrefix != null ? defaultTopicPrefix : "emf.events";
    }
    
    /**
     * Creates a new KafkaEventPublisher with default topic prefix.
     * 
     * @param kafkaTemplate the Kafka template for sending messages
     * @param objectMapper the object mapper for JSON serialization
     */
    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this(kafkaTemplate, objectMapper, "emf.events");
    }
    
    @Override
    public void publishCreate(CollectionDefinition definition, Map<String, Object> data) {
        if (!isEventsEnabled(definition)) {
            return;
        }
        
        String recordId = extractRecordId(data);
        CollectionEvent event = CollectionEvent.create(definition.name(), recordId, data);
        publish(definition, event);
    }
    
    @Override
    public void publishUpdate(CollectionDefinition definition, Map<String, Object> data) {
        if (!isEventsEnabled(definition)) {
            return;
        }
        
        String recordId = extractRecordId(data);
        CollectionEvent event = CollectionEvent.update(definition.name(), recordId, data);
        publish(definition, event);
    }
    
    @Override
    public void publishDelete(CollectionDefinition definition, String recordId) {
        if (!isEventsEnabled(definition)) {
            return;
        }
        
        CollectionEvent event = CollectionEvent.delete(definition.name(), recordId);
        publish(definition, event);
    }
    
    @Override
    public void publish(CollectionDefinition definition, CollectionEvent event) {
        if (!isEventsEnabled(definition)) {
            logger.debug("Events disabled for collection '{}', skipping event publish", definition.name());
            return;
        }
        
        try {
            String topic = buildTopicName(definition, event.eventType());
            String payload = objectMapper.writeValueAsString(event);
            
            kafkaTemplate.send(topic, event.recordId(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to publish {} event for record '{}' in collection '{}': {}",
                            event.eventType(), event.recordId(), definition.name(), ex.getMessage());
                    } else {
                        logger.debug("Published {} event for record '{}' in collection '{}' to topic '{}'",
                            event.eventType(), event.recordId(), definition.name(), topic);
                    }
                });
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event for collection '{}': {}", 
                definition.name(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error publishing event for collection '{}': {}", 
                definition.name(), e.getMessage());
        }
    }
    
    /**
     * Checks if events are enabled for the collection.
     */
    private boolean isEventsEnabled(CollectionDefinition definition) {
        EventsConfig eventsConfig = definition.eventsConfig();
        return eventsConfig != null && eventsConfig.enabled();
    }
    
    /**
     * Builds the Kafka topic name for an event.
     * Format: {prefix}.{collectionName}.{eventType}
     */
    private String buildTopicName(CollectionDefinition definition, CollectionEvent.EventType eventType) {
        String prefix = getTopicPrefix(definition);
        return String.format("%s.%s.%s", prefix, definition.name(), eventType.name().toLowerCase());
    }
    
    /**
     * Gets the topic prefix from the collection config or uses the default.
     */
    private String getTopicPrefix(CollectionDefinition definition) {
        EventsConfig eventsConfig = definition.eventsConfig();
        if (eventsConfig != null && eventsConfig.topicPrefix() != null && !eventsConfig.topicPrefix().isBlank()) {
            return eventsConfig.topicPrefix();
        }
        return defaultTopicPrefix;
    }
    
    /**
     * Extracts the record ID from the data map.
     */
    private String extractRecordId(Map<String, Object> data) {
        Object id = data.get("id");
        return id != null ? id.toString() : "unknown";
    }
}
