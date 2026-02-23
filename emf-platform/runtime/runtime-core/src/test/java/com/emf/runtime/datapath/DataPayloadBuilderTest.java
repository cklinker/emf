package com.emf.runtime.datapath;

import com.emf.runtime.model.*;
import com.emf.runtime.query.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPayloadBuilder}.
 */
class DataPayloadBuilderTest {

    private QueryEngine queryEngine;
    private CollectionDefinitionProvider collectionProvider;
    private DataPathResolver pathResolver;
    private DataPayloadBuilder builder;

    private CollectionDefinition customersCollection;
    private CollectionDefinition ordersCollection;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionProvider = mock(CollectionDefinitionProvider.class);
        pathResolver = new DataPathResolver(queryEngine, collectionProvider);
        builder = new DataPayloadBuilder(pathResolver);

        customersCollection = new CollectionDefinitionBuilder()
            .name("customers")
            .displayName("Customers")
            .addField(new FieldDefinitionBuilder()
                .name("name").type(FieldType.STRING).build())
            .addField(new FieldDefinitionBuilder()
                .name("email").type(FieldType.STRING).build())
            .build();

        ordersCollection = new CollectionDefinitionBuilder()
            .name("orders")
            .displayName("Orders")
            .addField(new FieldDefinitionBuilder()
                .name("total").type(FieldType.DOUBLE).build())
            .addField(FieldDefinition.lookup("customer_id", "customers", "Customer"))
            .build();

        when(collectionProvider.getByName("customers")).thenReturn(customersCollection);
        when(collectionProvider.getByName("orders")).thenReturn(ordersCollection);
    }

    @Test
    @DisplayName("Should build payload from multiple DataPath fields")
    void shouldBuildPayload() {
        Map<String, Object> orderRecord = new HashMap<>();
        orderRecord.put("id", "order-1");
        orderRecord.put("customer_id", "cust-1");
        orderRecord.put("total", 150.0);

        Map<String, Object> customerRecord = Map.of(
            "id", "cust-1", "name", "John", "email", "john@example.com");

        when(queryEngine.getById(customersCollection, "cust-1"))
            .thenReturn(Optional.of(customerRecord));

        List<DataPayloadBuilder.DataPayloadField> fields = List.of(
            new DataPayloadBuilder.DataPayloadField("customerEmail", "customer_id.email"),
            new DataPayloadBuilder.DataPayloadField("customerName", "customer_id.name"),
            new DataPayloadBuilder.DataPayloadField("orderTotal", "total")
        );

        Map<String, Object> payload = builder.buildPayload(
            fields, orderRecord, ordersCollection);

        assertEquals("john@example.com", payload.get("customerEmail"));
        assertEquals("John", payload.get("customerName"));
        assertEquals(150.0, payload.get("orderTotal"));
    }

    @Test
    @DisplayName("Should handle null resolved values in payload")
    void shouldHandleNullValues() {
        Map<String, Object> orderRecord = new HashMap<>();
        orderRecord.put("id", "order-1");
        // customer_id is null

        List<DataPayloadBuilder.DataPayloadField> fields = List.of(
            new DataPayloadBuilder.DataPayloadField("customerEmail", "customer_id.email")
        );

        Map<String, Object> payload = builder.buildPayload(
            fields, orderRecord, ordersCollection);

        assertTrue(payload.containsKey("customerEmail"));
        assertNull(payload.get("customerEmail"));
    }

    @Test
    @DisplayName("Should return empty map for empty fields list")
    void shouldHandleEmptyFields() {
        Map<String, Object> record = Map.of("id", "1");

        Map<String, Object> payload = builder.buildPayload(
            List.of(), record, ordersCollection);

        assertTrue(payload.isEmpty());
    }
}
