package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayoutFieldRefreshHookTest {

    private PlatformEventPublisher publisher;
    private JdbcTemplate jdbcTemplate;
    private LayoutFieldRefreshHook hook;

    @BeforeEach
    void setUp() {
        publisher = mock(PlatformEventPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        hook = new LayoutFieldRefreshHook(publisher, jdbcTemplate);
    }

    @Test
    void getCollectionNameIsLayoutFields() {
        assertEquals("layout-fields", hook.getCollectionName());
    }

    @Test
    void afterCreateResolvesLayoutIdAndPublishes() {
        Map<String, Object> record = new HashMap<>();
        record.put("sectionId", "section-1");

        when(jdbcTemplate.queryForList(anyString(), eq("section-1")))
                .thenReturn(List.of(Map.of("layout_id", "layout-7")));

        hook.afterCreate(record, "tenant-1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("kelta.config.layout.changed.layout-7", subjectCaptor.getValue());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals("layout-7", payload.getId());
        assertEquals(ChangeType.CREATED, payload.getChangeType());
    }

    @Test
    void afterUpdateResolvesLayoutIdAndPublishes() {
        Map<String, Object> record = new HashMap<>();
        record.put("sectionId", "section-2");

        when(jdbcTemplate.queryForList(anyString(), eq("section-2")))
                .thenReturn(List.of(Map.of("layout_id", "layout-8")));

        hook.afterUpdate("field-1", record, null, "tenant-1");

        ArgumentCaptor<PlatformEvent> eventCaptor = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(publisher).publish(anyString(), eventCaptor.capture());
        CollectionChangedPayload payload = (CollectionChangedPayload) eventCaptor.getValue().getPayload();
        assertEquals(ChangeType.UPDATED, payload.getChangeType());
        assertEquals("layout-8", payload.getId());
    }

    @Test
    void missingSectionIdSkipsBroadcast() {
        hook.afterCreate(new HashMap<>(), "tenant-1");
        verify(publisher, never()).publish(anyString(), any());
    }

    @Test
    void unresolvedSectionSkipsBroadcast() {
        Map<String, Object> record = new HashMap<>();
        record.put("sectionId", "section-missing");
        when(jdbcTemplate.queryForList(anyString(), eq("section-missing")))
                .thenReturn(List.of());

        hook.afterCreate(record, "tenant-1");

        verify(publisher, never()).publish(anyString(), any());
    }

    @Test
    void afterDeleteDoesNotPublish() {
        hook.afterDelete("field-1", "tenant-1");
        verify(publisher, never()).publish(anyString(), any());
    }
}
