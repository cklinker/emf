package io.kelta.gateway.listener;

import io.kelta.gateway.cache.GatewayCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomDomainCacheInvalidationListener (Gateway)")
class CustomDomainCacheInvalidationListenerTest {

    @Mock
    private GatewayCacheManager cacheManager;

    private CustomDomainCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        listener = new CustomDomainCacheInvalidationListener(cacheManager, new ObjectMapper());
    }

    @Test
    @DisplayName("Removes the specific domain when the event carries a domain in payload")
    void removesSpecificDomain() {
        String message = "{\"eventType\":\"kelta.config.domain.changed\","
                + "\"tenantId\":\"tenant-1\","
                + "\"payload\":{\"id\":\"d-1\",\"domain\":\"app.acme.com\",\"changeType\":\"UPDATED\"}}";

        listener.handleDomainChanged(message);

        verify(cacheManager).removeCustomDomain("app.acme.com");
        verify(cacheManager, never()).evictAllCustomDomains();
    }

    @Test
    @DisplayName("Falls back to evicting all when the event lacks a domain")
    void evictsAllWhenDomainMissing() {
        String message = "{\"eventType\":\"kelta.config.domain.changed\","
                + "\"tenantId\":\"tenant-1\","
                + "\"payload\":{\"id\":\"d-1\",\"changeType\":\"DELETED\"}}";

        listener.handleDomainChanged(message);

        verify(cacheManager).evictAllCustomDomains();
        verify(cacheManager, never()).removeCustomDomain(any());
    }

    @Test
    @DisplayName("Handles flat payloads (no envelope) by reading domain directly")
    void handlesFlatPayload() {
        String message = "{\"domain\":\"shop.example.com\",\"changeType\":\"CREATED\"}";

        listener.handleDomainChanged(message);

        verify(cacheManager).removeCustomDomain("shop.example.com");
    }

    @Test
    @DisplayName("Handles malformed JSON gracefully")
    void handlesMalformedJson() {
        listener.handleDomainChanged("not json");

        verifyNoInteractions(cacheManager);
    }
}
