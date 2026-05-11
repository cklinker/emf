package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemFeatureCacheInvalidationListener (Worker)")
class SystemFeatureCacheInvalidationListenerTest {

    @Mock
    private WorkerCacheManager cacheManager;

    private SystemFeatureCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SystemFeatureCacheInvalidationListener(cacheManager, new ObjectMapper());
    }

    @Test
    @DisplayName("Evicts tenant limits cache when tenantId is on the envelope")
    void evictsForEnvelopeTenantId() {
        String message = "{\"eventType\":\"kelta.config.feature.changed\","
                + "\"tenantId\":\"tenant-1\","
                + "\"payload\":{\"name\":\"audit_log\",\"enabled\":true}}";

        listener.handleFeatureChanged(message);

        verify(cacheManager).evictTenantLimits("tenant-1");
    }

    @Test
    @DisplayName("Falls back to tenantId inside payload when not on the envelope")
    void evictsForPayloadTenantId() {
        String message = "{\"eventType\":\"kelta.config.feature.changed\","
                + "\"payload\":{\"tenantId\":\"tenant-2\",\"name\":\"audit_log\"}}";

        listener.handleFeatureChanged(message);

        verify(cacheManager).evictTenantLimits("tenant-2");
    }

    @Test
    @DisplayName("Does nothing when tenantId is missing")
    void skipsWhenTenantIdMissing() {
        String message = "{\"payload\":{\"name\":\"audit_log\"}}";

        listener.handleFeatureChanged(message);

        verifyNoInteractions(cacheManager);
    }

    @Test
    @DisplayName("Handles malformed JSON gracefully")
    void handlesMalformedJson() {
        listener.handleFeatureChanged("not json");

        verifyNoInteractions(cacheManager);
    }
}
