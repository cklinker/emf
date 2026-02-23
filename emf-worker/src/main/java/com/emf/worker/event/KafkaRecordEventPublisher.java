package com.emf.worker.event;

import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.events.RecordEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-based implementation of {@link RecordEventPublisher}.
 *
 * <p>Publishes {@link RecordChangeEvent} instances to the {@code emf.record.changed}
 * Kafka topic, keyed by {@code tenantId:collectionName} for partition ordering.
 * This ensures all events for a given tenant + collection are processed in order
 * by downstream consumers (workflow engine, audit, etc.).
 *
 * <p>Failures are handled gracefully — publishing errors are logged but do not
 * cause the main CRUD operation to fail.
 *
 * @since 1.0.0
 */
@Component
public class KafkaRecordEventPublisher implements RecordEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRecordEventPublisher.class);

    static final String TOPIC = "emf.record.changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRecordEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(RecordChangeEvent event) {
        try {
            String key = event.getTenantId() + ":" + event.getCollectionName();
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to publish record change event for record '{}' " +
                                "in collection '{}' (tenant '{}'): {}",
                            event.getRecordId(), event.getCollectionName(),
                            event.getTenantId(), ex.getMessage());
                    } else {
                        logger.debug("Published {} event for record '{}' in collection '{}' " +
                                "(tenant '{}') to topic '{}'",
                            event.getChangeType(), event.getRecordId(),
                            event.getCollectionName(), event.getTenantId(), TOPIC);
                    }
                });
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize record change event for record '{}' " +
                    "in collection '{}': {}",
                event.getRecordId(), event.getCollectionName(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error publishing record change event for record '{}' " +
                    "in collection '{}': {}",
                event.getRecordId(), event.getCollectionName(), e.getMessage());
        }
    }
}
