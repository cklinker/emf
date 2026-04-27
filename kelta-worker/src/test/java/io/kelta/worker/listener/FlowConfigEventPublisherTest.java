package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.FlowChangedPayload;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FlowConfigEventPublisher")
class FlowConfigEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private FlowConfigEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        publisher = new FlowConfigEventPublisher(eventPublisher);
    }

    @Test
    @DisplayName("Should target the 'flows' system collection")
    void shouldTargetFlows() {
        assertThat(publisher.getCollectionName()).isEqualTo("flows");
    }

    @Test
    @DisplayName("Should have order 100 so it runs after lifecycle hooks")
    void shouldRunAfterLifecycleHooks() {
        assertThat(publisher.getOrder()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should publish CREATED event with tenant-scoped subject")
    void shouldPublishCreatedEvent() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "id", "flow-1",
                "name", "Update Customer Totals on Order Change",
                "flowType", "RECORD_TRIGGERED",
                "active", true));

        publisher.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(subject.capture(), event.capture());

        assertThat(subject.getValue()).isEqualTo("kelta.config.flow.changed.tenant-1");
        assertThat(event.getValue().getTenantId()).isEqualTo("tenant-1");
        FlowChangedPayload payload = (FlowChangedPayload) event.getValue().getPayload();
        assertThat(payload.getId()).isEqualTo("flow-1");
        assertThat(payload.getName()).isEqualTo("Update Customer Totals on Order Change");
        assertThat(payload.getFlowType()).isEqualTo("RECORD_TRIGGERED");
        assertThat(payload.isActive()).isTrue();
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.CREATED);
    }

    @Test
    @DisplayName("Should publish UPDATED event with tenant-scoped subject")
    void shouldPublishUpdatedEvent() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "id", "flow-1", "name", "x", "flowType", "RECORD_TRIGGERED", "active", true));
        Map<String, Object> previous = new HashMap<>(record);

        publisher.afterUpdate("flow-1", record, previous, "tenant-1");

        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(eq("kelta.config.flow.changed.tenant-1"), event.capture());
        assertThat(((FlowChangedPayload) event.getValue().getPayload()).getChangeType())
                .isEqualTo(ChangeType.UPDATED);
    }

    @Test
    @DisplayName("Should publish DELETED event with id only")
    void shouldPublishDeletedEvent() {
        publisher.afterDelete("flow-1", "tenant-1");

        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(eq("kelta.config.flow.changed.tenant-1"), event.capture());
        FlowChangedPayload payload = (FlowChangedPayload) event.getValue().getPayload();
        assertThat(payload.getId()).isEqualTo("flow-1");
        assertThat(payload.getChangeType()).isEqualTo(ChangeType.DELETED);
    }

    @Test
    @DisplayName("Should skip publishing when the record is missing an id")
    void shouldSkipWhenIdMissing() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "name", "no-id-flow", "flowType", "RECORD_TRIGGERED", "active", true));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("Should skip publishing when tenantId is blank")
    void shouldSkipWhenTenantIdBlank() {
        Map<String, Object> record = new HashMap<>(Map.of(
                "id", "flow-1", "name", "x", "flowType", "RECORD_TRIGGERED", "active", true));

        publisher.afterCreate(record, "  ");

        verify(eventPublisher, never()).publish(anyString(), any());
    }
}
