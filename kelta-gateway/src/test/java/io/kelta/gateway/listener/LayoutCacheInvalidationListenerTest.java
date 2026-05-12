package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LayoutCacheInvalidationListenerTest {

    private GatewayCacheManager cacheManager;
    private LayoutCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        cacheManager = mock(GatewayCacheManager.class);
        listener = new LayoutCacheInvalidationListener(cacheManager);
    }

    @Test
    void evictsAllLayoutFamilyCollections() {
        listener.handleLayoutChanged("{\"tenantId\":\"tenant-1\"}");

        verify(cacheManager).evictSystemCollectionResponses("page-layouts");
        verify(cacheManager).evictSystemCollectionResponses("layout-sections");
        verify(cacheManager).evictSystemCollectionResponses("layout-fields");
        verify(cacheManager).evictSystemCollectionResponses("layout-related-lists");
        verify(cacheManager).evictSystemCollectionResponses("layout-rules");
    }
}
