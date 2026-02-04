package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CollectionDefinitionBuilder}.
 * 
 * Validates: Requirements 13.1 - Builder pattern for constructing Collection_Definition objects
 * Validates: Requirements 13.2 - Builder pattern for constructing Field_Definition objects
 * Validates: Requirements 13.3 - Validation of required fields when building collection definitions
 * Validates: Requirements 13.4 - Method chaining in builder implementations
 * Validates: Requirements 13.5 - Immutable collection and field definition objects after building
 */
@DisplayName("CollectionDefinitionBuilder Tests")
class CollectionDefinitionBuilderTest {

    @Nested
    @DisplayName("Method Chaining Tests")
    class MethodChainingTests {

        @Test
        @DisplayName("All builder methods should return the builder for chaining")
        void allMethodsShouldReturnBuilderForChaining() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder();
            
            // Each method should return the same builder instance
            assertSame(builder, builder.name("test"));
            assertSame(builder, builder.displayName("Test"));
            assertSame(builder, builder.description("Description"));
            assertSame(builder, builder.addField(FieldDefinition.string("field")));
            assertSame(builder, builder.storageConfig(StorageConfig.physicalTable("tbl")));
            assertSame(builder, builder.apiConfig(ApiConfig.allEnabled("/api")));
            assertSame(builder, builder.authzConfig(AuthzConfig.disabled()));
            assertSame(builder, builder.eventsConfig(EventsConfig.disabled()));
            assertSame(builder, builder.version(1L));
            assertSame(builder, builder.createdAt(Instant.now()));
            assertSame(builder, builder.updatedAt(Instant.now()));
        }

        @Test
        @DisplayName("Should support fluent method chaining")
        void shouldSupportFluentMethodChaining() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("products")
                .displayName("Products")
                .description("Product catalog")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.doubleField("price"))
                .storageConfig(StorageConfig.physicalTable("tbl_products"))
                .apiConfig(ApiConfig.allEnabled("/api/products"))
                .authzConfig(AuthzConfig.withRoles(List.of("USER"), List.of("ADMIN")))
                .eventsConfig(EventsConfig.allEvents("emf.products"))
                .build();
            
            assertEquals("products", collection.name());
            assertEquals("Products", collection.displayName());
            assertEquals("Product catalog", collection.description());
            assertEquals(2, collection.fields().size());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw IllegalStateException when name is null")
        void shouldThrowWhenNameIsNull() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .addField(FieldDefinition.string("field"));
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Collection name is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when name is blank")
        void shouldThrowWhenNameIsBlank() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .name("   ")
                .addField(FieldDefinition.string("field"));
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Collection name is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when name is empty")
        void shouldThrowWhenNameIsEmpty() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .name("")
                .addField(FieldDefinition.string("field"));
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("Collection name is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when no fields are added")
        void shouldThrowWhenNoFieldsAdded() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .name("test");
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("At least one field is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when fields list is empty")
        void shouldThrowWhenFieldsListIsEmpty() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .name("test")
                .fields(List.of());
            
            IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
            assertEquals("At least one field is required", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should default to Mode A (PHYSICAL_TABLES) storage")
        void shouldDefaultToModeAStorage() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertNotNull(collection.storageConfig());
            assertEquals(StorageMode.PHYSICAL_TABLES, collection.storageConfig().mode());
            assertEquals("tbl_test", collection.storageConfig().tableName());
        }

        @Test
        @DisplayName("Should default to all API operations enabled")
        void shouldDefaultToAllApiOperationsEnabled() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertNotNull(collection.apiConfig());
            assertTrue(collection.apiConfig().listEnabled());
            assertTrue(collection.apiConfig().getEnabled());
            assertTrue(collection.apiConfig().createEnabled());
            assertTrue(collection.apiConfig().updateEnabled());
            assertTrue(collection.apiConfig().deleteEnabled());
            assertEquals("/api/collections/test", collection.apiConfig().basePath());
        }

        @Test
        @DisplayName("Should default to authorization disabled")
        void shouldDefaultToAuthzDisabled() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertNotNull(collection.authzConfig());
            assertFalse(collection.authzConfig().enabled());
            assertTrue(collection.authzConfig().readRoles().isEmpty());
            assertTrue(collection.authzConfig().writeRoles().isEmpty());
        }

        @Test
        @DisplayName("Should default to events disabled")
        void shouldDefaultToEventsDisabled() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertNotNull(collection.eventsConfig());
            assertFalse(collection.eventsConfig().enabled());
        }

        @Test
        @DisplayName("Should default displayName to name when not set")
        void shouldDefaultDisplayNameToName() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("products")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertEquals("products", collection.displayName());
        }

        @Test
        @DisplayName("Should default version to 1")
        void shouldDefaultVersionToOne() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertEquals(1L, collection.version());
        }

        @Test
        @DisplayName("Should set createdAt and updatedAt to current time when not specified")
        void shouldSetTimestampsToCurrentTime() {
            Instant before = Instant.now();
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            Instant after = Instant.now();
            
            assertNotNull(collection.createdAt());
            assertNotNull(collection.updatedAt());
            assertFalse(collection.createdAt().isBefore(before));
            assertFalse(collection.createdAt().isAfter(after));
            assertFalse(collection.updatedAt().isBefore(before));
            assertFalse(collection.updatedAt().isAfter(after));
        }
    }

    @Nested
    @DisplayName("Custom Configuration Tests")
    class CustomConfigurationTests {

        @Test
        @DisplayName("Should use custom storage config when provided")
        void shouldUseCustomStorageConfig() {
            StorageConfig customStorage = new StorageConfig(
                StorageMode.JSONB_STORE, 
                null, 
                Map.of("key", "value")
            );
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .storageConfig(customStorage)
                .build();
            
            assertEquals(StorageMode.JSONB_STORE, collection.storageConfig().mode());
        }

        @Test
        @DisplayName("Should use custom API config when provided")
        void shouldUseCustomApiConfig() {
            ApiConfig customApi = ApiConfig.readOnly("/custom/path");
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .apiConfig(customApi)
                .build();
            
            assertTrue(collection.apiConfig().listEnabled());
            assertTrue(collection.apiConfig().getEnabled());
            assertFalse(collection.apiConfig().createEnabled());
            assertFalse(collection.apiConfig().updateEnabled());
            assertFalse(collection.apiConfig().deleteEnabled());
            assertEquals("/custom/path", collection.apiConfig().basePath());
        }

        @Test
        @DisplayName("Should use custom authz config when provided")
        void shouldUseCustomAuthzConfig() {
            AuthzConfig customAuthz = AuthzConfig.withRoles(
                List.of("READER", "VIEWER"),
                List.of("WRITER", "ADMIN")
            );
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .authzConfig(customAuthz)
                .build();
            
            assertTrue(collection.authzConfig().enabled());
            assertEquals(List.of("READER", "VIEWER"), collection.authzConfig().readRoles());
            assertEquals(List.of("WRITER", "ADMIN"), collection.authzConfig().writeRoles());
        }

        @Test
        @DisplayName("Should use custom events config when provided")
        void shouldUseCustomEventsConfig() {
            EventsConfig customEvents = EventsConfig.allEvents("custom.prefix");
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .eventsConfig(customEvents)
                .build();
            
            assertTrue(collection.eventsConfig().enabled());
            assertEquals("custom.prefix", collection.eventsConfig().topicPrefix());
        }

        @Test
        @DisplayName("Should use custom displayName when provided")
        void shouldUseCustomDisplayName() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("products")
                .displayName("Product Catalog")
                .addField(FieldDefinition.string("field"))
                .build();
            
            assertEquals("Product Catalog", collection.displayName());
        }

        @Test
        @DisplayName("Should use custom version when provided")
        void shouldUseCustomVersion() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .version(5L)
                .build();
            
            assertEquals(5L, collection.version());
        }

        @Test
        @DisplayName("Should use custom timestamps when provided")
        void shouldUseCustomTimestamps() {
            Instant customCreated = Instant.parse("2024-01-01T00:00:00Z");
            Instant customUpdated = Instant.parse("2024-06-01T00:00:00Z");
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .createdAt(customCreated)
                .updatedAt(customUpdated)
                .build();
            
            assertEquals(customCreated, collection.createdAt());
            assertEquals(customUpdated, collection.updatedAt());
        }
    }

    @Nested
    @DisplayName("Fields Management Tests")
    class FieldsManagementTests {

        @Test
        @DisplayName("Should add multiple fields using addField()")
        void shouldAddMultipleFieldsUsingAddField() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.doubleField("price"))
                .addField(FieldDefinition.bool("active", true))
                .build();
            
            assertEquals(3, collection.fields().size());
            assertNotNull(collection.getField("name"));
            assertNotNull(collection.getField("price"));
            assertNotNull(collection.getField("active"));
        }

        @Test
        @DisplayName("Should set all fields using fields()")
        void shouldSetAllFieldsUsingFields() {
            List<FieldDefinition> fields = List.of(
                FieldDefinition.requiredString("name"),
                FieldDefinition.doubleField("price")
            );
            
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .fields(fields)
                .build();
            
            assertEquals(2, collection.fields().size());
        }

        @Test
        @DisplayName("fields() should replace previously added fields")
        void fieldsShouldReplacePreviouslyAddedFields() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("old"))
                .fields(List.of(FieldDefinition.string("new")))
                .build();
            
            assertEquals(1, collection.fields().size());
            assertNotNull(collection.getField("new"));
            assertNull(collection.getField("old"));
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Built collection should be immutable")
        void builtCollectionShouldBeImmutable() {
            CollectionDefinition collection = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field"))
                .build();
            
            // Attempting to modify the fields list should throw
            assertThrows(UnsupportedOperationException.class, () -> {
                collection.fields().add(FieldDefinition.string("extra"));
            });
        }

        @Test
        @DisplayName("Builder should create independent instances")
        void builderShouldCreateIndependentInstances() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .name("test")
                .addField(FieldDefinition.string("field1"));
            
            CollectionDefinition collection1 = builder.build();
            
            builder.addField(FieldDefinition.string("field2"));
            CollectionDefinition collection2 = builder.build();
            
            // collection1 should not be affected by subsequent builder modifications
            assertEquals(1, collection1.fields().size());
            assertEquals(2, collection2.fields().size());
        }
    }

    @Nested
    @DisplayName("Builder Reuse Tests")
    class BuilderReuseTests {

        @Test
        @DisplayName("Builder can be reused to create multiple collections")
        void builderCanBeReusedToCreateMultipleCollections() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder()
                .addField(FieldDefinition.string("field"));
            
            CollectionDefinition collection1 = builder.name("collection1").build();
            CollectionDefinition collection2 = builder.name("collection2").build();
            
            assertEquals("collection1", collection1.name());
            assertEquals("collection2", collection2.name());
        }
    }
}
