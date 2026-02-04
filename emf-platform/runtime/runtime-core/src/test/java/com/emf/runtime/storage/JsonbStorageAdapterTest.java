package com.emf.runtime.storage;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.StorageConfig;
import com.emf.runtime.model.StorageMode;
import com.emf.runtime.query.FilterCondition;
import com.emf.runtime.query.FilterOperator;
import com.emf.runtime.query.Pagination;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.query.SortField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonbStorageAdapter.
 * 
 * <p>Uses H2 in-memory database for testing. Note that H2 has limited JSONB support
 * compared to PostgreSQL, so some tests use H2's JSON functions which are similar
 * but not identical to PostgreSQL's JSONB operators.
 * 
 * <p>For full JSONB functionality testing, integration tests with PostgreSQL
 * via Testcontainers should be used.
 */
class JsonbStorageAdapterTest {
    
    private JdbcTemplate jdbcTemplate;
    private JsonbStorageAdapter adapter;
    private CollectionDefinition testCollection;
    
    @BeforeEach
    void setUp() {
        // Create embedded H2 database with PostgreSQL compatibility mode
        DataSource dataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:h2-jsonb-init.sql")
            .build();
        
        jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Drop table if exists (for clean test runs)
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS emf_collections");
        } catch (Exception e) {
            // Ignore
        }
        
        adapter = new JsonbStorageAdapter(jdbcTemplate);
        
        // Create a test collection definition
        testCollection = createTestCollection("test_products");
    }
    
    private CollectionDefinition createTestCollection(String name) {
        List<FieldDefinition> fields = List.of(
            new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
            new FieldDefinition("description", FieldType.STRING, true, false, false, null, null, null, null),
            new FieldDefinition("price", FieldType.DOUBLE, false, false, false, null, null, null, null),
            new FieldDefinition("quantity", FieldType.INTEGER, true, false, false, null, null, null, null),
            new FieldDefinition("active", FieldType.BOOLEAN, true, false, false, null, null, null, null),
            new FieldDefinition("sku", FieldType.STRING, false, false, true, null, null, null, null)
        );
        
        StorageConfig storageConfig = StorageConfig.jsonbStore();
        
        return new CollectionDefinition(
            name,
            "Test Products",
            "A test collection for products",
            fields,
            storageConfig,
            null,
            null,
            null,
            1L,
            Instant.now(),
            Instant.now()
        );
    }
    
    @Nested
    @DisplayName("Table Initialization Tests")
    class InitializationTests {
        
        @Test
        @DisplayName("Should create shared table with correct structure")
        void shouldCreateSharedTableWithCorrectStructure() {
            adapter.initializeCollection(testCollection);
            
            // Verify table exists by querying it
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emf_collections", Integer.class);
            assertEquals(0, count);
        }
        
        @Test
        @DisplayName("Should be idempotent - calling twice should not fail")
        void shouldBeIdempotent() {
            adapter.initializeCollection(testCollection);
            assertDoesNotThrow(() -> adapter.initializeCollection(testCollection));
        }
        
        @Test
        @DisplayName("Should use same table for different collections")
        void shouldUseSameTableForDifferentCollections() {
            CollectionDefinition collection1 = createTestCollection("collection1");
            CollectionDefinition collection2 = createTestCollection("collection2");
            
            adapter.initializeCollection(collection1);
            adapter.initializeCollection(collection2);
            
            // Both should use the same table
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emf_collections", Integer.class);
            assertEquals(0, count);
        }
        
        @Test
        @DisplayName("Schema update should be no-op for JSONB mode")
        void schemaUpdateShouldBeNoOp() {
            adapter.initializeCollection(testCollection);
            
            // Add a new field to the collection
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("price", FieldType.DOUBLE, false, false, false, null, null, null, null),
                new FieldDefinition("newField", FieldType.STRING, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition newDefinition = new CollectionDefinition(
                testCollection.name(),
                testCollection.displayName(),
                testCollection.description(),
                newFields,
                testCollection.storageConfig(),
                null, null, null,
                2L, testCollection.createdAt(), Instant.now()
            );
            
            // Should not throw - schema updates are no-op for JSONB
            assertDoesNotThrow(() -> adapter.updateCollectionSchema(testCollection, newDefinition));
        }
    }
    
    @Nested
    @DisplayName("CRUD Operations Tests")
    class CrudTests {
        
        @BeforeEach
        void initTable() {
            adapter.initializeCollection(testCollection);
        }
        
        @Test
        @DisplayName("Should create a record")
        void shouldCreateRecord() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            
            Map<String, Object> created = adapter.create(testCollection, data);
            
            assertNotNull(created);
            assertEquals("1", created.get("id"));
            assertEquals("Product A", created.get("name"));
        }
        
        @Test
        @DisplayName("Should get record by ID")
        void shouldGetRecordById() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            adapter.create(testCollection, data);
            
            Optional<Map<String, Object>> result = adapter.getById(testCollection, "1");
            
            assertTrue(result.isPresent());
            assertEquals("Product A", result.get().get("name"));
            assertEquals(99.99, result.get().get("price"));
        }
        
        @Test
        @DisplayName("Should return empty for non-existent ID")
        void shouldReturnEmptyForNonExistentId() {
            Optional<Map<String, Object>> result = adapter.getById(testCollection, "non-existent");
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should return empty for wrong collection")
        void shouldReturnEmptyForWrongCollection() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            adapter.create(testCollection, data);
            
            // Create a different collection
            CollectionDefinition otherCollection = createTestCollection("other_collection");
            adapter.initializeCollection(otherCollection);
            
            // Should not find the record in the other collection
            Optional<Map<String, Object>> result = adapter.getById(otherCollection, "1");
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should update a record")
        void shouldUpdateRecord() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            adapter.create(testCollection, data);
            
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Updated Product");
            updateData.put("price", 149.99);
            updateData.put("updatedAt", Instant.now());
            
            Optional<Map<String, Object>> updated = adapter.update(testCollection, "1", updateData);
            
            assertTrue(updated.isPresent());
            assertEquals("Updated Product", updated.get().get("name"));
            assertEquals(149.99, updated.get().get("price"));
        }
        
        @Test
        @DisplayName("Should preserve unmodified fields on update")
        void shouldPreserveUnmodifiedFieldsOnUpdate() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            data.put("quantity", 100);
            adapter.create(testCollection, data);
            
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Updated Product");
            updateData.put("updatedAt", Instant.now());
            
            Optional<Map<String, Object>> updated = adapter.update(testCollection, "1", updateData);
            
            assertTrue(updated.isPresent());
            assertEquals("Updated Product", updated.get().get("name"));
            assertEquals(99.99, updated.get().get("price")); // Preserved
            assertEquals(100, updated.get().get("quantity")); // Preserved
            assertEquals("SKU001", updated.get().get("sku")); // Preserved
        }
        
        @Test
        @DisplayName("Should return empty when updating non-existent record")
        void shouldReturnEmptyWhenUpdatingNonExistent() {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Updated Product");
            updateData.put("updatedAt", Instant.now());
            
            Optional<Map<String, Object>> result = adapter.update(testCollection, "non-existent", updateData);
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should delete a record")
        void shouldDeleteRecord() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            adapter.create(testCollection, data);
            
            boolean deleted = adapter.delete(testCollection, "1");
            
            assertTrue(deleted);
            assertTrue(adapter.getById(testCollection, "1").isEmpty());
        }
        
        @Test
        @DisplayName("Should return false when deleting non-existent record")
        void shouldReturnFalseWhenDeletingNonExistent() {
            boolean deleted = adapter.delete(testCollection, "non-existent");
            
            assertFalse(deleted);
        }
        
        @Test
        @DisplayName("Should only delete from correct collection")
        void shouldOnlyDeleteFromCorrectCollection() {
            Map<String, Object> data = createTestRecord("1", "Product A", 99.99, "SKU001");
            adapter.create(testCollection, data);
            
            // Create a different collection
            CollectionDefinition otherCollection = createTestCollection("other_collection");
            adapter.initializeCollection(otherCollection);
            
            // Try to delete from wrong collection
            boolean deleted = adapter.delete(otherCollection, "1");
            
            assertFalse(deleted);
            // Original record should still exist
            assertTrue(adapter.getById(testCollection, "1").isPresent());
        }
        
        private Map<String, Object> createTestRecord(String id, String name, double price, String sku) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("price", price);
            data.put("sku", sku);
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            return data;
        }
    }
    
    @Nested
    @DisplayName("Query Tests")
    class QueryTests {
        
        @BeforeEach
        void initTableAndData() {
            adapter.initializeCollection(testCollection);
            
            // Insert test data
            insertTestData("1", "Apple", 1.99, 100, true, "SKU001");
            insertTestData("2", "Banana", 0.99, 200, true, "SKU002");
            insertTestData("3", "Cherry", 3.99, 50, false, "SKU003");
            insertTestData("4", "Date", 5.99, 30, true, "SKU004");
            insertTestData("5", "Elderberry", 7.99, 10, false, "SKU005");
        }
        
        private void insertTestData(String id, String name, double price, int quantity, 
                boolean active, String sku) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("price", price);
            data.put("quantity", quantity);
            data.put("active", active);
            data.put("sku", sku);
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            adapter.create(testCollection, data);
        }
        
        @Test
        @DisplayName("Should query all records with default pagination")
        void shouldQueryAllRecordsWithDefaultPagination() {
            QueryRequest request = QueryRequest.defaults();
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(5, result.data().size());
            assertEquals(5, result.metadata().totalCount());
        }
        
        @Test
        @DisplayName("Should apply pagination correctly")
        void shouldApplyPaginationCorrectly() {
            QueryRequest request = new QueryRequest(
                new Pagination(1, 2),
                List.of(),
                List.of(),
                List.of()
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size());
            assertEquals(5, result.metadata().totalCount());
            assertEquals(3, result.metadata().totalPages());
        }
        
        @Test
        @DisplayName("Should return empty for page beyond data")
        void shouldReturnEmptyForPageBeyondData() {
            QueryRequest request = new QueryRequest(
                new Pagination(10, 20),
                List.of(),
                List.of(),
                List.of()
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(0, result.data().size());
            assertEquals(5, result.metadata().totalCount());
        }
        
        @Test
        @DisplayName("Should only query records from specified collection")
        void shouldOnlyQueryRecordsFromSpecifiedCollection() {
            // Create another collection with data
            CollectionDefinition otherCollection = createTestCollection("other_collection");
            adapter.initializeCollection(otherCollection);
            
            Map<String, Object> otherData = new HashMap<>();
            otherData.put("id", "other-1");
            otherData.put("name", "Other Product");
            otherData.put("price", 999.99);
            otherData.put("sku", "OTHER001");
            otherData.put("createdAt", Instant.now());
            otherData.put("updatedAt", Instant.now());
            adapter.create(otherCollection, otherData);
            
            // Query original collection
            QueryRequest request = QueryRequest.defaults();
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(5, result.data().size());
            
            // Query other collection
            QueryResult otherResult = adapter.query(otherCollection, request);
            assertEquals(1, otherResult.data().size());
        }
        
        @Test
        @DisplayName("Should apply field selection")
        void shouldApplyFieldSelection() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of("name", "price"),
                List.of()
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(5, result.data().size());
            Map<String, Object> firstRecord = result.data().get(0);
            
            // Should have id (always included), name, price, and system fields
            assertTrue(firstRecord.containsKey("id"));
            assertTrue(firstRecord.containsKey("name"));
            assertTrue(firstRecord.containsKey("price"));
            // Should not have other fields
            assertFalse(firstRecord.containsKey("quantity"));
            assertFalse(firstRecord.containsKey("sku"));
        }
    }
    
    @Nested
    @DisplayName("Uniqueness Tests")
    class UniquenessTests {
        
        @BeforeEach
        void initTable() {
            adapter.initializeCollection(testCollection);
        }
        
        @Test
        @DisplayName("Should return true for unique value")
        void shouldReturnTrueForUniqueValue() {
            Map<String, Object> data = new HashMap<>();
            data.put("id", "1");
            data.put("name", "Product A");
            data.put("price", 99.99);
            data.put("sku", "SKU001");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            adapter.create(testCollection, data);
            
            boolean isUnique = adapter.isUnique(testCollection, "sku", "SKU002", null);
            
            assertTrue(isUnique);
        }
        
        @Test
        @DisplayName("Should return false for duplicate value")
        void shouldReturnFalseForDuplicateValue() {
            Map<String, Object> data = new HashMap<>();
            data.put("id", "1");
            data.put("name", "Product A");
            data.put("price", 99.99);
            data.put("sku", "SKU001");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            adapter.create(testCollection, data);
            
            boolean isUnique = adapter.isUnique(testCollection, "sku", "SKU001", null);
            
            assertFalse(isUnique);
        }
        
        @Test
        @DisplayName("Should exclude specified ID when checking uniqueness")
        void shouldExcludeSpecifiedIdWhenCheckingUniqueness() {
            Map<String, Object> data = new HashMap<>();
            data.put("id", "1");
            data.put("name", "Product A");
            data.put("price", 99.99);
            data.put("sku", "SKU001");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            adapter.create(testCollection, data);
            
            // Should be unique when excluding the record's own ID
            boolean isUnique = adapter.isUnique(testCollection, "sku", "SKU001", "1");
            
            assertTrue(isUnique);
        }
        
        @Test
        @DisplayName("Should check uniqueness within collection only")
        void shouldCheckUniquenessWithinCollectionOnly() {
            Map<String, Object> data = new HashMap<>();
            data.put("id", "1");
            data.put("name", "Product A");
            data.put("price", 99.99);
            data.put("sku", "SKU001");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            adapter.create(testCollection, data);
            
            // Create another collection
            CollectionDefinition otherCollection = createTestCollection("other_collection");
            adapter.initializeCollection(otherCollection);
            
            // Same SKU should be unique in the other collection
            boolean isUnique = adapter.isUnique(otherCollection, "sku", "SKU001", null);
            
            assertTrue(isUnique);
        }
    }
    
    @Nested
    @DisplayName("System Fields Tests")
    class SystemFieldsTests {
        
        @BeforeEach
        void initTable() {
            adapter.initializeCollection(testCollection);
        }
        
        @Test
        @DisplayName("Should store and retrieve createdAt timestamp")
        void shouldStoreAndRetrieveCreatedAtTimestamp() {
            Instant createdAt = Instant.now();
            
            Map<String, Object> data = new HashMap<>();
            data.put("id", "1");
            data.put("name", "Product A");
            data.put("price", 99.99);
            data.put("sku", "SKU001");
            data.put("createdAt", createdAt);
            data.put("updatedAt", createdAt);
            adapter.create(testCollection, data);
            
            Optional<Map<String, Object>> result = adapter.getById(testCollection, "1");
            
            assertTrue(result.isPresent());
            assertNotNull(result.get().get("createdAt"));
        }
        
        @Test
        @DisplayName("Should store and retrieve updatedAt timestamp")
        void shouldStoreAndRetrieveUpdatedAtTimestamp() {
            Instant now = Instant.now();
            
            Map<String, Object> data = new HashMap<>();
            data.put("id", "1");
            data.put("name", "Product A");
            data.put("price", 99.99);
            data.put("sku", "SKU001");
            data.put("createdAt", now);
            data.put("updatedAt", now);
            adapter.create(testCollection, data);
            
            // Update the record
            Instant updatedAt = Instant.now();
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Updated Product");
            updateData.put("updatedAt", updatedAt);
            
            adapter.update(testCollection, "1", updateData);
            
            Optional<Map<String, Object>> result = adapter.getById(testCollection, "1");
            
            assertTrue(result.isPresent());
            assertNotNull(result.get().get("updatedAt"));
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should throw exception for invalid JSON path")
        void shouldThrowExceptionForInvalidJsonPath() {
            adapter.initializeCollection(testCollection);
            
            assertThrows(IllegalArgumentException.class, () -> {
                adapter.isUnique(testCollection, "invalid-field-name!", "value", null);
            });
        }
        
        @Test
        @DisplayName("Should throw exception for blank JSON path")
        void shouldThrowExceptionForBlankJsonPath() {
            adapter.initializeCollection(testCollection);
            
            assertThrows(IllegalArgumentException.class, () -> {
                adapter.isUnique(testCollection, "", "value", null);
            });
        }
    }
}
