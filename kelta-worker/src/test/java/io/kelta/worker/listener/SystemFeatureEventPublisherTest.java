package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.FeatureChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SystemFeatureEventPublisher")
class SystemFeatureEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private SystemFeatureEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        publisher = new SystemFeatureEventPublisher(eventPublisher);
    }

    @Test
    @DisplayName("Should publish UPDATED event with tenant-scoped subject")
    void shouldPublishUpdatedEvent() {
        publisher.publishUpdated("tenant-1", "limits");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(subject.capture(), event.capture());

        assertThat(subject.getValue()).isEqualTo("kelta.config.feature.changed.tenant-1");
        assertThat(event.getValue().getTenantId()).isEqualTo("tenant-1");
        FeatureChangedPayload payload = (FeatureChangedPayload) event.getValue().getPayload();
        assertThat(payload.getTenantId()).isEqualTo("tenant-1");
        assertThat(payload.getScope()).isEqualTo("limits");
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.UPDATED);
    }

    @Test
    @DisplayName("Should accept explicit change type via publish()")
    void shouldAcceptExplicitChangeType() {
        publisher.publish("tenant-1", "features", ChangeType.CREATED);

        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(eq("kelta.config.feature.changed.tenant-1"), event.capture());

        FeatureChangedPayload payload = (FeatureChangedPayload) event.getValue().getPayload();
        assertThat(payload.getScope()).isEqualTo("features");
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.CREATED);
    }

    @Test
    @DisplayName("Should skip publishing when tenantId is blank")
    void shouldSkipWhenTenantIdBlank() {
        publisher.publishUpdated("  ", "limits");
        verify(eventPublisher, never()).publish(anyString(), any());
    }
}
