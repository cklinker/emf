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
import io.kelta.worker.listener.SystemFeatureCacheInvalidationListener;
import io.kelta.worker.module.ModuleEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
                mock(FlowEventListener.class),
                mock(SearchIndexListener.class),
                mock(CollectionSchemaListener.class),
                mock(ModuleEventListener.class),
                mock(CerbosCacheInvalidationListener.class),
                mock(CustomDomainCacheInvalidationListener.class),
                mock(SystemFeatureCacheInvalidationListener.class),
                mock(LayoutCacheInvalidationListener.class));
    }

    @Test
    @DisplayName("registerSubscriptions boots without CredentialCacheInvalidationListener (no encryption key)")
    void registerSubscriptions_withoutCredentialListener_skipsCredentialCacheRegistration() {
        // Optional listeners (incl. credential cache) left null — mirrors Spring wiring
        // when kelta.encryption.key is not configured.
        config.registerSubscriptions();

        // Nine always-on subscriptions: worker-flows, worker-search-index, worker-schema,
        // worker-modules, worker-cerbos, worker-flow-cache, worker-domain-cache,
        // worker-feature-cache, worker-layout-cache. Credential/Superset/Svix are conditional.
        verify(subscriptionManager, times(9)).register(any(EventSubscription.class));
    }

    @Test
    @DisplayName("registerSubscriptions registers credential cache subscription when listener is present")
    void registerSubscriptions_withCredentialListener_registersCredentialCache() {
        ReflectionTestUtils.setField(config, "credentialCacheInvalidationListener",
                mock(CredentialCacheInvalidationListener.class));

        config.registerSubscriptions();

        verify(subscriptionManager, times(10)).register(any(EventSubscription.class));
    }
}
