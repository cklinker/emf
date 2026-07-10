package io.kelta.gateway.config;

import io.kelta.gateway.listener.CerbosCacheInvalidationListener;
import io.kelta.gateway.listener.ChatMessageBridge;
import io.kelta.gateway.listener.ConfigEventListener;
import io.kelta.gateway.listener.CustomDomainCacheInvalidationListener;
import io.kelta.gateway.listener.IpAllowlistCacheInvalidationListener;
import io.kelta.gateway.listener.LayoutCacheInvalidationListener;
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
    private final CustomDomainCacheInvalidationListener customDomainCacheInvalidationListener;
    private final LayoutCacheInvalidationListener layoutCacheInvalidationListener;
    private final IpAllowlistCacheInvalidationListener ipAllowlistCacheInvalidationListener;
    private final io.kelta.gateway.websocket.PresenceService presenceService;
    private final ChatMessageBridge chatMessageBridge;

    public NatsSubscriptionConfig(NatsSubscriptionManager subscriptionManager,
                                   RealtimeBridge realtimeBridge,
                                   SystemCollectionRouteListener systemCollectionRouteListener,
                                   ConfigEventListener configEventListener,
                                   CerbosCacheInvalidationListener cerbosCacheInvalidationListener,
                                   CustomDomainCacheInvalidationListener customDomainCacheInvalidationListener,
                                   LayoutCacheInvalidationListener layoutCacheInvalidationListener,
                                   IpAllowlistCacheInvalidationListener ipAllowlistCacheInvalidationListener,
                                   io.kelta.gateway.websocket.PresenceService presenceService,
                                   ChatMessageBridge chatMessageBridge) {
        this.subscriptionManager = subscriptionManager;
        this.realtimeBridge = realtimeBridge;
        this.systemCollectionRouteListener = systemCollectionRouteListener;
        this.configEventListener = configEventListener;
        this.cerbosCacheInvalidationListener = cerbosCacheInvalidationListener;
        this.customDomainCacheInvalidationListener = customDomainCacheInvalidationListener;
        this.layoutCacheInvalidationListener = layoutCacheInvalidationListener;
        this.ipAllowlistCacheInvalidationListener = ipAllowlistCacheInvalidationListener;
        this.presenceService = presenceService;
        this.chatMessageBridge = chatMessageBridge;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerSubscriptions() {
        // Every gateway subscription mutates per-pod state only (local WebSocket
        // sessions, RouteRegistry, in-memory caches), so all of them must be
        // BROADCAST — a queue group would deliver each event to exactly one pod
        // and leave every other pod's sessions and caches stale.
        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-realtime", "kelta.record.changed.>",
                realtimeBridge::onRecordChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-record-routes", "kelta.record.changed.>",
                systemCollectionRouteListener::onRecordChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-config", "kelta.config.collection.changed.*",
                configEventListener::handleCollectionChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-config-assignment", "kelta.worker.assignment.changed.*",
                configEventListener::handleWorkerAssignmentChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-cerbos-cache", "kelta.cerbos.policies.changed.*",
                cerbosCacheInvalidationListener::handlePolicyChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-domain-cache", "kelta.config.domain.changed.*",
                customDomainCacheInvalidationListener::handleDomainChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-layout-cache", "kelta.config.layout.changed.*",
                layoutCacheInvalidationListener::handleLayoutChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-ip-allowlist-cache", "kelta.config.tenant.ip-allowlist.changed.*",
                ipAllowlistCacheInvalidationListener::handleIpAllowlistChanged));

        // Presence deltas (app-intelligence slice 3): BROADCAST — every pod merges the
        // fleet-wide view and rebroadcasts snapshots to its local sessions.
        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-presence", "kelta.presence.*",
                presenceService::onPresenceEvent));

        // Chat events (telehealth slice 2): BROADCAST — each pod fans to its own
        // conversation-joined sockets. Ids/state only, never message bodies.
        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-chat-messages", "kelta.chat.message.>",
                chatMessageBridge::onChatMessage));

        subscriptionManager.register(EventSubscription.broadcast(
                "gateway-chat-conversations", "kelta.chat.conversation.>",
                chatMessageBridge::onConversationChanged));
    }
}
