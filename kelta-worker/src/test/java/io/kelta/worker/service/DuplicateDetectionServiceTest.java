package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.StorageConfig;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.DuplicateDetectionService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("DuplicateDetectionService")
class DuplicateDetectionServiceTest {

    private QueryEngine queryEngine;
    private CollectionRegistry collectionRegistry;
    private RecordMaskingService recordMaskingService;
    private DuplicateDetectionService service;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionRegistry = mock(CollectionRegistry.class);
        // Default mock: maskableConfigs() returns an empty map, so no field is maskable.
        recordMaskingService = mock(RecordMaskingService.class);
        service = new DuplicateDetectionService(queryEngine, collectionRegistry, recordMaskingService);
    }

    private static final ReportExecutionService.MaskingPrincipal MASKED_VIEWER =
        new ReportExecutionService.MaskingPrincipal("viewer@acme.example", "profile-1", "tenant-1");

    private CollectionDefinition contactsDef() {
        return CollectionDefinition.builder()
                .name("contacts")
                .storageConfig(StorageConfig.physicalTable("contacts"))
                .addField(FieldDefinition.string("email"))
                .build();
    }

    private void stubQuery(List<Map<String, Object>> rows) {
        when(collectionRegistry.get("contacts")).thenReturn(contactsDef());
        when(queryEngine.executeQuery(any(CollectionDefinition.class), any(QueryRequest.class)))
                .thenReturn(QueryResult.of(rows, rows.size(), new Pagination(1, 1000)));
    }

    @Test
    @DisplayName("groups records with equal match-field values, largest first")
    void detectsDuplicates() {
        stubQuery(List.of(
                Map.of("id", "1", "email", "a@x.com"),
                Map.of("id", "2", "email", "a@x.com"),
                Map.of("id", "3", "email", "a@x.com"),
                Map.of("id", "4", "email", "b@x.com"),
                Map.of("id", "5", "email", "b@x.com"),
                Map.of("id", "6", "email", "unique@x.com")));

        Result result = service.findDuplicates("contacts", List.of("email"), 100, null);

        assertThat(result.scanned()).isEqualTo(6);
        assertThat(result.truncated()).isFalse();
        assertThat(result.groups()).hasSize(2);
        // Largest group first: a@x.com (3) then b@x.com (2).
        assertThat(result.groups().get(0).count()).isEqualTo(3);
        assertThat(result.groups().get(0).values()).containsEntry("email", "a@x.com");
        assertThat(result.groups().get(0).recordIds()).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(result.groups().get(1).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("skips rows with a null/blank match value (nulls are not duplicates)")
    void ignoresNulls() {
        java.util.Map<String, Object> nullRow1 = new java.util.HashMap<>();
        nullRow1.put("id", "1");
        nullRow1.put("email", null);
        java.util.Map<String, Object> nullRow2 = new java.util.HashMap<>();
        nullRow2.put("id", "2");
        nullRow2.put("email", null);
        stubQuery(List.of(nullRow1, nullRow2, Map.of("id", "3", "email", "")));

        Result result = service.findDuplicates("contacts", List.of("email"), 100, null);

        assertThat(result.groups()).isEmpty();
    }

    @Test
    @DisplayName("rejects empty matchFields and unknown collection")
    void validation() {
        assertThatThrownBy(() -> service.findDuplicates("contacts", List.of(), 100, null))
                .isInstanceOf(IllegalArgumentException.class);

        when(collectionRegistry.get("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.findDuplicates("missing", List.of("email"), 100, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects matching on a field masked for the requester (before any scan)")
    void rejectsMaskedMatchField() {
        when(collectionRegistry.get("contacts")).thenReturn(contactsDef());
        var cfg = new FieldMaskingService.MaskingConfig(FieldMaskingService.MaskType.FULL, '*', null);
        when(recordMaskingService.maskableConfigs(any())).thenReturn(Map.of("email", cfg));
        when(recordMaskingService.maskedFieldsFor(eq("viewer@acme.example"), eq("profile-1"),
                eq("tenant-1"), eq("contacts"), any())).thenReturn(java.util.Set.of("email"));

        assertThatThrownBy(() -> service.findDuplicates("contacts", List.of("email"), 100, MASKED_VIEWER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("masked");
        // The leak is prevented before the record scan runs.
        verify(queryEngine, never()).executeQuery(any(), any());
    }

    @Test
    @DisplayName("allows matching on a maskable field the requester may unmask")
    void allowsUnmaskableMatchField() {
        stubQuery(List.of(
                Map.of("id", "1", "email", "a@x.com"),
                Map.of("id", "2", "email", "a@x.com")));
        var cfg = new FieldMaskingService.MaskingConfig(FieldMaskingService.MaskType.FULL, '*', null);
        when(recordMaskingService.maskableConfigs(any())).thenReturn(Map.of("email", cfg));
        // Viewer may unmask email → not in the masked set → detection proceeds.
        when(recordMaskingService.maskedFieldsFor(any(), any(), any(), eq("contacts"), any()))
                .thenReturn(java.util.Set.of());

        Result result = service.findDuplicates("contacts", List.of("email"), 100, MASKED_VIEWER);

        assertThat(result.groups()).hasSize(1);
    }

    @Test
    @DisplayName("selects the match fields plus id, capped at MAX_SCAN")
    void queryShape() {
        stubQuery(List.of(Map.of("id", "1", "email", "a@x.com")));

        service.findDuplicates("contacts", List.of("email"), 100, null);

        verify(queryEngine).executeQuery(any(CollectionDefinition.class), org.mockito.ArgumentMatchers.argThat(req ->
                req.fields().contains("email") && req.fields().contains("id")
                        && req.pagination().pageSize() == DuplicateDetectionService.PAGE_SIZE));
    }
}
