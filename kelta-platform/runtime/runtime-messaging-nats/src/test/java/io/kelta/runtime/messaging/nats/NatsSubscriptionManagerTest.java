package io.kelta.runtime.messaging.nats;

import io.kelta.runtime.event.EventSubscription;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NatsSubscriptionManager metrics")
class NatsSubscriptionManagerTest {

    @Mock
    private NatsConnectionManager connectionManager;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private NatsSubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        subscriptionManager = new NatsSubscriptionManager(
                connectionManager, objectMapper, NatsTracing.NOOP, meterRegistry);
    }

    @Test
    @DisplayName("processed records counter and latency tagged by subscription")
    void processedRecordsCounterAndLatency() {
        long start = System.nanoTime();
        subscriptionManager.recordProcessed("collection-events", start);

        assertThat(meterRegistry.find("kelta.nats.consume.processed")
                .tag("subscription", "collection-events").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("kelta.nats.consume.latency")
                .tag("subscription", "collection-events").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("failed records counter and latency tagged by subscription")
    void failedRecordsCounterAndLatency() {
        long start = System.nanoTime();
        subscriptionManager.recordFailed("flow-events", start);

        assertThat(meterRegistry.find("kelta.nats.consume.failed")
                .tag("subscription", "flow-events").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("kelta.nats.consume.latency")
                .tag("subscription", "flow-events").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("no metrics when MeterRegistry is null")
    void noMetricsWithoutRegistry() {
        NatsSubscriptionManager noMetrics = new NatsSubscriptionManager(
                connectionManager, objectMapper, NatsTracing.NOOP, null);

        noMetrics.recordProcessed("sub", System.nanoTime());
        noMetrics.recordFailed("sub", System.nanoTime());

        // Original registry should remain empty since we constructed with null
        assertThat(meterRegistry.find("kelta.nats.consume.processed").counter()).isNull();
    }

    @Test
    @DisplayName("tryStartSubscription returns false when JetStream is unavailable (no throw)")
    void tryStartReturnsFalseWhenJetStreamUnavailable() {
        when(connectionManager.getConnection())
                .thenThrow(new IllegalStateException("NATS not connected"));

        EventSubscription sub = EventSubscription.broadcast(
                "test-broadcast", "kelta.test.>", msg -> { });

        assertThat(subscriptionManager.tryStartSubscription(sub, true)).isFalse();
    }

    @Test
    @DisplayName("retryPending attaches a subscription once JetStream becomes available")
    void retryPendingAttachesWhenStreamAppears() throws Exception {
        Connection connection = mock(Connection.class);
        JetStream jetStream = mock(JetStream.class);
        Dispatcher dispatcher = mock(Dispatcher.class);
        JetStreamSubscription jsSub = mock(JetStreamSubscription.class);
        when(connectionManager.getConnection()).thenReturn(connection);
        when(connectionManager.jetStream()).thenReturn(jetStream);
        when(connection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(anyString(), any(Dispatcher.class), any(MessageHandler.class),
                anyBoolean(), any(PushSubscribeOptions.class))).thenReturn(jsSub);

        EventSubscription sub = EventSubscription.broadcast(
                "test-broadcast", "kelta.test.>", msg -> { });

        subscriptionManager.retryPending(List.of(sub));

        verify(jetStream).subscribe(anyString(), any(Dispatcher.class), any(MessageHandler.class),
                anyBoolean(), any(PushSubscribeOptions.class));
    }

    @Test
    @DisplayName("counters segregate by subscription tag")
    void countersSegregateBySubscription() {
        long start = System.nanoTime();
        subscriptionManager.recordProcessed("sub-a", start);
        subscriptionManager.recordProcessed("sub-a", start);
        subscriptionManager.recordProcessed("sub-b", start);

        assertThat(meterRegistry.find("kelta.nats.consume.processed")
                .tag("subscription", "sub-a").counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.find("kelta.nats.consume.processed")
                .tag("subscription", "sub-b").counter().count()).isEqualTo(1.0);
    }
}
