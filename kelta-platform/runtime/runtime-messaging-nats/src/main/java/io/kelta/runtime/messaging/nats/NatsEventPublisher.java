package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * NATS JetStream implementation of {@link PlatformEventPublisher}.
 *
 * <p>Publishes events as JSON to JetStream subjects. Sets the {@code Nats-Msg-Id}
 * header to the event's unique ID for server-side deduplication.
 *
 * <p>Publishing is asynchronous — failures are logged but do not propagate
 * to the caller, matching the existing fire-and-forget pattern.
 *
 * @since 1.0.0
 */
public class NatsEventPublisher implements PlatformEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsEventPublisher.class);

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public NatsEventPublisher(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Name of the NATS header that carries the publishing tenant's ID.
     *
     * <p>Consumers can use this for tenant-scoped routing, filtering, or
     * cross-checking against the body's {@code tenantId} field as a
     * tamper-resistance signal. Set for every event that carries a tenant
     * context; events that are explicitly global (tenantId null/blank) omit
     * the header so receivers can distinguish them.
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Override
    public void publish(String subject, PlatformEvent<?> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            Headers headers = new Headers();
            headers.add("Nats-Msg-Id", event.getEventId());
            String tenantId = event.getTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                headers.add(TENANT_ID_HEADER, tenantId);
            }

            NatsMessage message = NatsMessage.builder()
                    .subject(subject)
                    .headers(headers)
                    .data(data)
                    .build();

            JetStream js = connectionManager.jetStream();
            CompletableFuture<PublishAck> future = js.publishAsync(message);
            future.whenComplete((ack, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event {} to {}: {}",
                            event.getEventId(), subject, ex.getMessage());
                } else {
                    log.debug("Published event {} to {} (stream: {}, seq: {})",
                            event.getEventId(), subject, ack.getStream(), ack.getSeqno());
                }
            });
        } catch (Exception e) {
            log.error("Failed to publish event {} to {}: {}",
                    event.getEventId(), subject, e.getMessage());
        }
    }
}
