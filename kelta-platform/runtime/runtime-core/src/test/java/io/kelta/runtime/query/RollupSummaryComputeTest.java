package io.kelta.runtime.query;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinitionBuilder;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.service.RollupSummaryService;
import io.kelta.runtime.storage.StorageAdapter;
import io.kelta.runtime.storage.TableRef;
import io.kelta.runtime.validation.ValidationEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that DefaultQueryEngine reads ROLLUP_SUMMARY field config and
 * delegates to RollupSummaryService at query time.
 */
class RollupSummaryComputeTest {

    private StorageAdapter storageAdapter;
    private RollupSummaryService rollupSummaryService;
    private CollectionRegistry collectionRegistry;
    private DefaultQueryEngine queryEngine;

    @BeforeEach
    void setUp() {
        storageAdapter = mock(StorageAdapter.class);
        rollupSummaryService = mock(RollupSummaryService.class);
        collectionRegistry = mock(CollectionRegistry.class);
        queryEngine = new DefaultQueryEngine(
                storageAdapter, mock(ValidationEngine.class),
                null, null, null, rollupSummaryService,
                null, null, null, collectionRegistry);
    }

    private CollectionDefinition orderLines() {
        return new CollectionDefinitionBuilder()
                .name("orderLines")
                .displayName("Order Lines")
                .addField(new FieldDefinitionBuilder().name("amount").type(FieldType.DOUBLE).nullable(false).build())
                .build();
    }

    private CollectionDefinition ordersWithRollup(Map<String, Object> rollupCfg) {
        return new CollectionDefinitionBuilder()
                .name("orders")
                .displayName("Orders")
                .addField(new FieldDefinitionBuilder().name("id").type(FieldType.STRING).nullable(false).build())
                .addField(new FieldDefinitionBuilder()
                        .name("totalAmount")
                        .type(FieldType.ROLLUP_SUMMARY)
                        .fieldTypeConfig(rollupCfg)
                        .build())
                .build();
    }

    @Test
    void computesSumRollupForEachReturnedRecord() {
        Map<String, Object> cfg = Map.of(
                "childCollection", "orderLines",
                "foreignKeyField", "orderId",
                "aggregateFunction", "SUM",
                "aggregateField", "amount");
        CollectionDefinition orders = ordersWithRollup(cfg);

        Map<String, Object> row1 = new HashMap<>(Map.of("id", "o1"));
        Map<String, Object> row2 = new HashMap<>(Map.of("id", "o2"));
        doReturn(new QueryResult(List.of(row1, row2), new PaginationMetadata(2, 1, 50, 1)))
                .when(storageAdapter).query(eq(orders), any(QueryRequest.class));
        when(collectionRegistry.get("orderLines")).thenReturn(orderLines());
        doReturn(12.0).when(rollupSummaryService).compute(eq(TableRef.publicSchema("orderLines")), eq("order_id"), eq("o1"),
                eq("SUM"), eq("amount"), any());
        doReturn(7.0).when(rollupSummaryService).compute(eq(TableRef.publicSchema("orderLines")), eq("order_id"), eq("o2"),
                eq("SUM"), eq("amount"), any());

        QueryResult result = queryEngine.executeQuery(orders, QueryRequest.defaults());

        assertEquals(12.0, result.data().get(0).get("totalAmount"));
        assertEquals(7.0, result.data().get(1).get("totalAmount"));
    }

    @Test
    void countRollupOmitsAggregateField() {
        Map<String, Object> cfg = Map.of(
                "childCollection", "orderLines",
                "foreignKeyField", "orderId",
                "aggregateFunction", "COUNT");
        CollectionDefinition orders = ordersWithRollup(cfg);

        Map<String, Object> row = new HashMap<>(Map.of("id", "o1"));
        doReturn(new QueryResult(List.of(row), new PaginationMetadata(1, 1, 50, 1)))
                .when(storageAdapter).query(eq(orders), any(QueryRequest.class));
        when(collectionRegistry.get("orderLines")).thenReturn(orderLines());
        doReturn(3L).when(rollupSummaryService).compute(eq(TableRef.publicSchema("orderLines")), eq("order_id"), eq("o1"),
                eq("COUNT"), any(), any());

        QueryResult result = queryEngine.executeQuery(orders, QueryRequest.defaults());

        assertEquals(3L, result.data().get(0).get("totalAmount"));
    }

    @Test
    void skipsWhenChildCollectionUnknown() {
        Map<String, Object> cfg = Map.of(
                "childCollection", "missing",
                "foreignKeyField", "orderId",
                "aggregateFunction", "COUNT");
        CollectionDefinition orders = ordersWithRollup(cfg);
        Map<String, Object> row = new HashMap<>(Map.of("id", "o1"));
        doReturn(new QueryResult(List.of(row), new PaginationMetadata(1, 1, 50, 1)))
                .when(storageAdapter).query(eq(orders), any(QueryRequest.class));
        when(collectionRegistry.get("missing")).thenReturn(null);

        QueryResult result = queryEngine.executeQuery(orders, QueryRequest.defaults());

        assertNull(result.data().get(0).get("totalAmount"));
        verify(rollupSummaryService, never()).compute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsWhenFieldTypeConfigMissingRequiredKeys() {
        Map<String, Object> partialCfg = Map.of("childCollection", "orderLines"); // no FK or fn
        CollectionDefinition orders = ordersWithRollup(partialCfg);
        Map<String, Object> row = new HashMap<>(Map.of("id", "o1"));
        doReturn(new QueryResult(List.of(row), new PaginationMetadata(1, 1, 50, 1)))
                .when(storageAdapter).query(eq(orders), any(QueryRequest.class));

        QueryResult result = queryEngine.executeQuery(orders, QueryRequest.defaults());

        assertNull(result.data().get(0).get("totalAmount"));
        verify(rollupSummaryService, never()).compute(any(), any(), any(), any(), any(), any());
    }
}
