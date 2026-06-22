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

class UIPageConfigEventPublisherTest {

    private PlatformEventPublisher publisher;
    private UIPageConfigEventPublisher hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        hook = new UIPageConfigEventPublisher(publisher);
    }

    @Test
    void getCollectionNameIsUiPages() {
        assertEquals("ui-pages", hook.getCollectionName());
    }

    @Test
    void afterCreatePublishesCreatedEvent() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "page-1");
        record.put("name", "Home");

        hook.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.page.changed.page-1", subjectCaptor.getValue());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("page-1", payload.getId());
        assertEquals("Home", payload.getName());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    void afterUpdatePublishesUpdatedEventWithProvidedId() {
        Map<String, Object> record = new HashMap<>();
        record.put("name", "Renamed Page");

        hook.afterUpdate("page-2", record, null, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.page.changed.page-2", subjectCaptor.getValue());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("page-2", payload.getId());
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
    }

    @Test
    void afterDeletePublishesDeletedEvent() {
        hook.afterDelete("page-3", "tenant-1");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());

        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("page-3", payload.getId());
        assertEquals(ChangeType.DELETED, payload.getChangeType());
    }

    @Test
    void afterCreateWithoutIdSkipsBroadcast() {
        hook.afterCreate(new HashMap<>(), "tenant-1");
        verify(publisher, never()).publish(anyString(), any());
    }
}
