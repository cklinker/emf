package io.kelta.worker.listener;

import io.kelta.runtime.router.SystemCollectionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TranslationCacheInvalidationListenerTest {

    private SystemCollectionCache cache;
    private TranslationCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        cache = mock(SystemCollectionCache.class);
        listener = new TranslationCacheInvalidationListener(cache, new ObjectMapper());
    }

    @Test
    void evictsTheTranslationsCollectionForTheEventTenant() {
        listener.handleTranslationChanged("{\"tenantId\":\"tenant-1\",\"payload\":{\"id\":\"tr-1\"}}");

        verify(cache).evict("tenant-1", "ui-translations");
    }

    @Test
    void readsTenantIdFromThePayloadWhenAbsentFromTheEnvelope() {
        listener.handleTranslationChanged("{\"payload\":{\"tenantId\":\"tenant-2\"}}");

        verify(cache).evict("tenant-2", "ui-translations");
    }

    @Test
    void skipsEvictionWhenTenantIdIsMissing() {
        listener.handleTranslationChanged("{\"payload\":{\"id\":\"tr-1\"}}");

        verify(cache, never()).evict(anyString(), anyString());
    }

    @Test
    void malformedMessageDoesNotThrow() {
        listener.handleTranslationChanged("not-json");

        verify(cache, never()).evict(anyString(), anyString());
    }
}
