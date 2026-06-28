package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FormulaRecomputeService")
class FormulaRecomputeServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private CollectionRegistry collectionRegistry;
    @Mock
    private StorageAdapter storageAdapter;

    private FormulaEvaluator formulaEvaluator;
    private FormulaRecomputeService service;

    @BeforeEach
    void setUp() {
        formulaEvaluator = new FormulaEvaluator(List.of());
        service = new FormulaRecomputeService(jdbcTemplate, collectionRegistry,
                storageAdapter, formulaEvaluator);
    }

    private static FieldDefinition formula(String name, String expression) {
        Map<String, Object> config = new HashMap<>();
        config.put("expression", expression);
        config.put("returnType", "NUMBER");
        return new FieldDefinition(name, FieldType.FORMULA,
                true, false, false, null, null, null, null, config, null);
    }

    private static CollectionDefinition userCollection(String name, FieldDefinition... extras) {
        CollectionDefinitionBuilder builder = new CollectionDefinitionBuilder().name(name)
                .systemCollection(false)
                .addField(FieldDefinition.doubleField("amount"))
                .addField(FieldDefinition.doubleField("quantity"));
        for (FieldDefinition f : extras) {
            builder.addField(f);
        }
        return builder.build();
    }

    @Test
    @DisplayName("blank arguments are ignored")
    void blankArgsIgnored() {
        service.recomputeAsync("", "invoices", "total");
        service.recomputeAsync("tenant-1", "", "total");
        service.recomputeAsync("tenant-1", "invoices", "");
        verifyNoInteractions(jdbcTemplate, collectionRegistry, storageAdapter);
    }

    @Test
    @DisplayName("missing collection logs and skips without touching storage")
    void missingCollection() {
        when(collectionRegistry.get("invoices")).thenReturn(null);

        service.recomputeAsync("tenant-1", "invoices", "total");

        verify(storageAdapter, never()).query(any(), any());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("field is not a FORMULA → skipped")
    void notFormulaField() {
        when(collectionRegistry.get("invoices")).thenReturn(userCollection("invoices"));

        service.recomputeAsync("tenant-1", "invoices", "amount");

        verify(storageAdapter, never()).query(any(), any());
    }

    @Test
    @DisplayName("formula expression blank → no work")
    void blankExpression() {
        FieldDefinition blank = formula("total", "");
        when(collectionRegistry.get("invoices")).thenReturn(userCollection("invoices", blank));

        service.recomputeAsync("tenant-1", "invoices", "total");

        verify(storageAdapter, never()).query(any(), any());
    }

    @Test
    @DisplayName("single page: evaluates each record and issues one batch UPDATE")
    void singlePage() throws Exception {
        FieldDefinition total = formula("total", "amount * quantity");
        when(collectionRegistry.get("invoices")).thenReturn(userCollection("invoices", total));
        when(storageAdapter.query(any(), any())).thenReturn(
                resultOf(record("r1", 10.0, 2),
                         record("r2", 5.0, 3)));

        TenantContext.runWithTenant("tenant-1", "acme",
                () -> service.recomputeAsync("tenant-1", "invoices", "total"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batch = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), batch.capture());
        assertThat(batch.getValue()).hasSize(2);
        assertThat(batch.getValue().get(0)[0]).isEqualTo("r1");
        assertThat(batch.getValue().get(1)[0]).isEqualTo("r2");
    }

    @Test
    @DisplayName("multi-page: pages of PAGE_SIZE drive additional queries")
    void multiPage() throws Exception {
        FieldDefinition total = formula("total", "amount * quantity");
        when(collectionRegistry.get("invoices")).thenReturn(userCollection("invoices", total));

        List<Map<String, Object>> full = new ArrayList<>();
        for (int i = 0; i < FormulaRecomputeService.PAGE_SIZE; i++) {
            full.add(record("r" + i, 1.0, i));
        }
        when(storageAdapter.query(any(), any()))
                .thenReturn(resultOf(full))
                .thenReturn(resultOf(record("rTail", 1.0, 1)));

        TenantContext.runWithTenant("tenant-1", "acme",
                () -> service.recomputeAsync("tenant-1", "invoices", "total"));

        // Page 1 (full) + page 2 (partial) => two queries, two batchUpdates.
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);
        verify(storageAdapter, times(2)).query(any(), requests.capture());
        assertThat(requests.getAllValues().get(0).pagination().pageNumber()).isEqualTo(1);
        assertThat(requests.getAllValues().get(1).pagination().pageNumber()).isEqualTo(2);
        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("empty result terminates without issuing an UPDATE")
    void emptyFirstPage() throws Exception {
        FieldDefinition total = formula("total", "amount * quantity");
        when(collectionRegistry.get("invoices")).thenReturn(userCollection("invoices", total));
        when(storageAdapter.query(any(), any())).thenReturn(resultOf());

        TenantContext.runWithTenant("tenant-1", "acme",
                () -> service.recomputeAsync("tenant-1", "invoices", "total"));

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("evaluation failure on a record does not abort the batch")
    void evaluationFailureIsSwallowed() throws Exception {
        FieldDefinition total = formula("total", "amount / quantity");
        when(collectionRegistry.get("invoices")).thenReturn(userCollection("invoices", total));
        when(storageAdapter.query(any(), any())).thenReturn(
                resultOf(record("r1", 10.0, 0),    // divide-by-zero
                         record("r2", 6.0, 2)));

        TenantContext.runWithTenant("tenant-1", "acme",
                () -> service.recomputeAsync("tenant-1", "invoices", "total"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batch = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), batch.capture());
        assertThat(batch.getValue()).hasSize(2);
    }

    private static Map<String, Object> record(String id, double amount, int quantity) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("amount", amount);
        r.put("quantity", quantity);
        return r;
    }

    @SafeVarargs
    private static QueryResult resultOf(Map<String, Object>... records) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : records) {
            list.add(r);
        }
        return resultOf(list);
    }

    private static QueryResult resultOf(List<Map<String, Object>> records) {
        int size = records.size();
        int pageSize = Math.max(size, 1);
        return new QueryResult(records,
                new PaginationMetadata(size, 1, pageSize, size == 0 ? 1 : 1));
    }
}
