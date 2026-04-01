package io.kelta.gateway.listener;

import tools.jackson.databind.ObjectMapper;
import io.kelta.gateway.authz.cerbos.CerbosAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CerbosCacheInvalidationListener (Gateway)")
class CerbosCacheInvalidationListenerTest {

    @Mock
    private CerbosAuthorizationService authzService;

    private CerbosCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        listener = new CerbosCacheInvalidationListener(authzService, new ObjectMapper());
    }

    @Test
    @DisplayName("Should evict cache for tenant on policy changed event")
    void evictsOnPolicyChange() {
        String message = "{\"tenantId\":\"tenant-1\",\"syncedAt\":\"2026-03-22T10:00:00Z\"}";
        listener.handlePolicyChanged(message);
        verify(authzService).evictForTenant("tenant-1");
    }

    @Test
    @DisplayName("Should handle missing tenantId gracefully")
    void handlesMissingTenantId() {
        String message = "{\"syncedAt\":\"2026-03-22T10:00:00Z\"}";
        listener.handlePolicyChanged(message);
        verify(authzService, never()).evictForTenant(any());
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void handlesMalformedJson() {
        listener.handlePolicyChanged("not json");
        verify(authzService, never()).evictForTenant(any());
    }
}
