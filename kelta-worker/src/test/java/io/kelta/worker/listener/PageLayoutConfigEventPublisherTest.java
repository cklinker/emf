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

class PageLayoutConfigEventPublisherTest {

    private PlatformEventPublisher publisher;
    private PageLayoutConfigEventPublisher hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        hook = new PageLayoutConfigEventPublisher(publisher);
    }

    @Test
    void getCollectionNameIsPageLayouts() {
        assertEquals("page-layouts", hook.getCollectionName());
    }

    @Test
    void afterCreatePublishesCreatedEvent() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "layout-1");
        record.put("name", "Account Detail");

        hook.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.layout.changed.layout-1", subjectCaptor.getValue());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("layout-1", payload.getId());
        assertEquals("Account Detail", payload.getName());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    void afterUpdatePublishesUpdatedEventWithProvidedId() {
        Map<String, Object> record = new HashMap<>();
        record.put("name", "Renamed Layout");

        hook.afterUpdate("layout-2", record, null, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.layout.changed.layout-2", subjectCaptor.getValue());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("layout-2", payload.getId());
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
    }

    @Test
    void afterDeletePublishesDeletedEvent() {
        hook.afterDelete("layout-3", "tenant-1");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());

        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("layout-3", payload.getId());
        assertEquals(ChangeType.DELETED, payload.getChangeType());
    }

    @Test
    void afterCreateWithoutIdSkipsBroadcast() {
        hook.afterCreate(new HashMap<>(), "tenant-1");
        verify(publisher, never()).publish(anyString(), any());
    }
}
