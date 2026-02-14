package com.emf.controlplane.event;

import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.FieldPolicy;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.entity.RoutePolicy;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiPage;
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.EventFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Component for publishing configuration change events to Kafka.
 *
 * <p>Payload construction from JPA entities happens synchronously on the caller's
 * thread (within the transaction) to avoid LazyInitializationException. Only the
 * Kafka send is performed asynchronously.
 *
 * <p>This component is conditionally enabled based on the property
 * {@code emf.control-plane.kafka.enabled}. When disabled (e.g., in tests),
 * no events will be published.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>10.1: Publish collection change events to Kafka with full entity payload</li>
 *   <li>10.2: Publish authorization change events to Kafka</li>
 *   <li>10.3: Publish UI configuration change events to Kafka</li>
 *   <li>10.4: Publish OIDC configuration change events to Kafka</li>
 *   <li>10.5: Include correlation ID in all events</li>
 *   <li>10.6: Events should be published asynchronously</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "emf.control-plane.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class ConfigEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigEventPublisher.class);

    private static final String EVENT_TYPE_COLLECTION_CHANGED = "emf.config.collection.changed";
    private static final String EVENT_TYPE_AUTHZ_CHANGED = "emf.config.authz.changed";
    private static final String EVENT_TYPE_UI_CHANGED = "emf.config.ui.changed";
    private static final String EVENT_TYPE_OIDC_CHANGED = "emf.config.oidc.changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ControlPlaneProperties properties;

    public ConfigEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            ControlPlaneProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Publishes a collection changed event to Kafka.
     *
     * <p>Builds the payload synchronously from the JPA entity (to access lazy-loaded
     * fields within the transaction), then sends to Kafka asynchronously.
     *
     * @param collection The collection that changed
     * @param changeType The type of change (CREATED, UPDATED, DELETED)
     *
     * Validates: Requirements 10.1, 10.5, 10.6
     */
    public void publishCollectionChanged(Collection collection, ChangeType changeType) {
        log.info("Publishing collection changed event: collectionId={}, changeType={}",
                collection.getId(), changeType);

        // Build payload synchronously (accesses lazy-loaded JPA fields within the transaction)
        CollectionChangedPayload payload = PayloadAdapter.toCollectionPayload(collection, changeType);
        ConfigEvent<CollectionChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_COLLECTION_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getCollectionChanged();
        sendEventAsync(topic, collection.getId(), event);
    }

    /**
     * Publishes an authorization changed event to Kafka.
     *
     * <p>Builds the payload synchronously from JPA entities, then sends asynchronously.
     *
     * @param collectionId The collection ID
     * @param collectionName The collection name
     * @param routePolicies The route policies for the collection
     * @param fieldPolicies The field policies for the collection
     *
     * Validates: Requirements 10.2, 10.5, 10.6
     */
    public void publishAuthzChanged(
            String collectionId,
            String collectionName,
            List<RoutePolicy> routePolicies,
            List<FieldPolicy> fieldPolicies) {
        log.info("Publishing authz changed event: collectionId={}", collectionId);

        // Build payload synchronously (accesses lazy-loaded JPA relations within the transaction)
        AuthzChangedPayload payload = PayloadAdapter.toAuthzPayload(
                collectionId, collectionName, routePolicies, fieldPolicies);
        ConfigEvent<AuthzChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_AUTHZ_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getAuthzChanged();
        sendEventAsync(topic, collectionId, event);
    }

    /**
     * Publishes a UI configuration changed event to Kafka.
     *
     * <p>Builds the payload synchronously from JPA entities, then sends asynchronously.
     *
     * @param pages The list of active UI pages
     * @param menus The list of UI menus
     *
     * Validates: Requirements 10.3, 10.5, 10.6
     */
    public void publishUiChanged(List<UiPage> pages, List<UiMenu> menus) {
        log.info("Publishing UI changed event: pages={}, menus={}",
                pages != null ? pages.size() : 0,
                menus != null ? menus.size() : 0);

        // Build payload synchronously (accesses lazy-loaded JPA relations within the transaction)
        UiChangedPayload payload = UiChangedPayload.create(pages, menus);
        ConfigEvent<UiChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_UI_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getUiChanged();
        sendEventAsync(topic, "ui-config", event);
    }

    /**
     * Publishes an OIDC configuration changed event to Kafka.
     *
     * <p>Builds the payload synchronously from JPA entities, then sends asynchronously.
     *
     * @param providers The list of active OIDC providers
     *
     * Validates: Requirements 10.4, 10.5, 10.6
     */
    public void publishOidcChanged(List<OidcProvider> providers) {
        log.info("Publishing OIDC changed event: providers={}",
                providers != null ? providers.size() : 0);

        // Build payload synchronously (accesses JPA entities within the transaction)
        OidcChangedPayload payload = OidcChangedPayload.create(providers);
        ConfigEvent<OidcChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_OIDC_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getOidcChanged();
        sendEventAsync(topic, "oidc-config", event);
    }

    /**
     * Publishes a worker assignment changed event to Kafka.
     *
     * @param workerId The worker ID
     * @param collectionId The collection ID
     * @param workerBaseUrl The worker's base URL
     * @param collectionName The collection name
     * @param changeType The type of change (CREATED, DELETED)
     */
    public void publishWorkerAssignmentChanged(String workerId, String collectionId,
            String workerBaseUrl, String collectionName, ChangeType changeType) {
        log.info("Publishing worker assignment changed event: workerId={}, collectionId={}, changeType={}",
                workerId, collectionId, changeType);

        Map<String, Object> payload = Map.of(
                "workerId", workerId,
                "collectionId", collectionId,
                "workerBaseUrl", workerBaseUrl,
                "collectionName", collectionName,
                "changeType", changeType.name()
        );
        ConfigEvent<Map<String, Object>> event = EventFactory.createEvent(
                "emf.worker.assignment.changed", generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getWorkerAssignmentChanged();
        sendEventAsync(topic, collectionId, event);
    }

    /**
     * Publishes a worker status changed event to Kafka.
     *
     * @param workerId The worker ID
     * @param host The worker's host
     * @param status The worker's new status
     * @param pool The worker's pool
     */
    public void publishWorkerStatusChanged(String workerId, String host, String status, String pool) {
        log.info("Publishing worker status changed event: workerId={}, status={}", workerId, status);

        Map<String, Object> payload = Map.of(
                "workerId", workerId,
                "host", host,
                "status", status,
                "pool", pool
        );
        ConfigEvent<Map<String, Object>> event = EventFactory.createEvent(
                "emf.worker.status.changed", generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getWorkerStatusChanged();
        sendEventAsync(topic, workerId, event);
    }

    /**
     * Generates a correlation ID for the event.
     * Uses the requestId from MDC if available, otherwise generates a new UUID.
     *
     * @return The correlation ID
     */
    private String generateCorrelationId() {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Sends an event to the specified Kafka topic asynchronously.
     *
     * <p>The Kafka send itself is non-blocking (returns a CompletableFuture).
     * This method is marked @Async so that if the Kafka producer blocks on
     * buffer-full or metadata fetch, it doesn't block the caller's thread.
     *
     * @param topic The Kafka topic
     * @param key The message key (used for partitioning)
     * @param event The event to send
     */
    @Async
    void sendEventAsync(String topic, String key, ConfigEvent<?> event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {}: eventId={}, error={}",
                        topic, event.getEventId(), ex.getMessage(), ex);
            } else {
                log.info("Successfully published event to topic {}: eventId={}, partition={}, offset={}",
                        topic, event.getEventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
