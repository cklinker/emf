package com.emf.gateway.listener;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka listener for configuration change events.
 *
 * This listener subscribes to Kafka topics:
 * - Collection changed events: Updates route registry when collections are created/updated/deleted
 * - Worker assignment changed events: Updates routes when collections are assigned to workers
 *
 * All event processing is done asynchronously and handles malformed events gracefully
 * by logging errors and continuing to process subsequent events.
 *
 * <p>Uses {@code recordEventKafkaListenerContainerFactory} (StringDeserializer) because
 * the worker publishes events as JSON strings via {@code StringSerializer}. Messages are
 * manually deserialized using {@link ObjectMapper}.
 */
@Component
public class ConfigEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigEventListener.class);

    private final RouteRegistry routeRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final String workerServiceUrl;

    public ConfigEventListener(RouteRegistry routeRegistry,
                              ObjectMapper objectMapper,
                              ApplicationEventPublisher applicationEventPublisher,
                              @org.springframework.beans.factory.annotation.Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.routeRegistry = routeRegistry;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.workerServiceUrl = workerServiceUrl;
    }

    /**
     * Handles collection changed events from Kafka.
     *
     * <p>Accepts raw JSON strings and manually deserializes because the worker
     * publishes via {@code KafkaTemplate<String, String>} (StringSerializer),
     * which does not include type headers needed by JsonDeserializer.
     */
    @KafkaListener(
        topics = "${emf.gateway.kafka.topics.collection-changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "recordEventKafkaListenerContainerFactory"
    )
    public void handleCollectionChanged(String message) {
        try {
            logger.debug("Received collection changed event: {}", message);

            CollectionChangedPayload payload = parseCollectionPayload(message);

            if (payload == null) {
                logger.warn("Could not parse collection changed event from message");
                return;
            }

            logger.info("Processing collection change: id={}, name={}, changeType={}",
                        payload.getId(), payload.getName(), payload.getChangeType());

            if (payload.getChangeType() == ChangeType.DELETED) {
                routeRegistry.removeRoute(payload.getId());
                logger.info("Removed route for deleted collection: id={}, name={}",
                           payload.getId(), payload.getName());
            } else {
                RouteDefinition route = buildRouteFromCollection(payload);

                if (route != null) {
                    routeRegistry.updateRoute(route);
                    logger.info("Updated route for collection: id={}, name={}, path={}",
                               payload.getId(), payload.getName(), route.getPath());
                } else {
                    logger.error("Failed to build route from collection: id={}, name={}",
                                payload.getId(), payload.getName());
                }
            }

            applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));

        } catch (Exception e) {
            logger.error("Error processing collection changed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Parses the CollectionChangedPayload from the raw Kafka message.
     * Handles both ConfigEvent wrapper format and flat JSON format.
     */
    private CollectionChangedPayload parseCollectionPayload(String message) {
        try {
            var tree = objectMapper.readTree(message);

            if (tree.has("payload")) {
                return objectMapper.treeToValue(tree.get("payload"), CollectionChangedPayload.class);
            }

            return objectMapper.readValue(message, CollectionChangedPayload.class);

        } catch (Exception e) {
            logger.error("Failed to parse collection changed event: {}", e.getMessage());
            return null;
        }
    }

    private RouteDefinition buildRouteFromCollection(CollectionChangedPayload payload) {
        try {
            String collectionId = payload.getId();
            String collectionName = payload.getName();

            String path = "/api/" + collectionName + "/**";

            if (collectionId == null || collectionName == null) {
                logger.error("Missing required fields in collection payload: id={}, name={}",
                            collectionId, collectionName);
                return null;
            }

            return new RouteDefinition(
                collectionId,
                path,
                workerServiceUrl,
                collectionName
            );

        } catch (Exception e) {
            logger.error("Error building route from collection: {}", payload, e);
            return null;
        }
    }

    /**
     * Handles worker assignment changed events from Kafka.
     *
     * <p>Accepts raw JSON strings and manually deserializes because the worker
     * publishes via {@code KafkaTemplate<String, String>} (StringSerializer).
     */
    @KafkaListener(
        topics = "${emf.gateway.kafka.topics.worker-assignment-changed:emf.worker.assignment.changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "recordEventKafkaListenerContainerFactory"
    )
    public void handleWorkerAssignmentChanged(String message) {
        try {
            logger.debug("Received worker assignment event: {}", message);

            Map<String, Object> payload = parseWorkerAssignmentPayload(message);

            if (payload == null) {
                logger.warn("Could not parse worker assignment event from message");
                return;
            }

            String workerId = (String) payload.get("workerId");
            String collectionId = (String) payload.get("collectionId");
            String collectionName = (String) payload.get("collectionName");
            String changeType = (String) payload.get("changeType");

            logger.info("Processing worker assignment: workerId={}, collectionId={}, collectionName={}, changeType={}",
                        workerId, collectionId, collectionName, changeType);

            if ("DELETED".equals(changeType)) {
                routeRegistry.removeRoute(collectionId);
                logger.info("Removed route for unassigned collection: {}", collectionName);
            } else {
                if (collectionName == null || collectionId == null) {
                    logger.error("Missing required fields in worker assignment event: " +
                                "collectionId={}, collectionName={}",
                                collectionId, collectionName);
                    return;
                }

                // Always use the configured worker service URL (K8s Service DNS) instead
                // of the pod-specific IP from the event. Pod IPs are ephemeral and become
                // stale when pods restart, causing routing failures.
                String path = "/api/" + collectionName + "/**";
                RouteDefinition route = new RouteDefinition(
                    collectionId,
                    path,
                    workerServiceUrl,
                    collectionName
                );

                routeRegistry.updateRoute(route);
                logger.info("Added/updated route for worker-assigned collection: path={}, workerUrl={}",
                            path, workerServiceUrl);
            }

            applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));

        } catch (Exception e) {
            logger.error("Error processing worker assignment event: {}", e.getMessage(), e);
        }
    }

    /**
     * Parses the worker assignment payload from the raw Kafka message.
     * Handles both ConfigEvent wrapper format and flat JSON format.
     */
    private Map<String, Object> parseWorkerAssignmentPayload(String message) {
        try {
            var tree = objectMapper.readTree(message);

            if (tree.has("payload")) {
                return objectMapper.convertValue(tree.get("payload"),
                        new TypeReference<Map<String, Object>>() {});
            }

            return objectMapper.readValue(message,
                    new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            logger.error("Failed to parse worker assignment event: {}", e.getMessage());
            return null;
        }
    }
}
