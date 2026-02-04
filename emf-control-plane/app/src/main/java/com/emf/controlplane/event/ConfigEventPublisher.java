package com.emf.controlplane.event;

import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.FieldPolicy;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.entity.RoutePolicy;
import com.emf.controlplane.entity.Service;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiPage;
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.EventFactory;
import com.emf.runtime.event.ServiceChangedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Component for publishing configuration change events to Kafka.
 * Events are published asynchronously with correlation IDs for tracing.
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

    private static final String EVENT_TYPE_SERVICE_CHANGED = "emf.config.service.changed";
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
     * Publishes a service changed event to Kafka.
     * The event includes the full service entity.
     *
     * @param service The service that changed
     * @param changeType The type of change (CREATED, UPDATED, DELETED)
     */
    @Async
    public void publishServiceChanged(Service service, ChangeType changeType) {
        log.info("Publishing service changed event: serviceId={}, changeType={}", 
                service.getId(), changeType);

        ServiceChangedPayload payload = PayloadAdapter.toServicePayload(service, changeType);
        ConfigEvent<ServiceChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_SERVICE_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getServiceChanged();
        sendEvent(topic, service.getId(), event);
    }

    /**
     * Publishes a collection changed event to Kafka.
     * The event includes the full collection entity with all active fields.
     *
     * @param collection The collection that changed
     * @param changeType The type of change (CREATED, UPDATED, DELETED)
     *
     * Validates: Requirements 10.1, 10.5, 10.6
     */
    @Async
    public void publishCollectionChanged(Collection collection, ChangeType changeType) {
        log.info("Publishing collection changed event: collectionId={}, changeType={}", 
                collection.getId(), changeType);

        CollectionChangedPayload payload = PayloadAdapter.toCollectionPayload(collection, changeType);
        ConfigEvent<CollectionChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_COLLECTION_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getCollectionChanged();
        sendEvent(topic, collection.getId(), event);
    }

    /**
     * Publishes an authorization changed event to Kafka.
     * The event includes the full authorization configuration for the collection.
     *
     * @param collectionId The collection ID
     * @param collectionName The collection name
     * @param routePolicies The route policies for the collection
     * @param fieldPolicies The field policies for the collection
     *
     * Validates: Requirements 10.2, 10.5, 10.6
     */
    @Async
    public void publishAuthzChanged(
            String collectionId,
            String collectionName,
            List<RoutePolicy> routePolicies,
            List<FieldPolicy> fieldPolicies) {
        log.info("Publishing authz changed event: collectionId={}", collectionId);

        AuthzChangedPayload payload = PayloadAdapter.toAuthzPayload(
                collectionId, collectionName, routePolicies, fieldPolicies);
        ConfigEvent<AuthzChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_AUTHZ_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getAuthzChanged();
        sendEvent(topic, collectionId, event);
    }

    /**
     * Publishes a UI configuration changed event to Kafka.
     * The event includes the full UI configuration with all pages and menus.
     *
     * @param pages The list of active UI pages
     * @param menus The list of UI menus
     *
     * Validates: Requirements 10.3, 10.5, 10.6
     */
    @Async
    public void publishUiChanged(List<UiPage> pages, List<UiMenu> menus) {
        log.info("Publishing UI changed event: pages={}, menus={}", 
                pages != null ? pages.size() : 0, 
                menus != null ? menus.size() : 0);

        UiChangedPayload payload = UiChangedPayload.create(pages, menus);
        ConfigEvent<UiChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_UI_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getUiChanged();
        sendEvent(topic, "ui-config", event);
    }

    /**
     * Publishes an OIDC configuration changed event to Kafka.
     * The event includes the full list of active OIDC providers.
     *
     * @param providers The list of active OIDC providers
     *
     * Validates: Requirements 10.4, 10.5, 10.6
     */
    @Async
    public void publishOidcChanged(List<OidcProvider> providers) {
        log.info("Publishing OIDC changed event: providers={}", 
                providers != null ? providers.size() : 0);

        OidcChangedPayload payload = OidcChangedPayload.create(providers);
        ConfigEvent<OidcChangedPayload> event = EventFactory.createEvent(
                EVENT_TYPE_OIDC_CHANGED, generateCorrelationId(), payload);

        String topic = properties.getKafka().getTopics().getOidcChanged();
        sendEvent(topic, "oidc-config", event);
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
     * Sends an event to the specified Kafka topic.
     * Uses the key for partitioning to ensure events for the same entity go to the same partition.
     *
     * @param topic The Kafka topic
     * @param key The message key (used for partitioning)
     * @param event The event to send
     */
    private void sendEvent(String topic, String key, ConfigEvent<?> event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {}: eventId={}, error={}", 
                        topic, event.getEventId(), ex.getMessage(), ex);
            } else {
                log.debug("Successfully published event to topic {}: eventId={}, partition={}, offset={}", 
                        topic, event.getEventId(), 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
