package com.emf.worker.listener;

import com.emf.worker.model.AssignmentEvent;
import com.emf.worker.service.CollectionLifecycleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for collection assignment events.
 *
 * <p>Listens on the {@code emf.worker.assignment.changed} topic for events
 * that assign or unassign collections to/from workers. Every worker loads
 * every collection so the K8s Service can load-balance requests across all
 * worker pods.
 *
 * <p>Event change types:
 * <ul>
 *   <li><b>CREATED / ASSIGN:</b> Initialize the collection on this worker (regardless of target)</li>
 *   <li><b>DELETED / UNASSIGN:</b> Ignored — collections are never torn down since all workers serve all collections</li>
 * </ul>
 */
@Component
public class CollectionAssignmentListener {

    private static final Logger log = LoggerFactory.getLogger(CollectionAssignmentListener.class);

    private final CollectionLifecycleManager lifecycleManager;
    private final ObjectMapper objectMapper;

    public CollectionAssignmentListener(CollectionLifecycleManager lifecycleManager,
                                         ObjectMapper objectMapper) {
        this.lifecycleManager = lifecycleManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles collection assignment change events from Kafka.
     *
     * <p>Messages are expected to be JSON with a top-level {@code payload} field
     * containing the assignment event data (matching ConfigEvent format), or
     * a flat JSON object with the assignment fields directly.
     *
     * @param message the raw JSON message from Kafka
     */
    @KafkaListener(
            topics = "${emf.kafka.topics.assignment-changed:emf.worker.assignment.changed}",
            groupId = "${emf.worker.id:emf-worker-default}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleAssignmentChanged(String message) {
        log.debug("Received assignment event: {}", message);

        try {
            AssignmentEvent event = parseEvent(message);

            if (event == null) {
                log.warn("Could not parse assignment event from message");
                return;
            }

            String changeType = event.changeType() != null ? event.changeType().toUpperCase() : "";

            switch (changeType) {
                case "CREATED", "ASSIGN" -> {
                    // Initialize collection regardless of target worker. Every worker
                    // loads every collection so the K8s Service can load-balance
                    // requests across all worker pods.
                    if (!lifecycleManager.getActiveCollections().contains(event.collectionId())) {
                        log.info("Initializing new collection from assignment event: collectionId={}, name={}, targetWorker={}",
                                event.collectionId(), event.collectionName(), event.workerId());
                        lifecycleManager.initializeCollection(event.collectionId());
                    } else {
                        log.debug("Collection already loaded, skipping: collectionId={}", event.collectionId());
                    }
                }
                case "DELETED", "UNASSIGN" -> {
                    // Do not tear down collections when an assignment is deleted.
                    // Other workers may still route requests here via the K8s Service.
                    log.debug("Ignoring collection unassignment (all workers serve all collections): collectionId={}, name={}",
                            event.collectionId(), event.collectionName());
                }
                default ->
                    log.warn("Unknown change type '{}' in assignment event for collection {}",
                            changeType, event.collectionId());
            }

        } catch (Exception e) {
            log.error("Error processing assignment event: {}", e.getMessage(), e);
        }
    }

    /**
     * Parses an assignment event from the raw Kafka message.
     * Handles both ConfigEvent wrapper format and flat JSON format.
     */
    @SuppressWarnings("unchecked")
    private AssignmentEvent parseEvent(String message) {
        try {
            // First, try to parse as a ConfigEvent wrapper (has payload field)
            var tree = objectMapper.readTree(message);

            if (tree.has("payload")) {
                // ConfigEvent format: extract the payload
                var payloadNode = tree.get("payload");
                return objectMapper.treeToValue(payloadNode, AssignmentEvent.class);
            }

            // Flat format: parse directly
            return objectMapper.readValue(message, AssignmentEvent.class);

        } catch (Exception e) {
            log.error("Failed to parse assignment event: {}", e.getMessage());
            return null;
        }
    }
}
