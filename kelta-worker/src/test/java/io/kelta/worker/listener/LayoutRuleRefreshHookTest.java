package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LayoutRuleRefreshHookTest {

    private PlatformEventPublisher publisher;
    private LayoutRuleRefreshHook hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        hook = new LayoutRuleRefreshHook(publisher);
    }

    @Test
    void getCollectionNameIsLayoutRules() {
        assertEquals("layout-rules", hook.getCollectionName());
    }

    @Test
    void orderIsAfterAuditHooks() {
        assertEquals(200, hook.getOrder());
    }

    @Test
    void afterCreatePublishesLayoutChangedEvent() {
        Map<String, Object> record = new HashMap<>();
        record.put("layoutId", "layout-123");
        record.put("name", "Line total");

        hook.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.layout.changed.layout-123", subjectCaptor.getValue());
        PlatformEvent<?> event = eventCaptor.getValue();
        assertEquals("tenant-1", event.getTenantId());
        CollectionChangedPayload payload = (CollectionChangedPayload) event.getPayload();
        assertEquals("layout-123", payload.getId());
        assertEquals("Line total", payload.getName());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    void afterUpdatePublishesUpdatedEvent() {
        Map<String, Object> record = new HashMap<>();
        record.put("layoutId", "layout-456");
        record.put("name", "Discount cap");

        hook.afterUpdate("rule-789", record, null, "tenant-2");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());

        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
        assertEquals("layout-456", payload.getId());
    }

    @Test
    void missingLayoutIdSkipsBroadcast() {
        Map<String, Object> record = new HashMap<>();
        record.put("name", "no layout id");

        hook.afterCreate(record, "tenant-1");

        verify(publisher, never()).publish(anyString(), any());
    }

    @Test
    void afterDeleteDoesNotPublish() {
        hook.afterDelete("rule-1", "tenant-1");
        verify(publisher, never()).publish(anyString(), any());
    }

    @Test
    void missingNameStillPublishesWithNullName() {
        Map<String, Object> record = new HashMap<>();
        record.put("layoutId", "layout-1");

        hook.afterCreate(record, "tenant-1");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("layout-1", payload.getId());
        assertEquals(null, payload.getName());
    }
}
