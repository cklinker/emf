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

class MenuConfigEventPublisherTest {

    private PlatformEventPublisher publisher;
    private MenuConfigEventPublisher menuHook;
    private MenuConfigEventPublisher itemHook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        menuHook = new MenuConfigEventPublisher(publisher, "ui-menus");
        itemHook = new MenuConfigEventPublisher(publisher, "ui-menu-items");
    }

    @Test
    void collectionNameComesFromTheRegistration() {
        assertEquals("ui-menus", menuHook.getCollectionName());
        assertEquals("ui-menu-items", itemHook.getCollectionName());
    }

    @Test
    void afterCreatePublishesTenantScopedSubject() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "menu-1");
        record.put("name", "Sales");

        menuHook.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.menu.changed.tenant-1", subjectCaptor.getValue());
        assertEquals("tenant-1", eventCaptor.getValue().getTenantId());
        CollectionChangedPayload payload =
                (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("menu-1", payload.getId());
        assertEquals("Sales", payload.getName());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    void afterUpdatePublishesUpdatedEvent() {
        Map<String, Object> record = new HashMap<>();
        record.put("name", "Renamed");

        itemHook.afterUpdate("item-2", record, null, "tenant-9");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.menu.changed.tenant-9", subjectCaptor.getValue());
        CollectionChangedPayload payload =
                (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("item-2", payload.getId());
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
    }

    @Test
    void afterDeletePublishesDeletedEvent() {
        menuHook.afterDelete("menu-3", "tenant-1");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());
        CollectionChangedPayload payload =
                (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("menu-3", payload.getId());
        assertEquals(ChangeType.DELETED, payload.getChangeType());
    }

    @Test
    void missingTenantIdSkipsPublish() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "menu-1");

        menuHook.afterCreate(record, null);

        verify(publisher, never()).publish(anyString(), any());
    }
}
