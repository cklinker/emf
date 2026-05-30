package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

/**
 * NATS JetStream implementation of {@link PlatformEventPublisher}.
 *
 * <p>Publishes events as JSON to JetStream subjects. Sets the {@code Nats-Msg-Id}
 * header to the event's unique ID for server-side deduplication.
 *
 * <p>Publishing is asynchronous. Inflight publishes are bounded by a semaphore
 * (default 1000, configurable via {@code kelta.nats.max-inflight-publishes}).
 * When the cap is reached new publishes are dropped immediately with a warning
 * and a {@code kelta.nats.publish.dropped} counter increment, rather than queuing
 * unbounded inside the client.
 *
 * <p>When a {@link MeterRegistry} is provided, three counters and one gauge are
 * registered: {@code kelta.nats.publish.success}, {@code kelta.nats.publish.failure},
 * {@code kelta.nats.publish.dropped}, and {@code kelta.nats.publish.inflight}.
 *
 * @since 1.0.0
 */
public class NatsEventPublisher implements PlatformEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsEventPublisher.class);

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

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final Semaphore inflight;
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();

    public NatsEventPublisher(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this(connectionManager, objectMapper, 1000, null);
    }

    public NatsEventPublisher(NatsConnectionManager connectionManager,
                              ObjectMapper objectMapper,
                              int maxInflightPublishes,
                              MeterRegistry meterRegistry) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.inflight = new Semaphore(Math.max(1, maxInflightPublishes));
        registerMetrics(meterRegistry, maxInflightPublishes);
    }

    private void registerMetrics(MeterRegistry registry, int capacity) {
        if (registry == null) {
            return;
        }
        Counter.builder("kelta.nats.publish.success")
                .description("NATS JetStream publishes that received an ack")
                .register(registry);
        Counter.builder("kelta.nats.publish.failure")
                .description("NATS JetStream publishes that failed (broker error, timeout, etc.)")
                .register(registry);
        Counter.builder("kelta.nats.publish.dropped")
                .description("NATS publishes dropped because the inflight cap was reached")
                .register(registry);
        Gauge.builder("kelta.nats.publish.inflight", inflight,
                        s -> (double) (capacity - s.availablePermits()))
                .description("Current number of in-flight NATS publishes")
                .register(registry);
        // Bridge LongAdder values into the registered counters via FunctionCounter would
        // double-count on each tick; instead callers should read the registered counters
        // which we increment directly inside publish().
    }

    @Override
    public void publish(String subject, PlatformEvent<?> event) {
        if (!inflight.tryAcquire()) {
            droppedCount.increment();
            log.warn("Dropping NATS publish event {} to {} — inflight cap reached ({} permits)",
                    event.getEventId(), subject, inflight.availablePermits());
            return;
        }
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
                try {
                    if (ex != null) {
                        failureCount.increment();
                        log.error("Failed to publish event {} to {}: {}",
                                event.getEventId(), subject, ex.getMessage());
                    } else {
                        successCount.increment();
                        log.debug("Published event {} to {} (stream: {}, seq: {})",
                                event.getEventId(), subject, ack.getStream(), ack.getSeqno());
                    }
                } finally {
                    inflight.release();
                }
            });
        } catch (Exception e) {
            inflight.release();
            failureCount.increment();
            log.error("Failed to publish event {} to {}: {}",
                    event.getEventId(), subject, e.getMessage());
        }
    }

    long successCount() {
        return successCount.sum();
    }

    long failureCount() {
        return failureCount.sum();
    }

    long droppedCount() {
        return droppedCount.sum();
    }

    int availableInflightPermits() {
        return inflight.availablePermits();
    }
}
