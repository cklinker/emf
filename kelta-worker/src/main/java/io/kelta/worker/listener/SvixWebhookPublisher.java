package io.kelta.worker.listener;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bridges collection-change platform events to Svix for outbound webhook
 * delivery, scoped to the appropriate tenant.
 *
 * <p>Talks to the Svix REST API directly via {@link RestClient} rather than
 * the Svix Java SDK 1.68.0 — the SDK ships Jackson-2 model annotations and
 * doesn't deserialize cleanly under Spring Boot 4 / Jackson 3.
 *
 * @since 1.0.0
 */
public class SvixWebhookPublisher {

    private static final Logger log = LoggerFactory.getLogger(SvixWebhookPublisher.class);

    private static final Map<ChangeType, String> EVENT_TYPE_MAP = Map.of(
            ChangeType.CREATED, "collection.created",
            ChangeType.UPDATED, "collection.updated"
    );

    private final RestClient svixRestClient;
    private final ObjectMapper objectMapper;

    public SvixWebhookPublisher(RestClient svixRestClient, ObjectMapper objectMapper) {
        this.svixRestClient = svixRestClient;
        this.objectMapper = objectMapper;
    }

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

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventType", eventType);
            body.put("payload", webhookPayload);
            body.put("eventId", event.getEventId());
            if (payload.getName() != null && !payload.getName().isBlank()) {
                body.put("channels", Set.of(payload.getName()));
            }

            svixRestClient.post()
                    .uri("/api/v1/app/{appId}/msg/", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Published Svix webhook: type={}, tenant={}, collection={}",
                    eventType, tenantId, payload.getName());

        } catch (Exception e) {
            log.error("Failed to publish collection change event to Svix: {}", e.getMessage());
        }
    }
}
