package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.EventSubscription;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.impl.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final NatsConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final List<EventSubscription> subscriptions = new ArrayList<>();
    private final List<JetStreamSubscription> activeSubscriptions = new ArrayList<>();
    private final ExecutorService pullExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;

    public NatsSubscriptionManager(NatsConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a subscription to be activated on application startup.
     */
    public void register(EventSubscription subscription) {
        subscriptions.add(subscription);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubscriptions() {
        for (EventSubscription sub : subscriptions) {
            try {
                switch (sub.deliveryMode()) {
                    case QUEUE_GROUP -> startPullConsumer(sub);
                    case BROADCAST -> startPushConsumer(sub);
                }
            } catch (Exception e) {
                log.error("Failed to start subscription '{}' on {}: {}",
                        sub.name(), sub.subject(), e.getMessage(), e);
            }
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

        pullExecutor.submit(() -> {
            log.info("Pull consumer '{}' started on subject '{}'", sub.name(), sub.subject());
            while (running) {
                try {
                    List<Message> messages = jsSub.fetch(10, Duration.ofSeconds(1));
                    for (Message msg : messages) {
                        try {
                            String data = new String(msg.getData(), StandardCharsets.UTF_8);
                            if (!tenantEnvelopeValid(sub.name(), msg.getHeaders(), data)) {
                                msg.ack(); // discard, don't redeliver a poisoned message
                                continue;
                            }
                            sub.handler().accept(data);
                            msg.ack();
                        } catch (Exception e) {
                            log.error("Error processing message in '{}': {}",
                                    sub.name(), e.getMessage(), e);
                            msg.nak();
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
            try {
                String data = new String(msg.getData(), StandardCharsets.UTF_8);
                if (!tenantEnvelopeValid(sub.name(), msg.getHeaders(), data)) {
                    msg.ack(); // discard, don't redeliver a poisoned message
                    return;
                }
                sub.handler().accept(data);
                msg.ack();
            } catch (Exception e) {
                log.error("Error processing broadcast message in '{}': {}",
                        sub.name(), e.getMessage(), e);
                msg.nak();
            }
        }, false, options);

        activeSubscriptions.add(jsSub);
        log.info("Push consumer '{}' started on subject '{}'", sub.name(), sub.subject());
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

    @Override
    public void destroy() {
        running = false;
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
