package io.kelta.worker.listener;

import io.kelta.runtime.router.SystemCollectionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MenuCacheInvalidationListenerTest {

    private SystemCollectionCache cache;
    private MenuCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        cache = mock(SystemCollectionCache.class);
        listener = new MenuCacheInvalidationListener(cache, new ObjectMapper());
    }

    @Test
    void evictsBothMenuCollectionsForTheEventTenant() {
        listener.handleMenuChanged("{\"tenantId\":\"tenant-1\",\"payload\":{\"id\":\"menu-1\"}}");

        verify(cache).evict("tenant-1", "ui-menus");
        verify(cache).evict("tenant-1", "ui-menu-items");
    }

    @Test
    void readsTenantIdFromThePayloadWhenAbsentFromTheEnvelope() {
        listener.handleMenuChanged("{\"payload\":{\"tenantId\":\"tenant-2\"}}");

        verify(cache).evict("tenant-2", "ui-menus");
        verify(cache).evict("tenant-2", "ui-menu-items");
    }

    @Test
    void skipsEvictionWhenTenantIdIsMissing() {
        listener.handleMenuChanged("{\"payload\":{\"id\":\"menu-1\"}}");

        verify(cache, never()).evict(anyString(), anyString());
    }

    @Test
    void malformedMessageDoesNotThrow() {
        listener.handleMenuChanged("not-json");

        verify(cache, never()).evict(anyString(), anyString());
    }
}
