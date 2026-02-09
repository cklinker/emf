package com.emf.gateway.listener;

import com.emf.gateway.authz.AuthzConfig;
import com.emf.gateway.authz.AuthzConfigCache;
import com.emf.gateway.authz.FieldPolicy;
import com.emf.gateway.authz.RoutePolicy;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Kafka listener for configuration change events from the control plane.
 *
 * This listener subscribes to Kafka topics:
 * - Collection changed events: Updates route registry when collections are created/updated/deleted
 * - Authorization changed events: Updates authorization cache when policies change
 * - Worker assignment changed events: Updates routes when collections are assigned to workers
 *
 * All event processing is done asynchronously and handles malformed events gracefully
 * by logging errors and continuing to process subsequent events.
 */
@Component
public class ConfigEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigEventListener.class);

    private final RouteRegistry routeRegistry;
    private final AuthzConfigCache authzConfigCache;
    private final ObjectMapper objectMapper;
    private final String workerServiceUrl;

    public ConfigEventListener(RouteRegistry routeRegistry,
                              AuthzConfigCache authzConfigCache,
                              ObjectMapper objectMapper,
                              @org.springframework.beans.factory.annotation.Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.routeRegistry = routeRegistry;
        this.authzConfigCache = authzConfigCache;
        this.objectMapper = objectMapper;
        this.workerServiceUrl = workerServiceUrl;
    }

    /**
     * Handles collection changed events from Kafka.
     *
     * When a collection is created or updated, this method:
     * 1. Extracts collection data from the event payload
     * 2. Creates or updates a RouteDefinition using the worker service URL
     * 3. Updates the route registry
     *
     * When a collection is deleted, the route is removed from the registry.
     *
     * Malformed events are logged and processing continues.
     *
     * @param event The collection changed event
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

            if (payload.getChangeType() == ChangeType.DELETED) {
                // Remove the route for deleted collection
                routeRegistry.removeRoute(payload.getId());
                logger.info("Removed route for deleted collection: id={}, name={}",
                           payload.getId(), payload.getName());
            } else {
                // Create or update route for created/updated collection
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

        } catch (Exception e) {
            logger.error("Error processing collection changed event: eventId={}, error={}",
                        event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Handles authorization changed events from Kafka.
     *
     * When authorization configuration changes for a collection, this method:
     * 1. Extracts route policies and field policies from the event
     * 2. Parses policy rules JSON to extract required roles
     * 3. Creates AuthzConfig object
     * 4. Updates the authorization cache
     *
     * Malformed events are logged and processing continues.
     *
     * @param event The authorization changed event
     */
    @KafkaListener(
        topics = "${emf.gateway.kafka.topics.authz-changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleAuthzChanged(ConfigEvent<AuthzChangedPayload> event) {
        try {
            logger.info("Received authz changed event: eventId={}, correlationId={}",
                       event.getEventId(), event.getCorrelationId());

            AuthzChangedPayload payload = event.getPayload();

            if (payload == null) {
                logger.error("Authz changed event has null payload: eventId={}", event.getEventId());
                return;
            }

            logger.debug("Processing authz change: collectionId={}, collectionName={}",
                        payload.getCollectionId(), payload.getCollectionName());

            // Parse route policies
            List<RoutePolicy> routePolicies = parseRoutePolicies(payload.getRoutePolicies());

            // Parse field policies
            List<FieldPolicy> fieldPolicies = parseFieldPolicies(payload.getFieldPolicies());

            // Create and cache AuthzConfig
            AuthzConfig authzConfig = new AuthzConfig(
                payload.getCollectionId(),
                routePolicies,
                fieldPolicies
            );

            authzConfigCache.updateConfig(payload.getCollectionId(), authzConfig);

            logger.info("Updated authz config for collection: id={}, routePolicies={}, fieldPolicies={}",
                       payload.getCollectionId(), routePolicies.size(), fieldPolicies.size());

        } catch (Exception e) {
            logger.error("Error processing authz changed event: eventId={}, error={}",
                        event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Builds a RouteDefinition from a CollectionChangedPayload.
     *
     * @param payload The collection payload
     * @return RouteDefinition or null if required fields are missing
     */
    private RouteDefinition buildRouteFromCollection(CollectionChangedPayload payload) {
        try {
            String collectionId = payload.getId();
            String collectionName = payload.getName();

            // Build path from collection name (assuming /api/{collectionName}/** pattern)
            String path = "/api/" + collectionName + "/**";

            // Validate required fields
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
     * Parses route policy payloads into RoutePolicy objects.
     *
     * Extracts roles from the policy rules JSON string.
     *
     * @param payloads List of route policy payloads
     * @return List of RoutePolicy objects
     */
    private List<RoutePolicy> parseRoutePolicies(List<AuthzChangedPayload.RoutePolicyPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return Collections.emptyList();
        }

        List<RoutePolicy> policies = new ArrayList<>();

        for (AuthzChangedPayload.RoutePolicyPayload payload : payloads) {
            try {
                String operation = payload.getOperation();
                String policyId = payload.getPolicyId();
                List<String> roles = extractRolesFromPolicyRules(payload.getPolicyRules());

                if (operation != null && policyId != null && roles != null) {
                    policies.add(new RoutePolicy(operation, policyId, roles));
                    logger.debug("Parsed route policy: operation={}, policyId={}, roles={}",
                                operation, policyId, roles);
                } else {
                    logger.warn("Skipping route policy with missing fields: operation={}, policyId={}",
                               operation, policyId);
                }

            } catch (Exception e) {
                logger.error("Error parsing route policy: {}", payload, e);
            }
        }

        return policies;
    }

    /**
     * Parses field policy payloads into FieldPolicy objects.
     *
     * Extracts roles from the policy rules JSON string.
     *
     * @param payloads List of field policy payloads
     * @return List of FieldPolicy objects
     */
    private List<FieldPolicy> parseFieldPolicies(List<AuthzChangedPayload.FieldPolicyPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return Collections.emptyList();
        }

        List<FieldPolicy> policies = new ArrayList<>();

        for (AuthzChangedPayload.FieldPolicyPayload payload : payloads) {
            try {
                String fieldName = payload.getFieldName();
                String policyId = payload.getPolicyId();
                List<String> roles = extractRolesFromPolicyRules(payload.getPolicyRules());

                if (fieldName != null && policyId != null && roles != null) {
                    policies.add(new FieldPolicy(fieldName, policyId, roles));
                    logger.debug("Parsed field policy: fieldName={}, policyId={}, roles={}",
                                fieldName, policyId, roles);
                } else {
                    logger.warn("Skipping field policy with missing fields: fieldName={}, policyId={}",
                               fieldName, policyId);
                }

            } catch (Exception e) {
                logger.error("Error parsing field policy: {}", payload, e);
            }
        }

        return policies;
    }

    /**
     * Extracts roles from a policy rules JSON string.
     *
     * The policy rules are expected to be in the format:
     * {"roles": ["ADMIN", "USER"]}
     *
     * @param policyRulesJson The policy rules JSON string
     * @return List of role names, or empty list if parsing fails
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromPolicyRules(String policyRulesJson) {
        if (policyRulesJson == null || policyRulesJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Map<String, Object> rules = objectMapper.readValue(
                policyRulesJson,
                new TypeReference<Map<String, Object>>() {}
            );

            Object rolesObj = rules.get("roles");

            if (rolesObj instanceof List) {
                return (List<String>) rolesObj;
            } else {
                logger.warn("Policy rules 'roles' field is not a list: {}", policyRulesJson);
                return Collections.emptyList();
            }

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse policy rules JSON: {}", policyRulesJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * Handles worker assignment changed events from Kafka.
     *
     * When a collection is assigned to a worker (CREATED), adds a route to the registry
     * pointing to the worker's base URL.
     *
     * When a collection is unassigned from a worker (DELETED), removes the route.
     *
     * @param event The worker assignment changed event
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

            // The payload is deserialized as a LinkedHashMap since there is no typed payload class
            Map<String, Object> payload;
            if (rawPayload instanceof Map) {
                payload = (Map<String, Object>) rawPayload;
            } else {
                // Fallback: convert via ObjectMapper
                payload = objectMapper.convertValue(rawPayload, new TypeReference<Map<String, Object>>() {});
            }

            String workerId = (String) payload.get("workerId");
            String collectionId = (String) payload.get("collectionId");
            String workerBaseUrl = (String) payload.get("workerBaseUrl");
            String collectionName = (String) payload.get("collectionName");
            String changeType = (String) payload.get("changeType");

            logger.info("Processing worker assignment: workerId={}, collectionId={}, collectionName={}, changeType={}",
                        workerId, collectionId, collectionName, changeType);

            if ("DELETED".equals(changeType)) {
                routeRegistry.removeRoute(collectionId);
                logger.info("Removed route for unassigned collection: {}", collectionName);
            } else {
                // CREATED -- add route pointing to worker
                if (workerBaseUrl == null || collectionName == null || collectionId == null) {
                    logger.error("Missing required fields in worker assignment event: " +
                                "collectionId={}, collectionName={}, workerBaseUrl={}",
                                collectionId, collectionName, workerBaseUrl);
                    return;
                }

                String path = "/api/" + collectionName + "/**";
                RouteDefinition route = new RouteDefinition(
                    collectionId,
                    path,
                    workerBaseUrl,
                    collectionName
                );

                routeRegistry.updateRoute(route);
                logger.info("Added/updated route for worker-assigned collection: path={}, workerUrl={}",
                            path, workerBaseUrl);
            }
        } catch (Exception e) {
            logger.error("Error processing worker assignment event: eventId={}, error={}",
                        event.getEventId(), e.getMessage(), e);
        }
    }
}
