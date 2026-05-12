package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LayoutCacheInvalidationListenerTest {

    private WorkerCacheManager cacheManager;
    private LayoutCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        cacheManager = mock(WorkerCacheManager.class);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        listener = new LayoutCacheInvalidationListener(cacheManager, objectMapper);
    }

    @Test
    void evictsAllLayoutFamilyCollectionsForTenant() {
        String message = "{\"tenantId\":\"tenant-1\",\"payload\":{\"id\":\"layout-1\",\"changeType\":\"UPDATED\"}}";

        listener.handleLayoutChanged(message);

        verify(cacheManager).evictSystemCollection("tenant-1", "page-layouts");
        verify(cacheManager).evictSystemCollection("tenant-1", "layout-sections");
        verify(cacheManager).evictSystemCollection("tenant-1", "layout-fields");
        verify(cacheManager).evictSystemCollection("tenant-1", "layout-related-lists");
        verify(cacheManager).evictSystemCollection("tenant-1", "layout-rules");
    }

    @Test
    void evictsWithNullTenantWhenMissing() {
        String message = "{\"payload\":{\"id\":\"layout-1\"}}";

        listener.handleLayoutChanged(message);

        verify(cacheManager).evictSystemCollection(null, "page-layouts");
        verify(cacheManager).evictSystemCollection(null, "layout-related-lists");
    }

    @Test
    void malformedMessageDoesNotThrow() {
        listener.handleLayoutChanged("not json");
        verifyNoInteractions(cacheManager);
    }
}
