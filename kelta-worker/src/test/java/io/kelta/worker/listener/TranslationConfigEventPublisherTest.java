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

class TranslationConfigEventPublisherTest {

    private PlatformEventPublisher publisher;
    private TranslationConfigEventPublisher hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        hook = new TranslationConfigEventPublisher(publisher);
    }

    @Test
    void collectionNameIsUiTranslations() {
        assertEquals("ui-translations", hook.getCollectionName());
    }

    @Test
    void afterCreatePublishesTenantScopedSubject() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "tr-1");
        record.put("key", "listPower.groupBy");

        hook.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.translation.changed.tenant-1", subjectCaptor.getValue());
        assertEquals("tenant-1", eventCaptor.getValue().getTenantId());
        CollectionChangedPayload payload =
                (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("tr-1", payload.getId());
        assertEquals("listPower.groupBy", payload.getName());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    void afterDeletePublishesDeletedEvent() {
        hook.afterDelete("tr-2", "tenant-1");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());
        CollectionChangedPayload payload =
                (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals(ChangeType.DELETED, payload.getChangeType());
    }

    @Test
    void missingTenantIdSkipsPublish() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "tr-1");

        hook.afterCreate(record, null);

        verify(publisher, never()).publish(anyString(), any());
    }
}
