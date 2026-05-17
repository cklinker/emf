package io.kelta.worker.listener;

import io.kelta.worker.service.email.SmtpEmailProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;

@DisplayName("TenantEmailCacheInvalidationListener")
class TenantEmailCacheInvalidationListenerTest {

    private SmtpEmailProvider provider;
    private TenantEmailCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        provider = mock(SmtpEmailProvider.class);
        listener = new TenantEmailCacheInvalidationListener(provider, new ObjectMapper());
    }

    @Test
    @DisplayName("Should evict per-tenant sender on tenant.email.changed")
    void shouldEvictTenantOnEmailChanged() {
        listener.handleTenantEmailChanged("""
                {"type":"kelta.config.tenant.email.changed",
                 "payload":{"tenantId":"tenant-42"}}""");
        verify(provider).evictTenant("tenant-42");
    }

    @Test
    @DisplayName("Should evict all senders when any credential changes")
    void shouldEvictAllOnCredentialChange() {
        listener.handleCredentialChanged("""
                {"type":"kelta.config.credential.changed",
                 "payload":{"id":"cred-1"}}""");
        verify(provider).evictAll();
    }

    @Test
    @DisplayName("Should swallow malformed messages")
    void shouldSwallowBadMessages() {
        listener.handleTenantEmailChanged("not-json");
        verifyNoInteractions(provider);
    }
}
