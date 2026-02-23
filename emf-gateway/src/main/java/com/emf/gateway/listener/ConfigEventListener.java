package com.emf.gateway.listener;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
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
 * Kafka listener for configuration change events from the control plane.
 *
 * This listener subscribes to Kafka topics:
 * - Collection changed events: Updates route registry when collections are created/updated/deleted
 * - Worker assignment changed events: Updates routes when collections are assigned to workers
 *
 * All event processing is done asynchronously and handles malformed events gracefully
 * by logging errors and continuing to process subsequent events.
 *
 * The __control-plane system collection is ignored because its route is managed
 * statically by {@link com.emf.gateway.config.RouteInitializer}. Other system
 * collections (users, profiles, etc.) are handled normally since they are routed
 * to the worker like any other collection.
 */
@Component
public class ConfigEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigEventListener.class);

    /** Well-known UUID for the __control-plane system collection (see V43 migration). */
    private static final String CONTROL_PLANE_COLLECTION_ID = "00000000-0000-0000-0000-000000000100";

    /** Name of the control-plane system collection whose route is managed statically. */
    private static final String CONTROL_PLANE_COLLECTION_NAME = "__control-plane";

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
     * Checks whether a collection is the __control-plane collection whose route
     * is managed statically by {@link com.emf.gateway.config.RouteInitializer}.
     * Only the __control-plane collection is skipped — other system collections
     * (users, profiles, etc.) are routed to the worker like normal collections.
     */
    private boolean isControlPlaneCollection(String collectionId, String collectionName) {
        if (CONTROL_PLANE_COLLECTION_ID.equals(collectionId)) {
            return true;
        }
        return CONTROL_PLANE_COLLECTION_NAME.equals(collectionName);
    }

    /**
     * Handles collection changed events from Kafka.
     */
    @KafkaListener(
        topics = "${emf.gateway.kafka.topics.collection-changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleCollectionChanged(ConfigEvent<CollectionChangedPayload> event) {
        try {
            logger.info("Received collection changed event: eventId={}, correlationId={}",
                       event.getEventId(), event.getCorrelationId());

            CollectionChangedPayload payload = event.getPayload();

            if (payload == null) {
                logger.error("Collection changed event has null payload: eventId={}", event.getEventId());
                return;
            }

            logger.debug("Processing collection change: id={}, name={}, changeType={}",
                        payload.getId(), payload.getName(), payload.getChangeType());

            // Skip system collections — their routes are managed by RouteInitializer
            if (isControlPlaneCollection(payload.getId(), payload.getName())) {
                logger.debug("Ignoring collection changed event for __control-plane collection: id={}, name={}",
                            payload.getId(), payload.getName());
                return;
            }

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
            logger.error("Error processing collection changed event: eventId={}, error={}",
                        event.getEventId(), e.getMessage(), e);
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
     */
    @SuppressWarnings("unchecked")
    @KafkaListener(
        topics = "${emf.gateway.kafka.topics.worker-assignment-changed:emf.worker.assignment.changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleWorkerAssignmentChanged(ConfigEvent<Object> event) {
        try {
            logger.info("Received worker assignment event: eventId={}, correlationId={}",
                        event.getEventId(), event.getCorrelationId());

            Object rawPayload = event.getPayload();

            if (rawPayload == null) {
                logger.error("Worker assignment event has null payload: eventId={}", event.getEventId());
                return;
            }

            Map<String, Object> payload;
            if (rawPayload instanceof Map) {
                payload = (Map<String, Object>) rawPayload;
            } else {
                payload = objectMapper.convertValue(rawPayload, new TypeReference<Map<String, Object>>() {});
            }

            String workerId = (String) payload.get("workerId");
            String collectionId = (String) payload.get("collectionId");
            String collectionName = (String) payload.get("collectionName");
            String changeType = (String) payload.get("changeType");

            logger.info("Processing worker assignment: workerId={}, collectionId={}, collectionName={}, changeType={}",
                        workerId, collectionId, collectionName, changeType);

            // Skip system collections — their routes are managed by RouteInitializer
            if (isControlPlaneCollection(collectionId, collectionName)) {
                logger.debug("Ignoring worker assignment event for __control-plane collection: id={}, name={}",
                            collectionId, collectionName);
                return;
            }

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
            logger.error("Error processing worker assignment event: eventId={}, error={}",
                        event.getEventId(), e.getMessage(), e);
        }
    }
}
