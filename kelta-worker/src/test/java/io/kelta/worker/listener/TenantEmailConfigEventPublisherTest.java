package io.kelta.worker.listener;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("TenantEmailConfigEventPublisher")
class TenantEmailConfigEventPublisherTest {

    private PlatformEventPublisher publisher;
    private TenantEmailConfigEventPublisher hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        hook = new TenantEmailConfigEventPublisher(publisher);
    }

    @Test
    @DisplayName("Should publish when email columns change")
    void shouldPublishOnEmailFieldChange() {
        Map<String, Object> previous = new HashMap<>(Map.of(
                "emailSmtpCredentialId", "old", "emailFromAddress", "a@x.com"));
        Map<String, Object> updated = new HashMap<>(Map.of(
                "emailSmtpCredentialId", "new", "emailFromAddress", "a@x.com"));

        hook.afterUpdate("tenant-1", updated, previous, "tenant-1");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(subject.capture(), any(PlatformEvent.class));
        assertThat(subject.getValue()).isEqualTo("kelta.config.tenant.email.changed.tenant-1");
    }

    @Test
    @DisplayName("Should skip when only non-email fields change")
    void shouldSkipWhenOnlyOtherFieldsChange() {
        Map<String, Object> previous = new HashMap<>(Map.of("name", "Acme"));
        Map<String, Object> updated = new HashMap<>(Map.of("name", "Acme Corp"));

        hook.afterUpdate("tenant-1", updated, previous, "tenant-1");

        verify(publisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("Should always publish on delete")
    void shouldPublishOnDelete() {
        hook.afterDelete("tenant-1", "tenant-1");
        verify(publisher).publish(eq("kelta.config.tenant.email.changed.tenant-1"), any());
    }
}
