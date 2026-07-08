package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.EventSubscription;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.impl.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages NATS JetStream subscriptions for event handlers.
 *
 * <p>Supports two delivery modes:
 * <ul>
 *   <li>{@link EventSubscription.DeliveryMode#QUEUE_GROUP} — durable pull consumers
 *       with load balancing across instances</li>
 *   <li>{@link EventSubscription.DeliveryMode#BROADCAST} — ephemeral push consumers
 *       where every instance receives every message</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class NatsSubscriptionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsSubscriptionManager.class);

    static final long RETRY_SECONDS = 5;

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final NatsTracing tracing;
    private final MeterRegistry meterRegistry;
    private final List<EventSubscription> subscriptions = new ArrayList<>();
    private final List<JetStreamSubscription> activeSubscriptions = new ArrayList<>();
    private final ConcurrentHashMap<String, JetStreamSubscription> subsByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lagByName = new ConcurrentHashMap<>();
    private final ExecutorService pullExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService lagPoller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nats-consumer-lag-poller");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    public NatsSubscriptionManager(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this(connectionManager, objectMapper, NatsTracing.NOOP, null);
    }

    public NatsSubscriptionManager(NatsConnectionManager connectionManager,
                                   ObjectMapper objectMapper,
                                   NatsTracing tracing) {
        this(connectionManager, objectMapper, tracing, null);
    }

    public NatsSubscriptionManager(NatsConnectionManager connectionManager,
                                   ObjectMapper objectMapper,
                                   NatsTracing tracing,
                                   MeterRegistry meterRegistry) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.tracing = tracing != null ? tracing : NatsTracing.NOOP;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Registers a subscription to be activated on application startup.
     */
    public void register(EventSubscription subscription) {
        subscriptions.add(subscription);
    }

    /**
     * Starts all registered subscriptions. Ordered after
     * {@link JetStreamInitializer#initializeStreams()} so the streams exist before
     * the first subscribe attempt. Any subscription that still fails to start
     * (NATS not yet reachable, stream owned by a peer service not yet up) is
     * retried every {@link #RETRY_SECONDS}s until it attaches — a pod must never
     * run deaf until restart because of an unlucky startup race.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void startSubscriptions() {
        List<EventSubscription> failed = new ArrayList<>();
        for (EventSubscription sub : subscriptions) {
            if (!tryStartSubscription(sub, true)) {
                failed.add(sub);
            }
        }
        if (!failed.isEmpty()) {
            log.warn("{} NATS subscription(s) failed to start — retrying every {}s until attached",
                    failed.size(), RETRY_SECONDS);
            lagPoller.schedule(() -> retryPending(failed), RETRY_SECONDS, TimeUnit.SECONDS);
        }
        // Schedule consumer-lag polling after all subscriptions are registered.
        if (meterRegistry != null && !subscriptions.isEmpty()) {
            lagPoller.scheduleAtFixedRate(this::pollLagOnce, 30, 30, TimeUnit.SECONDS);
        }
    }

    boolean tryStartSubscription(EventSubscription sub, boolean firstAttempt) {
        try {
            switch (sub.deliveryMode()) {
                case QUEUE_GROUP -> startPullConsumer(sub);
                case BROADCAST -> startPushConsumer(sub);
            }
            return true;
        } catch (Exception e) {
            if (firstAttempt) {
                log.error("Failed to start subscription '{}' on {}: {}",
                        sub.name(), sub.subject(), e.getMessage(), e);
            } else {
                log.warn("Retry failed for subscription '{}' on {}: {}",
                        sub.name(), sub.subject(), e.getMessage());
            }
            return false;
        }
    }

    void retryPending(List<EventSubscription> pending) {
        if (!running) {
            return;
        }
        List<EventSubscription> still = new ArrayList<>();
        for (EventSubscription sub : pending) {
            if (tryStartSubscription(sub, false)) {
                log.info("Subscription '{}' attached on retry", sub.name());
            } else {
                still.add(sub);
            }
        }
        if (!still.isEmpty() && running) {
            lagPoller.schedule(() -> retryPending(still), RETRY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void startPullConsumer(EventSubscription sub) throws Exception {
        JetStream js = connectionManager.jetStream();

        ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable(sub.name())
                .filterSubject(sub.subject())
                .deliverPolicy(DeliverPolicy.Last)
                .ackWait(Duration.ofSeconds(30))
                .build();

        PullSubscribeOptions options = PullSubscribeOptions.builder()
                .configuration(cc)
                .build();

        JetStreamSubscription jsSub = js.subscribe(sub.subject(), options);
        activeSubscriptions.add(jsSub);
        registerForLagPolling(sub.name(), jsSub);

        pullExecutor.submit(() -> {
            log.info("Pull consumer '{}' started on subject '{}'", sub.name(), sub.subject());
            while (running) {
                try {
                    List<Message> messages = jsSub.fetch(10, Duration.ofSeconds(1));
                    for (Message msg : messages) {
                        long start = System.nanoTime();
                        try (NatsTracing.Scope ignored = tracing.extractAndOpen(msg.getHeaders())) {
                            String data = new String(msg.getData(), StandardCharsets.UTF_8);
                            if (!tenantEnvelopeValid(sub.name(), msg.getHeaders(), data)) {
                                msg.ack(); // discard, don't redeliver a poisoned message
                                recordProcessed(sub.name(), start);
                                continue;
                            }
                            dispatch(sub, msg.getSubject(), data);
                            msg.ack();
                            recordProcessed(sub.name(), start);
                        } catch (Exception e) {
                            log.error("Error processing message in '{}': {}",
                                    sub.name(), e.getMessage(), e);
                            msg.nak();
                            recordFailed(sub.name(), start);
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        log.warn("Pull consumer '{}' error: {}", sub.name(), e.getMessage());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        });
    }

    /**
     * Routes a message to the subscription's handler — the subject-aware
     * variant when registered (wildcard subjects carrying routing tokens),
     * else the body-only handler.
     */
    private static void dispatch(EventSubscription sub, String subject, String data) {
        if (sub.subjectHandler() != null) {
            sub.subjectHandler().accept(subject, data);
        } else {
            sub.handler().accept(data);
        }
    }

    private void startPushConsumer(EventSubscription sub) throws Exception {
        Connection conn = connectionManager.getConnection();
        JetStream js = connectionManager.jetStream();
        Dispatcher dispatcher = conn.createDispatcher();

        ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .deliverPolicy(DeliverPolicy.New)
                .ackWait(Duration.ofSeconds(30))
                .build();

        PushSubscribeOptions options = PushSubscribeOptions.builder()
                .configuration(cc)
                .build();

        JetStreamSubscription jsSub = js.subscribe(sub.subject(), dispatcher, msg -> {
            long start = System.nanoTime();
            try (NatsTracing.Scope ignored = tracing.extractAndOpen(msg.getHeaders())) {
                String data = new String(msg.getData(), StandardCharsets.UTF_8);
                if (!tenantEnvelopeValid(sub.name(), msg.getHeaders(), data)) {
                    msg.ack(); // discard, don't redeliver a poisoned message
                    recordProcessed(sub.name(), start);
                    return;
                }
                dispatch(sub, msg.getSubject(), data);
                msg.ack();
                recordProcessed(sub.name(), start);
            } catch (Exception e) {
                log.error("Error processing broadcast message in '{}': {}",
                        sub.name(), e.getMessage(), e);
                msg.nak();
                recordFailed(sub.name(), start);
            }
        }, false, options);

        activeSubscriptions.add(jsSub);
        registerForLagPolling(sub.name(), jsSub);
        log.info("Push consumer '{}' started on subject '{}'", sub.name(), sub.subject());
    }

    /**
     * Track this subscription for periodic lag polling and register a gauge
     * backed by the {@link AtomicLong} we update each tick. Idempotent —
     * second registration with the same name replaces the prior gauge.
     */
    void registerForLagPolling(String name, JetStreamSubscription jsSub) {
        if (meterRegistry == null) {
            return;
        }
        subsByName.put(name, jsSub);
        AtomicLong lag = lagByName.computeIfAbsent(name, k -> new AtomicLong(0L));
        Gauge.builder("kelta.nats.consume.lag", lag, AtomicLong::doubleValue)
                .description("NATS JetStream consumer pending message count (lag)")
                .tags("subscription", name)
                .register(meterRegistry);
    }

    /** Polls each tracked subscription for ConsumerInfo and updates lag gauges. */
    void pollLagOnce() {
        for (var entry : subsByName.entrySet()) {
            String name = entry.getKey();
            JetStreamSubscription sub = entry.getValue();
            try {
                ConsumerInfo info = sub.getConsumerInfo();
                long pending = info.getNumPending();
                AtomicLong lag = lagByName.get(name);
                if (lag != null) {
                    lag.set(pending);
                }
            } catch (Exception e) {
                log.debug("Lag poll failed for subscription '{}': {}", name, e.getMessage());
            }
        }
    }

    /**
     * Cross-checks the tenant identity declared by the NATS header
     * ({@link NatsEventPublisher#TENANT_ID_HEADER}) against the body's
     * {@code tenantId} field. Mismatches indicate either a publisher bug or
     * message tampering and cause the message to be dropped (ack-and-discard,
     * so it is not redelivered indefinitely).
     *
     * <p>Absence of the header is permitted for backward compatibility while
     * older publishers roll forward, and for legitimately global events that
     * carry no tenant context. In both cases the listener still receives the
     * body and can fall back to body-side tenant extraction.
     */
    private boolean tenantEnvelopeValid(String subscription, Headers headers, String body) {
        String headerTenant = headers != null ? headers.getFirst(NatsEventPublisher.TENANT_ID_HEADER) : null;
        if (headerTenant == null || headerTenant.isBlank()) {
            return true;
        }
        try {
            var tree = objectMapper.readTree(body);
            String bodyTenant = tree.path("tenantId").asText(null);
            if (bodyTenant != null && !bodyTenant.isBlank() && !headerTenant.equals(bodyTenant)) {
                log.warn("Dropping NATS message on '{}' — header X-Tenant-Id='{}' mismatches body tenantId='{}'",
                        subscription, headerTenant, bodyTenant);
                return false;
            }
        } catch (Exception e) {
            // Non-JSON body or parse error — don't drop on this alone; let the
            // listener surface its own parse error if it cares about structure.
            log.debug("Could not parse body for tenant cross-check on '{}': {}",
                    subscription, e.getMessage());
        }
        return true;
    }

    void recordProcessed(String subscription, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("subscription", subscription);
        Counter.builder("kelta.nats.consume.processed")
                .description("NATS messages successfully processed (including poisoned-message drops)")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder("kelta.nats.consume.latency")
                .description("End-to-end handler latency from message receipt to ack/nak")
                .tags(tags)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    void recordFailed(String subscription, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("subscription", subscription);
        Counter.builder("kelta.nats.consume.failed")
                .description("NATS messages whose handler threw and were nak'd for redelivery")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder("kelta.nats.consume.latency")
                .description("End-to-end handler latency from message receipt to ack/nak")
                .tags(tags)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void destroy() {
        running = false;
        lagPoller.shutdownNow();
        for (JetStreamSubscription sub : activeSubscriptions) {
            try {
                sub.drain(Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Error draining subscription: {}", e.getMessage());
            }
        }
        pullExecutor.shutdown();
    }
}
