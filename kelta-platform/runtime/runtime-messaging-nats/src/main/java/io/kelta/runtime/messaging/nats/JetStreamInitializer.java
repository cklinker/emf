package io.kelta.runtime.messaging.nats;

import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.util.List;

/**
 * Initializes JetStream streams on application startup.
 *
 * <p>Creates the required streams if they don't already exist.
 * Existing streams are left unchanged to avoid disrupting consumers.
 *
 * @since 1.0.0
 */
public class JetStreamInitializer {

    private static final Logger log = LoggerFactory.getLogger(JetStreamInitializer.class);

    private final NatsConnectionManager connectionManager;

    public JetStreamInitializer(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Runs before {@link NatsSubscriptionManager#startSubscriptions()} (see the
     * {@code @Order} values) so every stream exists before the first subscribe
     * attempt — the unordered race left pods with dead subscriptions until restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void initializeStreams() {
        try {
            JetStreamManagement jsm = connectionManager.jetStreamManagement();
            ensureStream(jsm, "KELTA_RECORDS",
                    List.of("kelta.record.changed.>"),
                    Duration.ofHours(24));
            ensureStream(jsm, "KELTA_CONFIG",
                    List.of("kelta.config.>", "kelta.cerbos.>", "kelta.worker.>", "kelta.data.>"),
                    Duration.ofDays(7));
            // External flow triggers: kelta.trigger.<tenantId>.<topic> — consumed
            // by NATS_TRIGGERED flows via the worker's queue-group subscription.
            ensureStream(jsm, "KELTA_TRIGGERS",
                    List.of("kelta.trigger.>"),
                    Duration.ofHours(24));
            // Ephemeral presence deltas (app-intelligence slice 3) — shortest-lived
            // stream; presence state expires client-side in 90s anyway.
            ensureStream(jsm, "KELTA_PRESENCE",
                    List.of("kelta.presence.>"),
                    Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("Failed to initialize JetStream streams: {}", e.getMessage(), e);
        }
    }

    private void ensureStream(JetStreamManagement jsm, String name,
                              List<String> subjects, Duration maxAge) {
        try {
            StreamInfo info = jsm.getStreamInfo(name);
            log.info("JetStream stream '{}' already exists (messages: {}, bytes: {})",
                    name, info.getStreamState().getMsgCount(), info.getStreamState().getByteCount());
        } catch (Exception e) {
            try {
                // NATS rejects a stream whose duplicates window exceeds its max age
                // (error 10052), so clamp the window for short-lived streams.
                Duration duplicateWindow = maxAge.compareTo(Duration.ofMinutes(2)) < 0
                        ? maxAge : Duration.ofMinutes(2);
                StreamConfiguration config = StreamConfiguration.builder()
                        .name(name)
                        .subjects(subjects)
                        .maxAge(maxAge)
                        .duplicateWindow(duplicateWindow)
                        .build();
                jsm.addStream(config);
                log.info("Created JetStream stream '{}' with subjects {}", name, subjects);
            } catch (Exception ex) {
                log.error("Failed to create JetStream stream '{}': {}", name, ex.getMessage(), ex);
            }
        }
    }
}
