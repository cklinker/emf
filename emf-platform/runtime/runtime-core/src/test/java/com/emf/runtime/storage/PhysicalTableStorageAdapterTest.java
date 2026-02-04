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
import com.emf.runtime.query.SortDirection;

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
 * Unit tests for PhysicalTableStorageAdapter.
 * 
 * Uses H2 in-memory database for testing.
 */
class PhysicalTableStorageAdapterTest {
    
    private JdbcTemplate jdbcTemplate;
    private SchemaMigrationEngine migrationEngine;
    private PhysicalTableStorageAdapter adapter;
    private CollectionDefinition testCollection;
    
    @BeforeEach
    void setUp() {
        // Create embedded H2 database
        DataSource dataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        
        jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Drop migration table if exists (for clean test runs)
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS emf_migrations");
        } catch (Exception e) {
            // Ignore
        }
        
        migrationEngine = new SchemaMigrationEngine(jdbcTemplate);
        adapter = new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine);
        
        // Create a test collection definition
        testCollection = createTestCollection("test_products");
        
        // Drop table if exists (for clean test runs)
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS tbl_test_products");
        } catch (Exception e) {
            // Ignore
        }
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
        
        StorageConfig storageConfig = new StorageConfig(
            StorageMode.PHYSICAL_TABLES,
            "tbl_" + name,
            Map.of()
        );
        
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
        @DisplayName("Should create table with correct columns")
        void shouldCreateTableWithCorrectColumns() {
            adapter.initializeCollection(testCollection);
            
            // Verify table exists by querying it
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_test_products", Integer.class);
            assertEquals(0, count);
        }
        
        @Test
        @DisplayName("Should be idempotent - calling twice should not fail")
        void shouldBeIdempotent() {
            adapter.initializeCollection(testCollection);
            assertDoesNotThrow(() -> adapter.initializeCollection(testCollection));
        }
        
        @Test
        @DisplayName("Should create table with all field types")
        void shouldCreateTableWithAllFieldTypes() {
            List<FieldDefinition> fields = List.of(
                new FieldDefinition("str_field", FieldType.STRING, true, false, false, null, null, null, null),
                new FieldDefinition("int_field", FieldType.INTEGER, true, false, false, null, null, null, null),
                new FieldDefinition("long_field", FieldType.LONG, true, false, false, null, null, null, null),
                new FieldDefinition("double_field", FieldType.DOUBLE, true, false, false, null, null, null, null),
                new FieldDefinition("bool_field", FieldType.BOOLEAN, true, false, false, null, null, null, null),
                new FieldDefinition("date_field", FieldType.DATE, true, false, false, null, null, null, null),
                new FieldDefinition("datetime_field", FieldType.DATETIME, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition allTypesCollection = new CollectionDefinition(
                "all_types",
                "All Types",
                "Collection with all field types",
                fields,
                StorageConfig.physicalTable("tbl_all_types"),
                null, null, null,
                1L, Instant.now(), Instant.now()
            );
            
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS tbl_all_types");
            } catch (Exception e) {
                // Ignore
            }
            
            assertDoesNotThrow(() -> adapter.initializeCollection(allTypesCollection));
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
            assertEquals("Product A", result.get().get("NAME"));
        }
        
        @Test
        @DisplayName("Should return empty for non-existent ID")
        void shouldReturnEmptyForNonExistentId() {
            Optional<Map<String, Object>> result = adapter.getById(testCollection, "non-existent");
            
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
            assertEquals("Updated Product", updated.get().get("NAME"));
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
        @DisplayName("Should apply sorting ascending")
        void shouldApplySortingAscending() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(SortField.asc("price")),
                List.of(),
                List.of()
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(5, result.data().size());
            // H2 returns column names in uppercase
            assertEquals("Banana", result.data().get(0).get("NAME")); // 0.99
            assertEquals("Apple", result.data().get(1).get("NAME"));  // 1.99
        }
        
        @Test
        @DisplayName("Should apply sorting descending")
        void shouldApplySortingDescending() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(SortField.desc("price")),
                List.of(),
                List.of()
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(5, result.data().size());
            assertEquals("Elderberry", result.data().get(0).get("NAME")); // 7.99
            assertEquals("Date", result.data().get(1).get("NAME"));       // 5.99
        }
    }
    
    @Nested
    @DisplayName("Filter Operator Tests")
    class FilterOperatorTests {
        
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
        @DisplayName("Should filter with EQ operator")
        void shouldFilterWithEqOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.eq("name", "Apple"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(1, result.data().size());
            assertEquals("Apple", result.data().get(0).get("NAME"));
        }
        
        @Test
        @DisplayName("Should filter with NEQ operator")
        void shouldFilterWithNeqOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.neq("name", "Apple"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(4, result.data().size());
        }
        
        @Test
        @DisplayName("Should filter with GT operator")
        void shouldFilterWithGtOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.gt("price", "5.00"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Date (5.99) and Elderberry (7.99)
        }
        
        @Test
        @DisplayName("Should filter with LT operator")
        void shouldFilterWithLtOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.lt("price", "2.00"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Apple (1.99) and Banana (0.99)
        }
        
        @Test
        @DisplayName("Should filter with GTE operator")
        void shouldFilterWithGteOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("price", FilterOperator.GTE, "5.99"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Date (5.99) and Elderberry (7.99)
        }
        
        @Test
        @DisplayName("Should filter with LTE operator")
        void shouldFilterWithLteOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("price", FilterOperator.LTE, "1.99"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Apple (1.99) and Banana (0.99)
        }
        
        @Test
        @DisplayName("Should filter with CONTAINS operator")
        void shouldFilterWithContainsOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.contains("name", "err"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Cherry and Elderberry
        }
        
        @Test
        @DisplayName("Should filter with STARTS operator")
        void shouldFilterWithStartsOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("name", FilterOperator.STARTS, "A"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(1, result.data().size());
            assertEquals("Apple", result.data().get(0).get("NAME"));
        }
        
        @Test
        @DisplayName("Should filter with ENDS operator")
        void shouldFilterWithEndsOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("name", FilterOperator.ENDS, "e"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Apple and Date
        }
        
        @Test
        @DisplayName("Should filter with ICONTAINS operator (case-insensitive)")
        void shouldFilterWithIcontainsOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.icontains("name", "APPLE"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(1, result.data().size());
            assertEquals("Apple", result.data().get(0).get("NAME"));
        }
        
        @Test
        @DisplayName("Should filter with ISTARTS operator (case-insensitive)")
        void shouldFilterWithIstartsOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("name", FilterOperator.ISTARTS, "a"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(1, result.data().size());
            assertEquals("Apple", result.data().get(0).get("NAME"));
        }
        
        @Test
        @DisplayName("Should filter with IENDS operator (case-insensitive)")
        void shouldFilterWithIendsOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("name", FilterOperator.IENDS, "E"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Apple and Date
        }
        
        @Test
        @DisplayName("Should filter with IEQ operator (case-insensitive equals)")
        void shouldFilterWithIeqOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("name", FilterOperator.IEQ, "APPLE"))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(1, result.data().size());
            assertEquals("Apple", result.data().get(0).get("NAME"));
        }
        
        @Test
        @DisplayName("Should combine multiple filters with AND logic")
        void shouldCombineMultipleFiltersWithAndLogic() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(
                    new FilterCondition("price", FilterOperator.GT, "1.00"),
                    new FilterCondition("active", FilterOperator.EQ, true)
                )
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            // Apple (1.99, active), Date (5.99, active)
            assertEquals(2, result.data().size());
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
    }
    
    @Nested
    @DisplayName("ISNULL Filter Tests")
    class IsNullFilterTests {
        
        @BeforeEach
        void initTableAndData() {
            adapter.initializeCollection(testCollection);
            
            // Insert test data with some null descriptions
            insertTestDataWithNullDescription("1", "Apple", 1.99, null, "SKU001");
            insertTestDataWithNullDescription("2", "Banana", 0.99, "Yellow fruit", "SKU002");
            insertTestDataWithNullDescription("3", "Cherry", 3.99, null, "SKU003");
        }
        
        private void insertTestDataWithNullDescription(String id, String name, double price, 
                String description, String sku) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("price", price);
            data.put("description", description);
            data.put("sku", sku);
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());
            adapter.create(testCollection, data);
        }
        
        @Test
        @DisplayName("Should filter with ISNULL=true operator")
        void shouldFilterWithIsNullTrueOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.isNull("description", true))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(2, result.data().size()); // Apple and Cherry have null descriptions
        }
        
        @Test
        @DisplayName("Should filter with ISNULL=false operator (IS NOT NULL)")
        void shouldFilterWithIsNullFalseOperator() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(FilterCondition.isNull("description", false))
            );
            
            QueryResult result = adapter.query(testCollection, request);
            
            assertEquals(1, result.data().size()); // Only Banana has a description
            assertEquals("Banana", result.data().get(0).get("NAME"));
        }
    }
}
