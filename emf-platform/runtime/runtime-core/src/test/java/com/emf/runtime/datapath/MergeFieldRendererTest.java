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
 * Unit tests for {@link MergeFieldRenderer}.
 */
class MergeFieldRendererTest {

    private QueryEngine queryEngine;
    private CollectionDefinitionProvider collectionProvider;
    private DataPathResolver pathResolver;
    private MergeFieldRenderer renderer;

    private CollectionDefinition customersCollection;
    private CollectionDefinition ordersCollection;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionProvider = mock(CollectionDefinitionProvider.class);
        pathResolver = new DataPathResolver(queryEngine, collectionProvider);
        renderer = new MergeFieldRenderer(pathResolver);

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
            .addField(new FieldDefinitionBuilder()
                .name("status").type(FieldType.STRING).build())
            .addField(FieldDefinition.lookup("customer_id", "customers", "Customer"))
            .build();

        when(collectionProvider.getByName("customers")).thenReturn(customersCollection);
        when(collectionProvider.getByName("orders")).thenReturn(ordersCollection);
    }

    @Nested
    @DisplayName("Simple field rendering")
    class SimpleFieldTests {

        @Test
        @DisplayName("Should render simple field merge tag")
        void shouldRenderSimpleField() {
            Map<String, Object> record = Map.of("status", "Approved", "total", 150.0);

            String result = renderer.render(
                "Order status: {{status}}", record, ordersCollection);

            assertEquals("Order status: Approved", result);
        }

        @Test
        @DisplayName("Should render multiple merge tags in same template")
        void shouldRenderMultipleTags() {
            Map<String, Object> record = Map.of("status", "Approved", "total", 150.0);

            String result = renderer.render(
                "Order {{status}} — Total: {{total}}", record, ordersCollection);

            assertEquals("Order Approved — Total: 150.0", result);
        }

        @Test
        @DisplayName("Should handle template with no merge tags")
        void shouldHandleNoTags() {
            Map<String, Object> record = Map.of("status", "Approved");

            String result = renderer.render(
                "Plain text with no tags", record, ordersCollection);

            assertEquals("Plain text with no tags", result);
        }

        @Test
        @DisplayName("Should return null template as-is")
        void shouldHandleNullTemplate() {
            Map<String, Object> record = Map.of("status", "Approved");

            String result = renderer.render(null, record, ordersCollection);

            assertNull(result);
        }

        @Test
        @DisplayName("Should return empty template as-is")
        void shouldHandleEmptyTemplate() {
            Map<String, Object> record = Map.of("status", "Approved");

            String result = renderer.render("", record, ordersCollection);

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Relationship field rendering")
    class RelationshipTests {

        @Test
        @DisplayName("Should render merge tag with relationship traversal")
        void shouldRenderRelationshipField() {
            Map<String, Object> orderRecord = new HashMap<>();
            orderRecord.put("customer_id", "cust-1");
            orderRecord.put("status", "Shipped");

            Map<String, Object> customerRecord = Map.of(
                "id", "cust-1", "name", "John", "email", "john@example.com");

            when(queryEngine.getById(customersCollection, "cust-1"))
                .thenReturn(Optional.of(customerRecord));

            String result = renderer.render(
                "Hello {{customer_id.name}}, your order is {{status}}",
                orderRecord, ordersCollection);

            assertEquals("Hello John, your order is Shipped", result);
        }

        @Test
        @DisplayName("Should replace missing value with empty string")
        void shouldReplaceMissingWithEmpty() {
            Map<String, Object> orderRecord = new HashMap<>();
            // customer_id is null — merge tag should resolve to ""

            String result = renderer.render(
                "Customer: {{customer_id.name}}", orderRecord, ordersCollection);

            assertEquals("Customer: ", result);
        }
    }

    @Nested
    @DisplayName("Whitespace handling")
    class WhitespaceTests {

        @Test
        @DisplayName("Should handle whitespace in merge tags")
        void shouldTrimWhitespace() {
            Map<String, Object> record = Map.of("status", "Done");

            String result = renderer.render(
                "Status: {{ status }}", record, ordersCollection);

            assertEquals("Status: Done", result);
        }
    }
}
