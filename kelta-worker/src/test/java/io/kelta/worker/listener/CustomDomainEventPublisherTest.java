package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.DomainChangedPayload;
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

@DisplayName("CustomDomainEventPublisher")
class CustomDomainEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private CustomDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        publisher = new CustomDomainEventPublisher(eventPublisher);
    }

    @Test
    @DisplayName("Should publish CREATED event with id-scoped subject")
    void shouldPublishCreatedEvent() {
        publisher.publishCreated("dom-1", "app.acme.com", "tenant-1");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(subject.capture(), event.capture());

        assertThat(subject.getValue()).isEqualTo("kelta.config.domain.changed.dom-1");
        assertThat(event.getValue().getTenantId()).isEqualTo("tenant-1");
        DomainChangedPayload payload = (DomainChangedPayload) event.getValue().getPayload();
        assertThat(payload.getId()).isEqualTo("dom-1");
        assertThat(payload.getDomain()).isEqualTo("app.acme.com");
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.CREATED);
    }

    @Test
    @DisplayName("Should publish DELETED event with the original domain attached")
    void shouldPublishDeletedEvent() {
        publisher.publishDeleted("dom-1", "app.acme.com", "tenant-1");

        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(eq("kelta.config.domain.changed.dom-1"), event.capture());

        DomainChangedPayload payload = (DomainChangedPayload) event.getValue().getPayload();
        assertThat(payload.getId()).isEqualTo("dom-1");
        assertThat(payload.getDomain()).isEqualTo("app.acme.com");
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.DELETED);
    }

    @Test
    @DisplayName("Should skip publishing when id is blank")
    void shouldSkipWhenIdBlank() {
        publisher.publishCreated("  ", "app.acme.com", "tenant-1");
        verify(eventPublisher, never()).publish(anyString(), any());
    }
}
