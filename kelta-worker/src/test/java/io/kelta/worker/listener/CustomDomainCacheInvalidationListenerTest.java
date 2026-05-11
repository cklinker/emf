package io.kelta.worker.listener;

import io.kelta.worker.cache.WorkerCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomDomainCacheInvalidationListener (Worker)")
class CustomDomainCacheInvalidationListenerTest {

    @Mock
    private WorkerCacheManager cacheManager;

    private CustomDomainCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        listener = new CustomDomainCacheInvalidationListener(cacheManager, new ObjectMapper());
    }

    @Test
    @DisplayName("Evicts the specific domain when the envelope payload includes 'domain'")
    void evictsSpecificDomain() {
        String message = "{\"tenantId\":\"tenant-1\",\"payload\":{\"id\":\"d-1\",\"domain\":\"app.acme.com\"}}";
        listener.handleDomainChanged(message);
        verify(cacheManager).evictCustomDomain("app.acme.com");
        verify(cacheManager, never()).evictAllCustomDomains();
    }

    @Test
    @DisplayName("Accepts a raw payload (no envelope) when 'domain' is at the top level")
    void evictsSpecificDomainFromRawPayload() {
        String message = "{\"id\":\"d-1\",\"domain\":\"app.acme.com\"}";
        listener.handleDomainChanged(message);
        verify(cacheManager).evictCustomDomain("app.acme.com");
    }

    @Test
    @DisplayName("Falls back to evicting all custom domains when 'domain' field is missing")
    void evictsAllWhenDomainFieldMissing() {
        String message = "{\"tenantId\":\"tenant-1\",\"payload\":{\"id\":\"d-1\"}}";
        listener.handleDomainChanged(message);
        verify(cacheManager).evictAllCustomDomains();
        verify(cacheManager, never()).evictCustomDomain(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Handles malformed JSON gracefully")
    void handlesMalformedJson() {
        listener.handleDomainChanged("not json");
        verify(cacheManager, never()).evictCustomDomain(org.mockito.ArgumentMatchers.anyString());
        verify(cacheManager, never()).evictAllCustomDomains();
    }
}
