package io.kelta.runtime.messaging.nats;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.JetStreamSubscription;
import io.nats.client.api.ConsumerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NatsSubscriptionManager consumer lag gauge")
class NatsSubscriptionManagerLagTest {

    @Mock
    private NatsConnectionManager connectionManager;

    @Mock
    private JetStreamSubscription jsSub;

    private MeterRegistry registry;
    private NatsSubscriptionManager mgr;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mgr = new NatsSubscriptionManager(connectionManager, new ObjectMapper(), NatsTracing.NOOP, registry);
    }

    @Test
    @DisplayName("registerForLagPolling exposes a gauge tagged by subscription")
    void registerExposesGauge() {
        mgr.registerForLagPolling("sub-a", jsSub);

        assertThat(registry.find("kelta.nats.consume.lag")
                .tag("subscription", "sub-a").gauge()).isNotNull();
        assertThat(registry.find("kelta.nats.consume.lag")
                .tag("subscription", "sub-a").gauge().value()).isZero();
    }

    @Test
    @DisplayName("pollLagOnce reads ConsumerInfo.getNumPending and updates gauge")
    void pollUpdatesGauge() throws Exception {
        ConsumerInfo info = mock(ConsumerInfo.class);
        when(info.getNumPending()).thenReturn(42L);
        when(jsSub.getConsumerInfo()).thenReturn(info);

        mgr.registerForLagPolling("sub-a", jsSub);
        mgr.pollLagOnce();

        assertThat(registry.find("kelta.nats.consume.lag")
                .tag("subscription", "sub-a").gauge().value()).isEqualTo(42.0);
    }

    @Test
    @DisplayName("pollLagOnce silently skips when ConsumerInfo lookup throws")
    void pollSilentlyHandlesFailure() throws Exception {
        when(jsSub.getConsumerInfo()).thenThrow(new RuntimeException("broker down"));

        mgr.registerForLagPolling("sub-a", jsSub);
        // Should not propagate
        mgr.pollLagOnce();

        assertThat(registry.find("kelta.nats.consume.lag")
                .tag("subscription", "sub-a").gauge().value()).isZero();
    }

    @Test
    @DisplayName("no MeterRegistry => no gauge registration on registerForLagPolling")
    void noMetricsWithoutRegistry() {
        NatsSubscriptionManager noMetrics = new NatsSubscriptionManager(
                connectionManager, new ObjectMapper(), NatsTracing.NOOP, null);

        noMetrics.registerForLagPolling("sub-a", jsSub);

        assertThat(registry.find("kelta.nats.consume.lag").gauge()).isNull();
    }
}
