package io.kelta.worker.config;

import io.kelta.runtime.event.EventSubscription;
import io.kelta.runtime.messaging.nats.NatsSubscriptionManager;
import io.kelta.worker.listener.CerbosCacheInvalidationListener;
import io.kelta.worker.listener.CollectionSchemaListener;
import io.kelta.worker.listener.CredentialCacheInvalidationListener;
import io.kelta.worker.listener.CustomDomainCacheInvalidationListener;
import io.kelta.worker.listener.FlowEventListener;
import io.kelta.worker.listener.LayoutCacheInvalidationListener;
import io.kelta.worker.listener.SearchIndexListener;
import io.kelta.worker.listener.SupersetCollectionSyncListener;
import io.kelta.worker.listener.SvixWebhookPublisher;
import io.kelta.worker.listener.SystemFeatureCacheInvalidationListener;
import io.kelta.worker.module.ModuleEventListener;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registers all worker NATS subscriptions with the {@link NatsSubscriptionManager}.
 *
 * <p>Replaces the previous Kafka listener annotations with explicit NATS
 * subscription registrations, supporting both queue-group (load-balanced)
 * and broadcast (all-pods) delivery modes.
 *
 * @since 1.0.0
 */
@Configuration
@Profile("!migrate")
public class NatsSubscriptionConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsSubscriptionConfig.class);

    private final NatsSubscriptionManager subscriptionManager;
    private final FlowEventListener flowEventListener;
    private final SearchIndexListener searchIndexListener;
    private final CollectionSchemaListener collectionSchemaListener;
    private final ModuleEventListener moduleEventListener;
    private final CerbosCacheInvalidationListener cerbosCacheInvalidationListener;
    private final CustomDomainCacheInvalidationListener customDomainCacheInvalidationListener;
    private final SystemFeatureCacheInvalidationListener systemFeatureCacheInvalidationListener;
    private final LayoutCacheInvalidationListener layoutCacheInvalidationListener;

    @Autowired(required = false)
    private CredentialCacheInvalidationListener credentialCacheInvalidationListener;

    @Autowired(required = false)
    private SupersetCollectionSyncListener supersetCollectionSyncListener;

    @Autowired(required = false)
    private SvixWebhookPublisher svixWebhookPublisher;

    public NatsSubscriptionConfig(NatsSubscriptionManager subscriptionManager,
                                   FlowEventListener flowEventListener,
                                   SearchIndexListener searchIndexListener,
                                   CollectionSchemaListener collectionSchemaListener,
                                   ModuleEventListener moduleEventListener,
                                   CerbosCacheInvalidationListener cerbosCacheInvalidationListener,
                                   CustomDomainCacheInvalidationListener customDomainCacheInvalidationListener,
                                   SystemFeatureCacheInvalidationListener systemFeatureCacheInvalidationListener,
                                   LayoutCacheInvalidationListener layoutCacheInvalidationListener) {
        this.subscriptionManager = subscriptionManager;
        this.flowEventListener = flowEventListener;
        this.searchIndexListener = searchIndexListener;
        this.collectionSchemaListener = collectionSchemaListener;
        this.moduleEventListener = moduleEventListener;
        this.cerbosCacheInvalidationListener = cerbosCacheInvalidationListener;
        this.customDomainCacheInvalidationListener = customDomainCacheInvalidationListener;
        this.systemFeatureCacheInvalidationListener = systemFeatureCacheInvalidationListener;
        this.layoutCacheInvalidationListener = layoutCacheInvalidationListener;
    }

    @PostConstruct
    public void registerSubscriptions() {
        // Queue group subscriptions — load-balanced across worker instances
        subscriptionManager.register(EventSubscription.queueGroup(
                "worker-flows", "kelta.record.changed.>", "worker-flows",
                flowEventListener::handleRecordChanged));

        subscriptionManager.register(EventSubscription.queueGroup(
                "worker-search-index", "kelta.record.changed.>", "worker-search-index",
                searchIndexListener::handleRecordChanged));

        // Broadcast subscriptions — every pod receives every message
        subscriptionManager.register(EventSubscription.broadcast(
                "worker-schema", "kelta.config.collection.changed.*",
                collectionSchemaListener::handleCollectionChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "worker-modules", "kelta.config.module.changed.>",
                moduleEventListener::handleModuleChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "worker-cerbos", "kelta.cerbos.policies.changed.*",
                cerbosCacheInvalidationListener::handlePolicyChanged));

        // Credential cache invalidation — every pod drops local cache entries
        // when a credential row changes upstream. Only present when
        // kelta.encryption.key is configured (credential beans are conditional
        // on EncryptionService).
        if (credentialCacheInvalidationListener != null) {
            subscriptionManager.register(EventSubscription.broadcast(
                    "worker-credential-cache", "kelta.config.credential.changed.>",
                    credentialCacheInvalidationListener::handleCredentialChanged));
            log.info("Registered NATS subscription for credential cache invalidation");
        }

        // Flow trigger cache invalidation — every pod drops the per-tenant
        // FlowEventListener cache when a flow row changes (create/update/delete).
        // Without this, flow edits made via the UI are invisible to pods that
        // already populated their cache for that tenant until the pod restarts.
        subscriptionManager.register(EventSubscription.broadcast(
                "worker-flow-cache", "kelta.config.flow.changed.>",
                flowEventListener::handleFlowConfigChanged));

        // Custom domain cache invalidation — every pod drops cached domain
        // resolutions when a domain row changes upstream.
        subscriptionManager.register(EventSubscription.broadcast(
                "worker-domain-cache", "kelta.config.domain.changed.*",
                customDomainCacheInvalidationListener::handleDomainChanged));

        // System feature cache invalidation — every pod evicts tenant-scoped
        // caches that may depend on feature flags when features change.
        subscriptionManager.register(EventSubscription.broadcast(
                "worker-feature-cache", "kelta.config.feature.changed.*",
                systemFeatureCacheInvalidationListener::handleFeatureChanged));

        subscriptionManager.register(EventSubscription.broadcast(
                "worker-layout-cache", "kelta.config.layout.changed.*",
                layoutCacheInvalidationListener::handleLayoutChanged));

        // Optional queue group subscriptions — only registered when the integration is enabled
        if (supersetCollectionSyncListener != null) {
            subscriptionManager.register(EventSubscription.queueGroup(
                    "worker-superset", "kelta.config.collection.changed.*", "worker-superset",
                    supersetCollectionSyncListener::onCollectionChanged));
            log.info("Registered NATS subscription for Superset collection sync");
        }

        if (svixWebhookPublisher != null) {
            subscriptionManager.register(EventSubscription.queueGroup(
                    "worker-svix", "kelta.config.collection.changed.*", "worker-svix",
                    svixWebhookPublisher::onCollectionChanged));
            log.info("Registered NATS subscription for Svix webhook publisher");
        }

        log.info("Registered {} worker NATS subscriptions", 9
                + (credentialCacheInvalidationListener != null ? 1 : 0)
                + (supersetCollectionSyncListener != null ? 1 : 0)
                + (svixWebhookPublisher != null ? 1 : 0));
    }
}
