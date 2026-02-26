package com.emf.worker.listener;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.worker.service.CollectionLifecycleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for collection schema change events from the control plane.
 *
 * <p>When fields are added, removed, or modified on a collection, the control plane
 * publishes a {@code collection-changed} event. This listener picks up those events
 * and refreshes the collection definition on the worker, which triggers schema
 * migration (e.g., ALTER TABLE ADD COLUMN) for any new fields.
 *
 * <p>Only processes events for collections that are actively managed by this worker.
 * Ignores events for collections assigned to other workers.
 */
@Component
public class CollectionSchemaListener {

    private static final Logger log = LoggerFactory.getLogger(CollectionSchemaListener.class);

    private final CollectionLifecycleManager lifecycleManager;
    private final ObjectMapper objectMapper;

    public CollectionSchemaListener(CollectionLifecycleManager lifecycleManager,
                                     ObjectMapper objectMapper) {
        this.lifecycleManager = lifecycleManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles collection changed events from the control plane.
     *
     * <p>When a collection's schema changes (e.g., field added), this method:
     * <ol>
     *   <li>Checks if the collection is actively managed by this worker</li>
     *   <li>If so, refreshes the collection definition from the control plane</li>
     *   <li>The refresh triggers schema migration (ALTER TABLE) for any new fields</li>
     * </ol>
     *
     * @param message the raw JSON message from Kafka
     */
    @KafkaListener(
            topics = "${emf.kafka.topics.collection-changed:emf.config.collection.changed}",
            groupId = "${emf.worker.id:emf-worker-default}-schema",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleCollectionChanged(String message) {
        log.debug("Received collection changed event: {}", message);

        try {
            CollectionChangedPayload payload = parsePayload(message);

            if (payload == null) {
                log.warn("Could not parse collection changed event from message");
                return;
            }

            String collectionId = payload.getId();
            String collectionName = payload.getName();
            ChangeType changeType = payload.getChangeType();

            if (changeType == ChangeType.DELETED) {
                log.info("Collection '{}' (id={}) was deleted, tearing down", collectionName, collectionId);
                lifecycleManager.teardownCollection(collectionId);
                return;
            }

            if (changeType == ChangeType.CREATED) {
                if (lifecycleManager.getActiveCollections().contains(collectionId)) {
                    log.debug("Collection '{}' (id={}) already active, ignoring CREATED event",
                            collectionName, collectionId);
                    return;
                }
                log.info("Collection '{}' (id={}) was created, initializing", collectionName, collectionId);
                lifecycleManager.initializeCollection(collectionId);
                return;
            }

            // UPDATED — refresh the collection definition and migrate schema
            if (!lifecycleManager.getActiveCollections().contains(collectionId)) {
                log.info("Collection '{}' (id={}) not yet active, initializing on UPDATED event",
                        collectionName, collectionId);
                lifecycleManager.initializeCollection(collectionId);
                return;
            }

            log.info("Collection '{}' (id={}) schema changed (type={}), refreshing definition",
                    collectionName, collectionId, changeType);
            lifecycleManager.refreshCollection(collectionId);

        } catch (Exception e) {
            log.error("Error processing collection changed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Parses the CollectionChangedPayload from the raw Kafka message.
     * Handles both ConfigEvent wrapper format and flat JSON format.
     */
    private CollectionChangedPayload parsePayload(String message) {
        try {
            var tree = objectMapper.readTree(message);

            if (tree.has("payload")) {
                var payloadNode = tree.get("payload");
                return objectMapper.treeToValue(payloadNode, CollectionChangedPayload.class);
            }

            return objectMapper.readValue(message, CollectionChangedPayload.class);

        } catch (Exception e) {
            log.error("Failed to parse collection changed event: {}", e.getMessage());
            return null;
        }
    }
}
