package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CollectionDefinition} record.
 * 
 * Validates: Requirements 1.1 - Collection definition with field definitions, validation rules,
 * storage mode configuration, API configuration, authorization configuration, and event configuration
 * Validates: Requirements 1.3 - Validation of required properties when created
 */
@DisplayName("CollectionDefinition Record Tests")
class CollectionDefinitionTest {

    private static final Instant NOW = Instant.now();

    private CollectionDefinition createValidCollectionDefinition() {
        return new CollectionDefinition(
            "products",
            "Products",
            "Product catalog",
            List.of(FieldDefinition.requiredString("name"), FieldDefinition.doubleField("price")),
            StorageConfig.physicalTable("tbl_products"),
            ApiConfig.allEnabled("/api/collections/products"),
            AuthzConfig.disabled(),
            EventsConfig.disabled(),
            1L,
            NOW,
            NOW
        );
    }

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Should create collection definition with all parameters")
        void shouldCreateWithAllParameters() {
            List<FieldDefinition> fields = List.of(
                FieldDefinition.requiredString("name"),
                FieldDefinition.doubleField("price")
            );
            StorageConfig storageConfig = StorageConfig.physicalTable("tbl_products");
            ApiConfig apiConfig = ApiConfig.allEnabled("/api/collections/products");
            AuthzConfig authzConfig = AuthzConfig.withRoles(List.of("USER"), List.of("ADMIN"));
            EventsConfig eventsConfig = EventsConfig.allEvents("emf.collections");
            
            CollectionDefinition collection = new CollectionDefinition(
                "products",
                "Products",
                "Product catalog",
                fields,
                storageConfig,
                apiConfig,
                authzConfig,
                eventsConfig,
                1L,
                NOW,
                NOW
            );
            
            assertEquals("products", collection.name());
            assertEquals("Products", collection.displayName());
            assertEquals("Product catalog", collection.description());
            assertEquals(2, collection.fields().size());
            assertEquals(storageConfig, collection.storageConfig());
            assertEquals(apiConfig, collection.apiConfig());
            assertEquals(authzConfig, collection.authzConfig());
            assertEquals(eventsConfig, collection.eventsConfig());
            assertEquals(1L, collection.version());
            assertEquals(NOW, collection.createdAt());
            assertEquals(NOW, collection.updatedAt());
        }

        @Test
        @DisplayName("Should throw NullPointerException when name is null")
        void shouldThrowWhenNameIsNull() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new CollectionDefinition(
                    null,
                    "Display",
                    "Description",
                    List.of(FieldDefinition.string("field")),
                    StorageConfig.physicalTable("tbl"),
                    ApiConfig.allEnabled("/api"),
                    AuthzConfig.disabled(),
                    EventsConfig.disabled(),
                    1L,
                    NOW,
                    NOW
                );
            });
            assertEquals("name cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name is blank")
        void shouldThrowWhenNameIsBlank() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new CollectionDefinition(
                    "  ",
                    "Display",
                    "Description",
                    List.of(FieldDefinition.string("field")),
                    StorageConfig.physicalTable("tbl"),
                    ApiConfig.allEnabled("/api"),
                    AuthzConfig.disabled(),
                    EventsConfig.disabled(),
                    1L,
                    NOW,
                    NOW
                );
            });
            assertEquals("name cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw NullPointerException when fields is null")
        void shouldThrowWhenFieldsIsNull() {
            NullPointerException exception = assertThrows(NullPointerException.class, () -> {
                new CollectionDefinition(
                    "test",
                    "Test",
                    "Description",
                    null,
                    StorageConfig.physicalTable("tbl"),
                    ApiConfig.allEnabled("/api"),
                    AuthzConfig.disabled(),
                    EventsConfig.disabled(),
                    1L,
                    NOW,
                    NOW
                );
            });
            assertEquals("fields cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when fields is empty")
        void shouldThrowWhenFieldsIsEmpty() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new CollectionDefinition(
                    "test",
                    "Test",
                    "Description",
                    List.of(),
                    StorageConfig.physicalTable("tbl"),
                    ApiConfig.allEnabled("/api"),
                    AuthzConfig.disabled(),
                    EventsConfig.disabled(),
                    1L,
                    NOW,
                    NOW
                );
            });
            assertEquals("fields cannot be empty", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Defensive Copying Tests")
    class DefensiveCopyingTests {

        @Test
        @DisplayName("Should perform defensive copy of fields list")
        void shouldPerformDefensiveCopyOfFields() {
            List<FieldDefinition> mutableList = new ArrayList<>();
            mutableList.add(FieldDefinition.string("name"));
            
            CollectionDefinition collection = new CollectionDefinition(
                "test",
                "Test",
                "Description",
                mutableList,
                StorageConfig.physicalTable("tbl"),
                ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(),
                EventsConfig.disabled(),
                1L,
                NOW,
                NOW
            );
            
            // Modify original list
            mutableList.add(FieldDefinition.string("extra"));
            
            // Collection should not be affected
            assertEquals(1, collection.fields().size());
        }

        @Test
        @DisplayName("Should return immutable fields list")
        void shouldReturnImmutableFieldsList() {
            CollectionDefinition collection = createValidCollectionDefinition();
            
            assertThrows(UnsupportedOperationException.class, () -> {
                collection.fields().add(FieldDefinition.string("extra"));
            });
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("getField() should return field by name")
        void getFieldShouldReturnFieldByName() {
            CollectionDefinition collection = createValidCollectionDefinition();
            
            FieldDefinition field = collection.getField("name");
            
            assertNotNull(field);
            assertEquals("name", field.name());
            assertEquals(FieldType.STRING, field.type());
        }

        @Test
        @DisplayName("getField() should return null for non-existent field")
        void getFieldShouldReturnNullForNonExistentField() {
            CollectionDefinition collection = createValidCollectionDefinition();
            
            FieldDefinition field = collection.getField("nonexistent");
            
            assertNull(field);
        }

        @Test
        @DisplayName("hasField() should return true for existing field")
        void hasFieldShouldReturnTrueForExistingField() {
            CollectionDefinition collection = createValidCollectionDefinition();
            
            assertTrue(collection.hasField("name"));
            assertTrue(collection.hasField("price"));
        }

        @Test
        @DisplayName("hasField() should return false for non-existent field")
        void hasFieldShouldReturnFalseForNonExistentField() {
            CollectionDefinition collection = createValidCollectionDefinition();
            
            assertFalse(collection.hasField("nonexistent"));
        }

        @Test
        @DisplayName("getFieldNames() should return all field names")
        void getFieldNamesShouldReturnAllFieldNames() {
            CollectionDefinition collection = createValidCollectionDefinition();
            
            List<String> fieldNames = collection.getFieldNames();
            
            assertEquals(2, fieldNames.size());
            assertTrue(fieldNames.contains("name"));
            assertTrue(fieldNames.contains("price"));
        }
    }

    @Nested
    @DisplayName("Version Management Tests")
    class VersionManagementTests {

        @Test
        @DisplayName("withIncrementedVersion() should increment version and update timestamp")
        void withIncrementedVersionShouldIncrementVersionAndUpdateTimestamp() {
            CollectionDefinition original = createValidCollectionDefinition();
            
            CollectionDefinition updated = original.withIncrementedVersion();
            
            assertEquals(original.version() + 1, updated.version());
            assertEquals(original.name(), updated.name());
            assertEquals(original.fields(), updated.fields());
            assertEquals(original.createdAt(), updated.createdAt());
            assertTrue(updated.updatedAt().isAfter(original.createdAt()) || 
                       updated.updatedAt().equals(original.updatedAt()));
        }

        @Test
        @DisplayName("withFields() should update fields and increment version")
        void withFieldsShouldUpdateFieldsAndIncrementVersion() {
            CollectionDefinition original = createValidCollectionDefinition();
            List<FieldDefinition> newFields = List.of(
                FieldDefinition.requiredString("name"),
                FieldDefinition.doubleField("price"),
                FieldDefinition.string("description")
            );
            
            CollectionDefinition updated = original.withFields(newFields);
            
            assertEquals(3, updated.fields().size());
            assertEquals(original.version() + 1, updated.version());
            assertEquals(original.name(), updated.name());
            assertEquals(original.createdAt(), updated.createdAt());
        }
    }

    @Nested
    @DisplayName("Builder Access Tests")
    class BuilderAccessTests {

        @Test
        @DisplayName("builder() should return a new builder instance")
        void builderShouldReturnNewBuilderInstance() {
            CollectionDefinitionBuilder builder = CollectionDefinition.builder();
            
            assertNotNull(builder);
        }
    }

    @Nested
    @DisplayName("Record Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            CollectionDefinition collection1 = new CollectionDefinition(
                "test",
                "Test",
                "Description",
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"),
                ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(),
                EventsConfig.disabled(),
                1L,
                NOW,
                NOW
            );
            CollectionDefinition collection2 = new CollectionDefinition(
                "test",
                "Test",
                "Description",
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"),
                ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(),
                EventsConfig.disabled(),
                1L,
                NOW,
                NOW
            );
            
            assertEquals(collection1, collection2);
            assertEquals(collection1.hashCode(), collection2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            CollectionDefinition collection1 = new CollectionDefinition(
                "test1",
                "Test",
                "Description",
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"),
                ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(),
                EventsConfig.disabled(),
                1L,
                NOW,
                NOW
            );
            CollectionDefinition collection2 = new CollectionDefinition(
                "test2",
                "Test",
                "Description",
                List.of(FieldDefinition.string("field")),
                StorageConfig.physicalTable("tbl"),
                ApiConfig.allEnabled("/api"),
                AuthzConfig.disabled(),
                EventsConfig.disabled(),
                1L,
                NOW,
                NOW
            );
            
            assertNotEquals(collection1, collection2);
        }
    }
}
