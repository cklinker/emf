package io.kelta.worker.listener;

import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CollectionLifecycleManager;
import io.kelta.worker.service.FormulaRecomputeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FieldConfigEventPublisher")
class FieldConfigEventPublisherTest {

    private PlatformEventPublisher eventPublisher;
    private JdbcTemplate jdbcTemplate;
    private CollectionLifecycleManager lifecycleManager;
    private CerbosAuthorizationService cerbosAuthorizationService;
    private FormulaRecomputeService formulaRecomputeService;
    private io.kelta.worker.service.SearchIndexService searchIndexService;
    private FieldConfigEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        lifecycleManager = mock(CollectionLifecycleManager.class);
        cerbosAuthorizationService = mock(CerbosAuthorizationService.class);
        formulaRecomputeService = mock(FormulaRecomputeService.class);
        searchIndexService = mock(io.kelta.worker.service.SearchIndexService.class);
        publisher = new FieldConfigEventPublisher(eventPublisher, jdbcTemplate, lifecycleManager,
                cerbosAuthorizationService, formulaRecomputeService, searchIndexService);
    }

    @Test
    @DisplayName("Should target 'fields' collection")
    void shouldTargetFields() {
        assertEquals("fields", publisher.getCollectionName());
    }

    @Test
    @DisplayName("Should have order 100 to run after schema hooks")
    void shouldHaveOrderAfterSchemaHooks() {
        assertEquals(100, publisher.getOrder());
    }

    @Test
    @DisplayName("Should publish collection UPDATED event with resolved name after field create")
    void shouldPublishOnFieldCreate() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "orders")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "type", "string",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> eventCaptor = captor();
        verify(eventPublisher).publish(eq("kelta.config.collection.changed.col-1"), eventCaptor.capture());

        CollectionChangedPayload payload = eventCaptor.getValue().getPayload();
        assertEquals("col-1", payload.getId());
        assertEquals("orders", payload.getName());

        // Read-after-write (#910): originating pod refreshed locally, AFTER the
        // NATS broadcast so other pods still receive the event.
        InOrder inOrder = inOrder(eventPublisher, lifecycleManager);
        inOrder.verify(eventPublisher).publish(anyString(), any());
        inOrder.verify(lifecycleManager).refreshOrInitializeLocally("col-1");
    }

    @Test
    @DisplayName("Should publish event with resolved name after field update")
    void shouldPublishOnFieldUpdate() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "orders")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-1"
        ));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-1"
        ));

        publisher.afterUpdate("field-1", record, previous, "tenant-1");

        ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> eventCaptor = captor();
        verify(eventPublisher).publish(eq("kelta.config.collection.changed.col-1"), eventCaptor.capture());

        CollectionChangedPayload payload = eventCaptor.getValue().getPayload();
        assertEquals("col-1", payload.getId());
        assertEquals("orders", payload.getName());

        verify(lifecycleManager).refreshOrInitializeLocally("col-1");
    }

    @Test
    @DisplayName("Should not publish when collectionId is missing")
    void shouldNotPublishWhenCollectionIdMissing() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(lifecycleManager);
    }

    @Test
    @DisplayName("Should not publish when collection name cannot be resolved")
    void shouldNotPublishWhenCollectionNameCannotBeResolved() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-missing")))
                .thenReturn(List.of());

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-missing"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
        // No broadcast → no local refresh either (refresh only alongside publish).
        verifyNoInteractions(lifecycleManager);
    }

    @Test
    @DisplayName("Should not publish when resolveCollectionName throws")
    void shouldNotPublishWhenResolveCollectionNameThrows() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenThrow(new RuntimeException("db down"));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
        verifyNoInteractions(lifecycleManager);
    }

    @Test
    @DisplayName("Should not publish on field delete (no collection context)")
    void shouldNotPublishOnFieldDelete() {
        publisher.afterDelete("field-1", "tenant-1");

        verify(eventPublisher, never()).publish(anyString(), any());
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(lifecycleManager);
    }

    @Test
    @DisplayName("Should schedule recompute when a FORMULA field is created with an expression")
    void shouldScheduleRecomputeOnFormulaCreate() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "invoices")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "total",
            "type", "FORMULA",
            "collectionId", "col-1",
            "fieldTypeConfig", Map.of("expression", "amount * 2", "returnType", "NUMBER")
        ));

        publisher.afterCreate(record, "tenant-1");

        verify(formulaRecomputeService).recomputeAsync("tenant-1", "invoices", "total");
    }

    @Test
    @DisplayName("Should not schedule recompute for non-FORMULA field creates")
    void shouldNotScheduleRecomputeForNonFormulaCreate() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "invoices")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "status",
            "type", "STRING",
            "collectionId", "col-1"
        ));

        publisher.afterCreate(record, "tenant-1");

        verifyNoInteractions(formulaRecomputeService);
    }

    @Test
    @DisplayName("Should not schedule recompute when formula has blank expression")
    void shouldNotScheduleRecomputeForBlankExpression() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "invoices")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "total",
            "type", "FORMULA",
            "collectionId", "col-1",
            "fieldTypeConfig", Map.of("expression", "", "returnType", "NUMBER")
        ));

        publisher.afterCreate(record, "tenant-1");

        verifyNoInteractions(formulaRecomputeService);
    }

    @Test
    @DisplayName("Should schedule recompute when a FORMULA field's expression changes")
    void shouldScheduleRecomputeOnExpressionChange() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "invoices")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "total",
            "type", "FORMULA",
            "collectionId", "col-1",
            "fieldTypeConfig", Map.of("expression", "amount * 3", "returnType", "NUMBER")
        ));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "total",
            "type", "FORMULA",
            "collectionId", "col-1",
            "fieldTypeConfig", Map.of("expression", "amount * 2", "returnType", "NUMBER")
        ));

        publisher.afterUpdate("field-1", record, previous, "tenant-1");

        verify(formulaRecomputeService).recomputeAsync("tenant-1", "invoices", "total");
    }

    @Test
    @DisplayName("Should rebuild the search index when a field gains a masking config")
    void shouldReindexWhenMaskingAdded() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "people")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1", "name", "ssn", "type", "STRING", "collectionId", "col-1",
            "fieldTypeConfig", Map.of("masking", Map.of("type", "LAST4"))));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "field-1", "name", "ssn", "type", "STRING", "collectionId", "col-1"));

        publisher.afterUpdate("field-1", record, previous, "tenant-1");

        verify(searchIndexService).rebuildCollectionIndexAsync("tenant-1", "people");
    }

    @Test
    @DisplayName("Should not rebuild the search index when masking is unchanged")
    void shouldNotReindexWhenMaskingUnchanged() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "people")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1", "name", "ssn", "type", "STRING", "collectionId", "col-1",
            "fieldTypeConfig", Map.of("masking", Map.of("type", "LAST4"))));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "field-1", "name", "ssn", "type", "STRING", "collectionId", "col-1",
            "fieldTypeConfig", Map.of("masking", Map.of("type", "FULL"))));

        publisher.afterUpdate("field-1", record, previous, "tenant-1");

        verify(searchIndexService, never()).rebuildCollectionIndexAsync(anyString(), anyString());
    }

    @Test
    @DisplayName("Should not schedule recompute when expression is unchanged on update")
    void shouldNotScheduleRecomputeWhenExpressionUnchanged() {
        when(jdbcTemplate.queryForList(anyString(), eq("col-1")))
                .thenReturn(List.of(Map.of("name", "invoices")));

        Map<String, Object> record = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "total",
            "type", "FORMULA",
            "collectionId", "col-1",
            "fieldTypeConfig", Map.of("expression", "amount * 2", "returnType", "NUMBER")
        ));
        Map<String, Object> previous = new HashMap<>(Map.of(
            "id", "field-1",
            "name", "total",
            "type", "FORMULA",
            "collectionId", "col-1",
            "fieldTypeConfig", Map.of("expression", "amount * 2", "returnType", "NUMBER")
        ));

        publisher.afterUpdate("field-1", record, previous, "tenant-1");

        verifyNoInteractions(formulaRecomputeService);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<PlatformEvent<CollectionChangedPayload>> captor() {
        return ArgumentCaptor.forClass(PlatformEvent.class);
    }
}
