package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    @DisplayName("Evicts the tenant's limits cache from the envelope tenantId")
    void evictsFromEnvelopeTenantId() {
        String message = "{\"tenantId\":\"tenant-1\",\"payload\":{\"feature\":\"AI_ASSIST\"}}";
        listener.handleFeatureChanged(message);
        verify(cacheManager).evictTenantLimits("tenant-1");
    }

    @Test
    @DisplayName("Falls back to payload.tenantId when the envelope is missing one")
    void evictsFromPayloadTenantId() {
        String message = "{\"payload\":{\"tenantId\":\"tenant-2\",\"feature\":\"AI_ASSIST\"}}";
        listener.handleFeatureChanged(message);
        verify(cacheManager).evictTenantLimits("tenant-2");
    }

    @Test
    @DisplayName("Skips eviction when no tenantId is present")
    void skipsWhenTenantIdMissing() {
        String message = "{\"payload\":{\"feature\":\"AI_ASSIST\"}}";
        listener.handleFeatureChanged(message);
        verify(cacheManager, never()).evictTenantLimits(anyString());
    }

    @Test
    @DisplayName("Skips eviction when tenantId is blank")
    void skipsWhenTenantIdBlank() {
        String message = "{\"tenantId\":\"  \",\"payload\":{\"feature\":\"AI_ASSIST\"}}";
        listener.handleFeatureChanged(message);
        verify(cacheManager, never()).evictTenantLimits(anyString());
    }

    @Test
    @DisplayName("Handles malformed JSON gracefully")
    void handlesMalformedJson() {
        listener.handleFeatureChanged("not json");
        verify(cacheManager, never()).evictTenantLimits(anyString());
    }
}
