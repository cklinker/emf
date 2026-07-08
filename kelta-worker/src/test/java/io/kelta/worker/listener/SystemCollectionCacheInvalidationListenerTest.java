package io.kelta.worker.listener;

import io.kelta.runtime.router.SystemCollectionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("SystemCollectionCacheInvalidationListener")
class SystemCollectionCacheInvalidationListenerTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";

    private SystemCollectionCache cache;
    private SystemCollectionCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        cache = mock(SystemCollectionCache.class);
        listener = new SystemCollectionCacheInvalidationListener(cache, new ObjectMapper());
    }

    @Test
    @DisplayName("evicts tenant and '_' entries when a system collection row changes")
    void evictsOnSystemCollectionChange() {
        listener.handleRecordChanged("""
                {"tenantId":"%s","payload":{"collectionName":"fields","recordId":"f1","changeType":"UPDATED"}}
                """.formatted(TENANT));

        verify(cache).evict(TENANT, "fields");
        verify(cache).evict(null, "fields");
    }

    @Test
    @DisplayName("ignores user-collection record events")
    void ignoresUserCollectionEvents() {
        listener.handleRecordChanged("""
                {"tenantId":"%s","payload":{"collectionName":"customers","recordId":"c1","changeType":"CREATED"}}
                """.formatted(TENANT));

        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("reads tenantId from the payload when the envelope lacks it")
    void fallsBackToPayloadTenantId() {
        listener.handleRecordChanged("""
                {"payload":{"tenantId":"%s","collectionName":"global-picklists","recordId":"p1","changeType":"UPDATED"}}
                """.formatted(TENANT));

        verify(cache).evict(TENANT, "global-picklists");
        verify(cache).evict(null, "global-picklists");
    }

    @Test
    @DisplayName("malformed events are swallowed, not thrown")
    void malformedEventDoesNotThrow() {
        listener.handleRecordChanged("not json");

        verifyNoInteractions(cache);
    }
}
