package io.kelta.gateway.config;

import io.kelta.gateway.listener.CerbosCacheInvalidationListener;
import io.kelta.gateway.listener.ConfigEventListener;
import io.kelta.gateway.listener.RealtimeBridge;
import io.kelta.gateway.listener.SystemCollectionRouteListener;
import io.kelta.runtime.event.EventSubscription;
import io.kelta.runtime.messaging.nats.NatsSubscriptionManager;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Registers all gateway NATS subscriptions with the {@link NatsSubscriptionManager}.
 *
 * <p>Subscriptions are registered on {@link ApplicationStartedEvent} so they are
 * ready before the manager starts consuming messages on {@code ApplicationReadyEvent}.
 *
 * @since 1.0.0
 */
@Configuration
public class NatsSubscriptionConfig {

    private final NatsSubscriptionManager subscriptionManager;
    private final RealtimeBridge realtimeBridge;
    private final SystemCollectionRouteListener systemCollectionRouteListener;
    private final ConfigEventListener configEventListener;
    private final CerbosCacheInvalidationListener cerbosCacheInvalidationListener;

    public NatsSubscriptionConfig(NatsSubscriptionManager subscriptionManager,
                                   RealtimeBridge realtimeBridge,
                                   SystemCollectionRouteListener systemCollectionRouteListener,
                                   ConfigEventListener configEventListener,
                                   CerbosCacheInvalidationListener cerbosCacheInvalidationListener) {
        this.subscriptionManager = subscriptionManager;
        this.realtimeBridge = realtimeBridge;
        this.systemCollectionRouteListener = systemCollectionRouteListener;
        this.configEventListener = configEventListener;
        this.cerbosCacheInvalidationListener = cerbosCacheInvalidationListener;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerSubscriptions() {
        subscriptionManager.register(EventSubscription.queueGroup(
                "gateway-realtime", "kelta.record.changed.>", "gateway-realtime",
                realtimeBridge::onRecordChanged));

        subscriptionManager.register(EventSubscription.queueGroup(
                "gateway-record-routes", "kelta.record.changed.>", "gateway-record-routes",
                systemCollectionRouteListener::onRecordChanged));

        subscriptionManager.register(EventSubscription.queueGroup(
                "gateway-config", "kelta.config.collection.changed.*", "gateway-config",
                configEventListener::handleCollectionChanged));

        subscriptionManager.register(EventSubscription.queueGroup(
                "gateway-config-assignment", "kelta.worker.assignment.changed.*", "gateway-config-assignment",
                configEventListener::handleWorkerAssignmentChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-cerbos-cache", "kelta.cerbos.policies.changed.*",
                cerbosCacheInvalidationListener::handlePolicyChanged));
    }
}
