package io.kelta.gateway.config;

import io.kelta.gateway.listener.CerbosCacheInvalidationListener;
import io.kelta.gateway.listener.ConfigEventListener;
import io.kelta.gateway.listener.CustomDomainCacheInvalidationListener;
import io.kelta.gateway.listener.IpAllowlistCacheInvalidationListener;
import io.kelta.gateway.listener.LayoutCacheInvalidationListener;
import io.kelta.gateway.listener.RealtimeBridge;
import io.kelta.gateway.listener.SystemCollectionRouteListener;
import io.kelta.runtime.event.EventSubscription;
import io.kelta.runtime.messaging.nats.NatsSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("NatsSubscriptionConfig")
class NatsSubscriptionConfigTest {

    private NatsSubscriptionManager subscriptionManager;
    private NatsSubscriptionConfig config;

    @BeforeEach
    void setUp() {
        subscriptionManager = mock(NatsSubscriptionManager.class);
        config = new NatsSubscriptionConfig(
                subscriptionManager,
                mock(RealtimeBridge.class),
                mock(SystemCollectionRouteListener.class),
                mock(ConfigEventListener.class),
                mock(CerbosCacheInvalidationListener.class),
                mock(CustomDomainCacheInvalidationListener.class),
                mock(LayoutCacheInvalidationListener.class),
                mock(IpAllowlistCacheInvalidationListener.class),
                mock(io.kelta.gateway.websocket.PresenceService.class),
                mock(io.kelta.gateway.listener.ChatMessageBridge.class));
    }

    @Test
    @DisplayName("every gateway subscription is BROADCAST — all handlers mutate per-pod state only")
    void registerSubscriptions_allBroadcast() {
        // Gateway handlers fan out to local WebSocket sessions or refresh this
        // pod's RouteRegistry/caches. A QUEUE_GROUP subscription would deliver
        // each event to exactly one pod and leave every other pod stale — with
        // 2+ replicas, realtime subscribers on the other pods went silent.
        config.registerSubscriptions();

        ArgumentCaptor<EventSubscription> captor = ArgumentCaptor.forClass(EventSubscription.class);
        verify(subscriptionManager, atLeastOnce()).register(captor.capture());

        assertThat(captor.getAllValues())
                .isNotEmpty()
                .allSatisfy(sub -> assertThat(sub.deliveryMode())
                        .as("subscription '%s' must be BROADCAST", sub.name())
                        .isEqualTo(EventSubscription.DeliveryMode.BROADCAST));
    }

    @Test
    @DisplayName("realtime bridge and route listeners subscribe to record changes")
    void registerSubscriptions_coversRecordChangeConsumers() {
        config.registerSubscriptions();

        ArgumentCaptor<EventSubscription> captor = ArgumentCaptor.forClass(EventSubscription.class);
        verify(subscriptionManager, atLeastOnce()).register(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(EventSubscription::name)
                .contains("gateway-realtime", "gateway-record-routes",
                        "gateway-config", "gateway-config-assignment", "gateway-presence",
                        "gateway-chat-messages", "gateway-chat-conversations");
    }
}
