package com.emf.runtime.integration;

import com.emf.runtime.model.*;
import com.emf.runtime.query.*;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.registry.ConcurrentCollectionRegistry;
import com.emf.runtime.storage.PhysicalTableStorageAdapter;
import com.emf.runtime.storage.SchemaMigrationEngine;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.DefaultValidationEngine;
import com.emf.runtime.validation.ValidationEngine;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EMF Runtime Core.
 * 
 * <p>Tests the full flow: create collection → create record → query → update → delete.
 * Uses an embedded H2 database for testing.
 */
@DisplayName("EMF Runtime Integration Tests")
class EmfRuntimeIntegrationTest {
    
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private CollectionRegistry registry;
    private StorageAdapter storageAdapter;
    private ValidationEngine validationEngine;
    private QueryEngine queryEngine;
    
    @BeforeEach
    void setUp() {
        // Set up embedded database
        dataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Initialize components
        registry = new ConcurrentCollectionRegistry();
        SchemaMigrationEngine migrationEngine = new SchemaMigrationEngine(jdbcTemplate);
        storageAdapter = new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine);
        validationEngine = new DefaultValidationEngine(storageAdapter, registry);
        queryEngine = new DefaultQueryEngine(storageAdapter, validationEngine);
    }
    
    @Nested
    @DisplayName("Full CRUD Flow")
    class FullCrudFlowTests {
        
        @Test
        @DisplayName("Should complete full CRUD lifecycle")
        void shouldCompleteFullCrudLifecycle() {
            // 1. Create collection definition
            CollectionDefinition products = new CollectionDefinitionBuilder()
                .name("products")
                .displayName("Products")
                .description("Product catalog")
                .addField(new FieldDefinitionBuilder()
                    .name("name")
                    .type(FieldType.STRING)
                    .nullable(false)
                    .build())
                .addField(new FieldDefinitionBuilder()
                    .name("price")
                    .type(FieldType.DOUBLE)
                    .nullable(false)
                    .build())
                .addField(new FieldDefinitionBuilder()
                    .name("category")
                    .type(FieldType.STRING)
                    .nullable(true)
                    .build())
                .build();
            
            // 2. Register collection and initialize storage
            registry.register(products);
            storageAdapter.initializeCollection(products);
            
            // 3. Create a record
            Map<String, Object> productData = new HashMap<>();
            productData.put("name", "Widget A");
            productData.put("price", 29.99);
            productData.put("category", "Electronics");
            
            Map<String, Object> created = queryEngine.create(products, productData);
            
            assertNotNull(created.get("id"));
            assertEquals("Widget A", created.get("name"));
            assertEquals(29.99, created.get("price"));
            assertNotNull(created.get("createdAt"));
            
            String recordId = (String) created.get("id");
            
            // 4. Query the record
            QueryRequest queryRequest = new QueryRequest(
                Pagination.defaults(),
                List.of(new SortField("name", SortDirection.ASC)),
                List.of(),
                List.of(new FilterCondition("category", FilterOperator.EQ, "Electronics"))
            );
            
            QueryResult result = queryEngine.executeQuery(products, queryRequest);
            
            assertEquals(1, result.size());
            assertEquals("Widget A", result.data().get(0).get("name"));
            
            // 5. Get by ID
            Optional<Map<String, Object>> fetched = queryEngine.getById(products, recordId);
            
            assertTrue(fetched.isPresent());
            assertEquals("Widget A", fetched.get().get("name"));
            
            // 6. Update the record
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("price", 34.99);
            
            Optional<Map<String, Object>> updated = queryEngine.update(products, recordId, updateData);
            
            assertTrue(updated.isPresent());
            assertEquals(34.99, updated.get().get("price"));
            
            // 7. Delete the record
            boolean deleted = queryEngine.delete(products, recordId);
            
            assertTrue(deleted);
            
            // 8. Verify deletion
            Optional<Map<String, Object>> afterDelete = queryEngine.getById(products, recordId);
            
            assertTrue(afterDelete.isEmpty());
        }
    }

    @Nested
    @DisplayName("Schema Migration")
    class SchemaMigrationTests {
        
        @Test
        @DisplayName("Should migrate schema when field is added")
        void shouldMigrateSchemaWhenFieldAdded() {
            // 1. Create initial collection
            CollectionDefinition v1 = new CollectionDefinitionBuilder()
                .name("items")
                .displayName("Items")
                .addField(new FieldDefinitionBuilder()
                    .name("name")
                    .type(FieldType.STRING)
                    .nullable(false)
                    .build())
                .version(1)
                .build();
            
            registry.register(v1);
            storageAdapter.initializeCollection(v1);
            
            // 2. Create a record
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Item 1");
            
            Map<String, Object> created = queryEngine.create(v1, itemData);
            String recordId = (String) created.get("id");
            
            // 3. Update collection with new field
            CollectionDefinition v2 = new CollectionDefinitionBuilder()
                .name("items")
                .displayName("Items")
                .addField(new FieldDefinitionBuilder()
                    .name("name")
                    .type(FieldType.STRING)
                    .nullable(false)
                    .build())
                .addField(new FieldDefinitionBuilder()
                    .name("description")
                    .type(FieldType.STRING)
                    .nullable(true)
                    .build())
                .version(2)
                .build();
            
            registry.register(v2);
            storageAdapter.updateCollectionSchema(v1, v2);
            
            // 4. Update record with new field
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("description", "A great item");
            
            Optional<Map<String, Object>> updated = queryEngine.update(v2, recordId, updateData);
            
            assertTrue(updated.isPresent());
            assertEquals("A great item", updated.get().get("description"));
        }
    }
    
    @Nested
    @DisplayName("Validation")
    class ValidationTests {
        
        @Test
        @DisplayName("Should validate required fields")
        void shouldValidateRequiredFields() {
            CollectionDefinition collection = new CollectionDefinitionBuilder()
                .name("validated_items")
                .displayName("Validated Items")
                .addField(new FieldDefinitionBuilder()
                    .name("name")
                    .type(FieldType.STRING)
                    .nullable(false)
                    .build())
                .build();
            
            registry.register(collection);
            storageAdapter.initializeCollection(collection);
            
            // Try to create without required field
            Map<String, Object> invalidData = new HashMap<>();
            // name is missing
            
            assertThrows(com.emf.runtime.validation.ValidationException.class, () ->
                queryEngine.create(collection, invalidData));
        }
        
        @Test
        @DisplayName("Should validate field types")
        void shouldValidateFieldTypes() {
            CollectionDefinition collection = new CollectionDefinitionBuilder()
                .name("typed_items")
                .displayName("Typed Items")
                .addField(new FieldDefinitionBuilder()
                    .name("count")
                    .type(FieldType.INTEGER)
                    .nullable(false)
                    .build())
                .build();
            
            registry.register(collection);
            storageAdapter.initializeCollection(collection);
            
            // Try to create with wrong type
            Map<String, Object> invalidData = new HashMap<>();
            invalidData.put("count", "not a number");
            
            assertThrows(com.emf.runtime.validation.ValidationException.class, () ->
                queryEngine.create(collection, invalidData));
        }
    }
    
    @Nested
    @DisplayName("Query Features")
    class QueryFeatureTests {
        
        private CollectionDefinition products;
        private String collectionName;
        
        @BeforeEach
        void setUpProducts() {
            // Use unique collection name for each test run
            collectionName = "query_products_" + System.currentTimeMillis();
            
            products = new CollectionDefinitionBuilder()
                .name(collectionName)
                .displayName("Query Products")
                .addField(new FieldDefinitionBuilder()
                    .name("name")
                    .type(FieldType.STRING)
                    .nullable(false)
                    .build())
                .addField(new FieldDefinitionBuilder()
                    .name("price")
                    .type(FieldType.DOUBLE)
                    .nullable(false)
                    .build())
                .build();
            
            registry.register(products);
            storageAdapter.initializeCollection(products);
            
            // Create test data
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> data = new HashMap<>();
                data.put("name", "Product " + i);
                data.put("price", i * 10.0);
                queryEngine.create(products, data);
            }
        }
        
        @Test
        @DisplayName("Should paginate results")
        void shouldPaginateResults() {
            QueryRequest request = new QueryRequest(
                new Pagination(1, 2),
                List.of(),
                List.of(),
                List.of()
            );
            
            QueryResult result = queryEngine.executeQuery(products, request);
            
            assertEquals(2, result.size());
            assertEquals(5, result.metadata().totalCount());
            assertEquals(3, result.metadata().totalPages());
        }
        
        @Test
        @DisplayName("Should sort results")
        void shouldSortResults() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(new SortField("price", SortDirection.DESC)),
                List.of(),
                List.of()
            );
            
            QueryResult result = queryEngine.executeQuery(products, request);
            
            assertEquals(50.0, result.data().get(0).get("price"));
            assertEquals(10.0, result.data().get(4).get("price"));
        }
        
        @Test
        @DisplayName("Should filter results")
        void shouldFilterResults() {
            QueryRequest request = new QueryRequest(
                Pagination.defaults(),
                List.of(),
                List.of(),
                List.of(new FilterCondition("price", FilterOperator.GTE, 30.0))
            );
            
            QueryResult result = queryEngine.executeQuery(products, request);
            
            assertEquals(3, result.size());
        }
    }
}
