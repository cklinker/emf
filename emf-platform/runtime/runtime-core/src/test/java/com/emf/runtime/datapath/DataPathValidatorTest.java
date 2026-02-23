package com.emf.runtime.datapath;

import com.emf.runtime.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataPathValidator}.
 */
class DataPathValidatorTest {

    private CollectionDefinitionProvider collectionProvider;
    private DataPathValidator validator;

    private CollectionDefinition customersCollection;
    private CollectionDefinition ordersCollection;

    @BeforeEach
    void setUp() {
        collectionProvider = mock(CollectionDefinitionProvider.class);
        validator = new DataPathValidator(collectionProvider);

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

    @Nested
    @DisplayName("Valid paths")
    class ValidPathTests {

        @Test
        @DisplayName("Should validate simple field path")
        void shouldValidateSimpleField() {
            DataPath path = DataPath.parse("total", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertTrue(result.valid());
            assertEquals("total", result.terminalFieldName());
            assertEquals(FieldType.DOUBLE, result.terminalFieldType());
            assertEquals("orders", result.terminalCollectionName());
        }

        @Test
        @DisplayName("Should validate single-hop relationship path")
        void shouldValidateSingleHop() {
            DataPath path = DataPath.parse("customer_id.email", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertTrue(result.valid());
            assertEquals("email", result.terminalFieldName());
            assertEquals(FieldType.STRING, result.terminalFieldType());
            assertEquals("customers", result.terminalCollectionName());
        }

        @Test
        @DisplayName("Should validate system field as terminal")
        void shouldValidateSystemField() {
            DataPath path = DataPath.parse("customer_id.id", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertTrue(result.valid());
            assertEquals("id", result.terminalFieldName());
            assertEquals("customers", result.terminalCollectionName());
        }

        @Test
        @DisplayName("Should validate createdAt system field")
        void shouldValidateCreatedAtSystemField() {
            DataPath path = DataPath.parse("createdAt", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertTrue(result.valid());
            assertEquals("createdAt", result.terminalFieldName());
        }
    }

    @Nested
    @DisplayName("Invalid paths")
    class InvalidPathTests {

        @Test
        @DisplayName("Should fail when root collection not found")
        void shouldFailWhenRootNotFound() {
            when(collectionProvider.getByName("nonexistent")).thenReturn(null);
            DataPath path = DataPath.parse("name", "nonexistent");

            DataPathValidationResult result = validator.validate(path);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("not found"));
        }

        @Test
        @DisplayName("Should fail when intermediate field not found")
        void shouldFailWhenFieldNotFound() {
            DataPath path = DataPath.parse("missing_field.email", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("missing_field"));
            assertTrue(result.errorMessage().contains("not found"));
        }

        @Test
        @DisplayName("Should fail when intermediate field is not a relationship")
        void shouldFailWhenNotRelationship() {
            DataPath path = DataPath.parse("total.something", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("not a relationship field"));
        }

        @Test
        @DisplayName("Should fail when target collection not found")
        void shouldFailWhenTargetCollectionNotFound() {
            // Create a collection with a lookup to unregistered collection
            CollectionDefinition withBrokenRef = new CollectionDefinitionBuilder()
                .name("broken")
                .displayName("Broken")
                .addField(FieldDefinition.lookup("bad_ref", "missing_collection", "Bad"))
                .build();

            when(collectionProvider.getByName("broken")).thenReturn(withBrokenRef);
            when(collectionProvider.getByName("missing_collection")).thenReturn(null);

            DataPath path = DataPath.parse("bad_ref.name", "broken");

            DataPathValidationResult result = validator.validate(path);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("missing_collection"));
        }

        @Test
        @DisplayName("Should fail when terminal field not found")
        void shouldFailWhenTerminalNotFound() {
            DataPath path = DataPath.parse("customer_id.nonexistent", "orders");

            DataPathValidationResult result = validator.validate(path);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("nonexistent"));
        }
    }
}
