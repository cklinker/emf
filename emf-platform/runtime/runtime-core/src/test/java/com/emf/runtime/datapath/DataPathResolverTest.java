package com.emf.runtime.datapath;

import com.emf.runtime.model.*;
import com.emf.runtime.query.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPathResolver}.
 */
class DataPathResolverTest {

    private QueryEngine queryEngine;
    private CollectionDefinitionProvider collectionProvider;
    private DataPathResolver resolver;

    private CollectionDefinition customersCollection;
    private CollectionDefinition ordersCollection;
    private CollectionDefinition lineItemsCollection;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionProvider = mock(CollectionDefinitionProvider.class);
        resolver = new DataPathResolver(queryEngine, collectionProvider);

        // Build collection definitions with relationships
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

        lineItemsCollection = new CollectionDefinitionBuilder()
            .name("line_items")
            .displayName("Line Items")
            .addField(new FieldDefinitionBuilder()
                .name("quantity").type(FieldType.INTEGER).build())
            .addField(FieldDefinition.lookup("order_id", "orders", "Order"))
            .build();

        when(collectionProvider.getByName("customers")).thenReturn(customersCollection);
        when(collectionProvider.getByName("orders")).thenReturn(ordersCollection);
        when(collectionProvider.getByName("line_items")).thenReturn(lineItemsCollection);
    }

    @Nested
    @DisplayName("Single-field resolution")
    class SingleFieldTests {

        @Test
        @DisplayName("Should resolve simple field from source record")
        void shouldResolveSimpleField() {
            Map<String, Object> record = Map.of("name", "John", "email", "john@example.com");
            DataPath path = DataPath.simple("email", "customers");

            Object result = resolver.resolve(path, record, customersCollection);

            assertEquals("john@example.com", result);
        }

        @Test
        @DisplayName("Should return null for missing field")
        void shouldReturnNullForMissingField() {
            Map<String, Object> record = Map.of("name", "John");
            DataPath path = DataPath.simple("email", "customers");

            Object result = resolver.resolve(path, record, customersCollection);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Single-hop resolution")
    class SingleHopTests {

        @Test
        @DisplayName("Should resolve through one relationship hop")
        void shouldResolveThroughOneHop() {
            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            orderRecord.put("customer_id", "cust-1");
            orderRecord.put("total", 150.0);

            Map<String, Object> customerRecord = Map.of(
                "id", "cust-1", "name", "John", "email", "john@example.com");

            when(queryEngine.getById(customersCollection, "cust-1"))
                .thenReturn(Optional.of(customerRecord));

            DataPath path = DataPath.parse("customer_id.email", "orders");
            Object result = resolver.resolve(path, orderRecord, ordersCollection);

            assertEquals("john@example.com", result);
        }

        @Test
        @DisplayName("Should return null when FK value is null")
        void shouldReturnNullWhenFkIsNull() {
            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            // customer_id is null (not set)

            DataPath path = DataPath.parse("customer_id.email", "orders");
            Object result = resolver.resolve(path, orderRecord, ordersCollection);

            assertNull(result);
            verify(queryEngine, never()).getById(any(), any());
        }

        @Test
        @DisplayName("Should return null when target record not found")
        void shouldReturnNullWhenTargetNotFound() {
            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            orderRecord.put("customer_id", "missing-cust");

            when(queryEngine.getById(customersCollection, "missing-cust"))
                .thenReturn(Optional.empty());

            DataPath path = DataPath.parse("customer_id.email", "orders");
            Object result = resolver.resolve(path, orderRecord, ordersCollection);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Multi-hop resolution")
    class MultiHopTests {

        @Test
        @DisplayName("Should resolve through multiple relationship hops")
        void shouldResolveThroughMultipleHops() {
            Map<String, Object> lineItemRecord = new HashMap<>();
            lineItemRecord.put("id", "li-1");
            lineItemRecord.put("order_id", "order-1");
            lineItemRecord.put("quantity", 3);

            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            orderRecord.put("customer_id", "cust-1");
            orderRecord.put("total", 150.0);

            Map<String, Object> customerRecord = Map.of(
                "id", "cust-1", "name", "John", "email", "john@example.com");

            when(queryEngine.getById(ordersCollection, "order-1"))
                .thenReturn(Optional.of(orderRecord));
            when(queryEngine.getById(customersCollection, "cust-1"))
                .thenReturn(Optional.of(customerRecord));

            DataPath path = DataPath.parse("order_id.customer_id.email", "line_items");
            Object result = resolver.resolve(path, lineItemRecord, lineItemsCollection);

            assertEquals("john@example.com", result);
        }

        @Test
        @DisplayName("Should return null when intermediate hop has null FK")
        void shouldReturnNullWhenIntermediateHopNull() {
            Map<String, Object> lineItemRecord = new HashMap<>();
            lineItemRecord.put("id", "li-1");
            lineItemRecord.put("order_id", "order-1");

            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            // customer_id is null

            when(queryEngine.getById(ordersCollection, "order-1"))
                .thenReturn(Optional.of(orderRecord));

            DataPath path = DataPath.parse("order_id.customer_id.email", "line_items");
            Object result = resolver.resolve(path, lineItemRecord, lineItemsCollection);

            assertNull(result);
            verify(queryEngine, never()).getById(eq(customersCollection), any());
        }
    }

    @Nested
    @DisplayName("resolveAll with shared prefix optimization")
    class ResolveAllTests {

        @Test
        @DisplayName("Should resolve multiple paths with shared prefix efficiently")
        void shouldResolveWithSharedPrefix() {
            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            orderRecord.put("customer_id", "cust-1");
            orderRecord.put("total", 150.0);

            Map<String, Object> customerRecord = Map.of(
                "id", "cust-1", "name", "John", "email", "john@example.com");

            when(queryEngine.getById(customersCollection, "cust-1"))
                .thenReturn(Optional.of(customerRecord));

            List<DataPath> paths = List.of(
                DataPath.parse("customer_id.email", "orders"),
                DataPath.parse("customer_id.name", "orders"),
                DataPath.parse("total", "orders")
            );

            Map<String, Object> results = resolver.resolveAll(
                paths, orderRecord, ordersCollection);

            assertEquals("john@example.com", results.get("customer_id.email"));
            assertEquals("John", results.get("customer_id.name"));
            assertEquals(150.0, results.get("total"));

            // Should only fetch customer record once (shared prefix)
            verify(queryEngine, times(1)).getById(customersCollection, "cust-1");
        }

        @Test
        @DisplayName("Should handle empty paths list")
        void shouldHandleEmptyPaths() {
            Map<String, Object> record = Map.of("id", "1");
            Map<String, Object> results = resolver.resolveAll(
                List.of(), record, customersCollection);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should return null for all descendants when FK is null")
        void shouldNullifyDescendantsWhenFkNull() {
            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("id", "order-1");
            // customer_id is null

            List<DataPath> paths = List.of(
                DataPath.parse("customer_id.email", "orders"),
                DataPath.parse("customer_id.name", "orders")
            );

            Map<String, Object> results = resolver.resolveAll(
                paths, orderRecord, ordersCollection);

            assertNull(results.get("customer_id.email"));
            assertNull(results.get("customer_id.name"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return null when collection not found in provider")
        void shouldReturnNullWhenCollectionNotFound() {
            // Build a collection with a lookup to a non-registered collection
            CollectionDefinition collection = new CollectionDefinitionBuilder()
                .name("test")
                .displayName("Test")
                .addField(FieldDefinition.lookup("ref_id", "nonexistent", "Ref"))
                .build();

            when(collectionProvider.getByName("nonexistent")).thenReturn(null);

            Map<String, Object> record = Map.of("ref_id", "some-id");
            DataPath path = DataPath.parse("ref_id.name", "test");

            Object result = resolver.resolve(path, record, collection);
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when field is not a relationship type")
        void shouldReturnNullWhenNotRelationship() {
            // Try to traverse through a non-relationship field
            Map<String, Object> record = Map.of("name", "Test");
            DataPath path = DataPath.parse("name.something", "customers");

            Object result = resolver.resolve(path, record, customersCollection);
            assertNull(result);
        }
    }
}
