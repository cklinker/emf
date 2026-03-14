package io.kelta.worker.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svix.Svix;
import com.svix.models.MessageIn;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer that bridges collection change events to Svix for
 * outbound webhook delivery.
 *
 * <p>Listens to the {@code kelta.config.collection.changed} topic and
 * publishes webhook messages to Svix, scoped to the appropriate tenant.
 *
 * @since 1.0.0
 */
public class SvixWebhookPublisher {

    private static final Logger log = LoggerFactory.getLogger(SvixWebhookPublisher.class);

    private static final Map<ChangeType, String> EVENT_TYPE_MAP = Map.of(
            ChangeType.CREATED, "collection.created",
            ChangeType.UPDATED, "collection.updated"
    );

    private final Svix svix;
    private final ObjectMapper objectMapper;

    public SvixWebhookPublisher(Svix svix, ObjectMapper objectMapper) {
        this.svix = svix;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "kelta.config.collection.changed",
            groupId = "kelta-worker-svix-webhooks"
    )
    public void onCollectionChanged(String message) {
        try {
            PlatformEvent<CollectionChangedPayload> event = objectMapper.readValue(
                    message,
                    new TypeReference<>() {}
            );

            String tenantId = event.getTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                log.debug("Skipping Svix publish — no tenant ID in collection change event (id={})",
                        event.getEventId());
                return;
            }

            CollectionChangedPayload payload = event.getPayload();
            if (payload == null || payload.getChangeType() == null) {
                return;
            }

            String eventType = EVENT_TYPE_MAP.get(payload.getChangeType());
            if (eventType == null) {
                log.debug("Skipping Svix publish — unhandled change type: {}", payload.getChangeType());
                return;
            }

            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("collectionId", payload.getId());
            webhookPayload.put("collectionName", payload.getName());
            webhookPayload.put("displayName", payload.getDisplayName());
            webhookPayload.put("changeType", payload.getChangeType().name());
            webhookPayload.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : null);

            String payloadJson = objectMapper.writeValueAsString(webhookPayload);

            var messageIn = new MessageIn();
            messageIn.setEventType(eventType);
            messageIn.setPayload(payloadJson);
            messageIn.setEventId(event.getEventId());

            svix.getMessage().create(tenantId, messageIn);
            log.info("Published Svix webhook: type={}, tenant={}, collection={}",
                    eventType, tenantId, payload.getName());

        } catch (Exception e) {
            log.error("Failed to publish collection change event to Svix: {}", e.getMessage());
        }
    }
}
