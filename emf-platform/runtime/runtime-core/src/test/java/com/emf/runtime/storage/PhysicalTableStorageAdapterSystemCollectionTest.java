package com.emf.runtime.storage;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldDefinitionBuilder;
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
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for system collection support in PhysicalTableStorageAdapter.
 *
 * <p>Verifies that system collections (those with systemCollection=true) correctly:
 * <ul>
 *   <li>Skip table creation during initialization</li>
 *   <li>Use column name mapping (field-level and collection-level)</li>
 *   <li>Inject tenant_id for tenant-scoped system collections</li>
 *   <li>Remap result column names from snake_case to camelCase</li>
 * </ul>
 */
class PhysicalTableStorageAdapterSystemCollectionTest {

    private JdbcTemplate jdbcTemplate;
    private SchemaMigrationEngine migrationEngine;
    private PhysicalTableStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        migrationEngine = mock(SchemaMigrationEngine.class);
        adapter = new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine);
    }

    // ==================== Helper Methods ====================

    /**
     * Builds a system collection definition for "users" with column mappings.
     * Table name: "users" (managed by Flyway, not prefixed with tbl_).
     */
    private CollectionDefinition buildSystemCollection() {
        return new CollectionDefinitionBuilder()
                .name("users")
                .displayName("Users")
                .storageConfig(new StorageConfig(StorageMode.PHYSICAL_TABLES, "users", null))
                .systemCollection(true)
                .tenantScoped(true)
                .addField(new FieldDefinitionBuilder()
                        .name("firstName")
                        .type(FieldType.STRING)
                        .columnName("first_name")
                        .build())
                .addField(new FieldDefinitionBuilder()
                        .name("lastName")
                        .type(FieldType.STRING)
                        .columnName("last_name")
                        .build())
                .addField(new FieldDefinitionBuilder()
                        .name("email")
                        .type(FieldType.STRING)
                        .build())
                .addColumnMapping("email", "email_address")
                .build();
    }

    /**
     * Builds a system collection with collection-level column mapping only
     * (no field-level columnName overrides).
     */
    private CollectionDefinition buildSystemCollectionWithCollectionMapping() {
        return new CollectionDefinitionBuilder()
                .name("audit-logs")
                .displayName("Audit Logs")
                .storageConfig(new StorageConfig(StorageMode.PHYSICAL_TABLES, "audit_logs", null))
                .systemCollection(true)
                .tenantScoped(false)
                .addField(new FieldDefinitionBuilder()
                        .name("actionType")
                        .type(FieldType.STRING)
                        .build())
                .addColumnMapping("actionType", "action_type")
                .build();
    }

    /**
     * Builds a standard (non-system) collection definition.
     */
    private CollectionDefinition buildNonSystemCollection() {
        return new CollectionDefinitionBuilder()
                .name("products")
                .displayName("Products")
                .addField(FieldDefinition.requiredString("name"))
                .addField(FieldDefinition.doubleField("price"))
                .build();
    }

    // ==================== Initialization Tests ====================

    @Nested
    @DisplayName("initializeCollection Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should skip table creation for system collections")
        void initializeCollection_skipsTableCreation_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            adapter.initializeCollection(systemDef);

            // Verify that no SQL execute was called (no CREATE TABLE)
            verify(jdbcTemplate, never()).execute(anyString());
            // Verify that migration engine was not invoked
            verify(migrationEngine, never()).recordMigration(
                    anyString(), any(SchemaMigrationEngine.MigrationType.class), anyString());
            verify(migrationEngine, never()).reconcileSchema(any(CollectionDefinition.class));
            verify(migrationEngine, never()).reconcileSchema(any(CollectionDefinition.class), any(TableRef.class));
        }

        @Test
        @DisplayName("Should create table for non-system collections")
        void initializeCollection_createsTable_forNonSystemCollections() {
            CollectionDefinition nonSystemDef = buildNonSystemCollection();

            adapter.initializeCollection(nonSystemDef);

            // Verify that SQL execute was called (CREATE TABLE)
            verify(jdbcTemplate, atLeastOnce()).execute(argThat((String sql) ->
                    sql.contains("CREATE TABLE IF NOT EXISTS")));
            // Verify migration tracking
            ArgumentCaptor<SchemaMigrationEngine.MigrationType> typeCaptor =
                    ArgumentCaptor.forClass(SchemaMigrationEngine.MigrationType.class);
            verify(migrationEngine).recordMigration(
                    eq("products"), typeCaptor.capture(), anyString());
            assertEquals(SchemaMigrationEngine.MigrationType.CREATE_TABLE, typeCaptor.getValue());
            verify(migrationEngine).reconcileSchema(eq(nonSystemDef), any(TableRef.class));
        }
    }

    // ==================== Column Name Mapping Tests (via create) ====================

    @Nested
    @DisplayName("Column Name Mapping Tests via create()")
    class ColumnNameMappingViaCreateTests {

        @Test
        @DisplayName("Should use field-level columnName for system collections in INSERT SQL")
        void create_usesFieldLevelColumnName_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("id", "user-1");
            data.put("firstName", "John");
            data.put("lastName", "Doe");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());

            adapter.create(systemDef, data);

            // Capture the SQL and verify it uses "first_name" and "last_name" columns
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("first_name"),
                    "SQL should use field-level column name 'first_name', got: " + sql);
            assertTrue(sql.contains("last_name"),
                    "SQL should use field-level column name 'last_name', got: " + sql);
        }

        @Test
        @DisplayName("Should use collection-level mapping for system collections in INSERT SQL")
        void create_usesCollectionLevelMapping_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("id", "user-1");
            data.put("email", "john@example.com");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());

            adapter.create(systemDef, data);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("email_address"),
                    "SQL should use collection-level column mapping 'email_address', got: " + sql);
        }

        @Test
        @DisplayName("Should use field name as column name for non-system collections")
        void create_usesFieldName_forNonSystemCollections() {
            CollectionDefinition nonSystemDef = buildNonSystemCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("id", "prod-1");
            data.put("name", "Widget");
            data.put("price", 9.99);
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());

            adapter.create(nonSystemDef, data);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            // For non-system collections, column names match field names
            assertTrue(sql.contains("name"),
                    "SQL should use field name 'name' for non-system collection, got: " + sql);
            assertTrue(sql.contains("price"),
                    "SQL should use field name 'price' for non-system collection, got: " + sql);
        }

        @Test
        @DisplayName("Should inject tenant_id for tenant-scoped system collections")
        void create_injectsTenantId_forTenantScopedSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("id", "user-1");
            data.put("tenantId", "tenant-abc");
            data.put("firstName", "Jane");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());

            adapter.create(systemDef, data);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("tenant_id"),
                    "SQL should include 'tenant_id' column for tenant-scoped system collection, got: " + sql);

            // Verify tenant_id value is in the params
            Object[] params = paramsCaptor.getValue();
            boolean hasTenantId = false;
            for (Object param : params) {
                if ("tenant-abc".equals(param)) {
                    hasTenantId = true;
                    break;
                }
            }
            assertTrue(hasTenantId, "Parameters should contain the tenant ID value 'tenant-abc'");
        }

        @Test
        @DisplayName("Should not inject tenant_id for non-system collections")
        void create_skipsTenantId_forNonSystemCollections() {
            CollectionDefinition nonSystemDef = buildNonSystemCollection();

            Map<String, Object> data = new HashMap<>();
            data.put("id", "prod-1");
            data.put("tenantId", "tenant-abc");
            data.put("name", "Widget");
            data.put("createdAt", Instant.now());
            data.put("updatedAt", Instant.now());

            adapter.create(nonSystemDef, data);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertFalse(sql.contains("tenant_id"),
                    "SQL should NOT include 'tenant_id' column for non-system collection, got: " + sql);
        }
    }

    // ==================== Column Name Mapping Tests (via update) ====================

    @Nested
    @DisplayName("Column Name Mapping Tests via update()")
    class ColumnNameMappingViaUpdateTests {

        @Test
        @DisplayName("Should use field-level columnName for system collections in UPDATE SQL")
        void update_usesFieldLevelColumnName_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            // Mock getById to return existing record
            Map<String, Object> existingRecord = new HashMap<>();
            existingRecord.put("id", "user-1");
            existingRecord.put("first_name", "John");
            when(jdbcTemplate.queryForList(contains("SELECT * FROM"), eq("user-1")))
                    .thenReturn(List.of(existingRecord));

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("firstName", "Jane");
            updateData.put("updatedAt", Instant.now());

            // Mock the update call
            when(jdbcTemplate.update(contains("UPDATE"), any(Object[].class))).thenReturn(1);
            // Mock the second getById call for the return value
            when(jdbcTemplate.queryForList(contains("SELECT * FROM"), eq("user-1")))
                    .thenReturn(List.of(existingRecord));

            adapter.update(systemDef, "user-1", updateData);

            // Capture the UPDATE SQL
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("first_name"),
                    "UPDATE SQL should use field-level column name 'first_name', got: " + sql);
        }
    }

    // ==================== Column Name Remapping in Query Results ====================

    @Nested
    @DisplayName("Column Name Remapping Tests via query()")
    class ColumnNameRemappingTests {

        @Test
        @DisplayName("Should remap snake_case columns to camelCase for system collections")
        void query_remapsSnakeCaseToCamelCase_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            // Mock queryForList to return results with snake_case column names
            Map<String, Object> row = new HashMap<>();
            row.put("id", "user-1");
            row.put("first_name", "John");
            row.put("last_name", "Doe");
            row.put("email_address", "john@example.com");
            row.put("created_at", Instant.now());
            row.put("updated_at", Instant.now());
            row.put("created_by", "admin");
            row.put("updated_by", "admin");
            row.put("tenant_id", "tenant-1");

            when(jdbcTemplate.queryForList(contains("SELECT"), any(Object[].class)))
                    .thenReturn(new ArrayList<>(List.of(new HashMap<>(row))));
            when(jdbcTemplate.queryForObject(contains("SELECT COUNT"), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);

            QueryRequest request = QueryRequest.defaults();
            QueryResult result = adapter.query(systemDef, request);

            assertFalse(result.data().isEmpty(), "Query should return data");
            Map<String, Object> record = result.data().get(0);

            // Verify system audit fields are remapped
            assertTrue(record.containsKey("createdAt"),
                    "Result should have 'createdAt' (remapped from 'created_at')");
            assertTrue(record.containsKey("updatedAt"),
                    "Result should have 'updatedAt' (remapped from 'updated_at')");
            assertTrue(record.containsKey("createdBy"),
                    "Result should have 'createdBy' (remapped from 'created_by')");
            assertTrue(record.containsKey("updatedBy"),
                    "Result should have 'updatedBy' (remapped from 'updated_by')");
            assertTrue(record.containsKey("tenantId"),
                    "Result should have 'tenantId' (remapped from 'tenant_id')");

            // Verify field-level column names are remapped
            assertTrue(record.containsKey("firstName"),
                    "Result should have 'firstName' (remapped from 'first_name')");
            assertTrue(record.containsKey("lastName"),
                    "Result should have 'lastName' (remapped from 'last_name')");

            // Verify collection-level column mapping is remapped
            assertTrue(record.containsKey("email"),
                    "Result should have 'email' (remapped from 'email_address')");
        }

        @Test
        @DisplayName("Should not remap columns for non-system collections")
        void query_isNoOp_forNonSystemCollections() {
            CollectionDefinition nonSystemDef = buildNonSystemCollection();

            Map<String, Object> row = new HashMap<>();
            row.put("id", "prod-1");
            row.put("name", "Widget");
            row.put("price", 9.99);
            row.put("created_at", Instant.now());
            row.put("updated_at", Instant.now());

            when(jdbcTemplate.queryForList(contains("SELECT"), any(Object[].class)))
                    .thenReturn(new ArrayList<>(List.of(new HashMap<>(row))));
            when(jdbcTemplate.queryForObject(contains("SELECT COUNT"), eq(Long.class), any(Object[].class)))
                    .thenReturn(1L);

            QueryRequest request = QueryRequest.defaults();
            QueryResult result = adapter.query(nonSystemDef, request);

            assertFalse(result.data().isEmpty(), "Query should return data");
            Map<String, Object> record = result.data().get(0);

            // For non-system collections, columns should NOT be remapped
            assertTrue(record.containsKey("name"),
                    "Non-system collection should preserve 'name' as-is");
            assertTrue(record.containsKey("price"),
                    "Non-system collection should preserve 'price' as-is");
        }
    }

    // ==================== Column Name Resolution in getById ====================

    @Nested
    @DisplayName("Column Name Remapping Tests via getById()")
    class GetByIdRemappingTests {

        @Test
        @DisplayName("Should remap column names in getById results for system collections")
        void getById_remapsColumnNames_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            Map<String, Object> row = new HashMap<>();
            row.put("id", "user-1");
            row.put("first_name", "John");
            row.put("last_name", "Doe");
            row.put("email_address", "john@example.com");
            row.put("created_at", Instant.now());
            row.put("updated_at", Instant.now());

            when(jdbcTemplate.queryForList(contains("SELECT * FROM"), eq("user-1")))
                    .thenReturn(new ArrayList<>(List.of(new HashMap<>(row))));

            Optional<Map<String, Object>> result = adapter.getById(systemDef, "user-1");

            assertTrue(result.isPresent());
            Map<String, Object> record = result.get();
            assertTrue(record.containsKey("firstName"),
                    "getById should remap 'first_name' to 'firstName'");
            assertTrue(record.containsKey("lastName"),
                    "getById should remap 'last_name' to 'lastName'");
            assertTrue(record.containsKey("email"),
                    "getById should remap 'email_address' to 'email'");
        }
    }

    // ==================== Column Name Resolution in Query Filters/Sorting ====================

    @Nested
    @DisplayName("Column Name Resolution in WHERE/ORDER BY")
    class ColumnNameResolutionInQueryTests {

        @Test
        @DisplayName("Should resolve system audit field names in WHERE clause")
        void query_resolvesSysFieldsInWhereClause() {
            CollectionDefinition systemDef = buildSystemCollection();

            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(new ArrayList<>());
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(0L);

            QueryRequest request = new QueryRequest(
                    Pagination.defaults(),
                    List.of(),
                    List.of(),
                    List.of(FilterCondition.eq("createdAt", "2024-01-01"))
            );

            adapter.query(systemDef, request);

            // Capture the SELECT SQL and verify it contains "created_at" in WHERE
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("created_at"),
                    "WHERE clause should use 'created_at' for 'createdAt', got: " + sql);
        }

        @Test
        @DisplayName("Should resolve mapped field names in ORDER BY clause")
        void query_resolvesMappedFieldsInOrderByClause() {
            CollectionDefinition systemDef = buildSystemCollection();

            when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(new ArrayList<>());
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                    .thenReturn(0L);

            QueryRequest request = new QueryRequest(
                    Pagination.defaults(),
                    List.of(SortField.asc("firstName")),
                    List.of(),
                    List.of()
            );

            adapter.query(systemDef, request);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("first_name"),
                    "ORDER BY clause should use 'first_name' for 'firstName', got: " + sql);
        }
    }

    // ==================== isUnique Column Name Resolution ====================

    @Nested
    @DisplayName("isUnique Column Name Resolution Tests")
    class IsUniqueColumnNameTests {

        @Test
        @DisplayName("Should resolve column name for system collection in isUnique query")
        void isUnique_resolvesColumnName_forSystemCollections() {
            CollectionDefinition systemDef = buildSystemCollection();

            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                    .thenReturn(0);

            adapter.isUnique(systemDef, "firstName", "John", null);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Integer.class), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("first_name"),
                    "isUnique SQL should use resolved column name 'first_name', got: " + sql);
        }

        @Test
        @DisplayName("Should use field name as column name for non-system collection in isUnique")
        void isUnique_usesFieldName_forNonSystemCollections() {
            CollectionDefinition nonSystemDef = buildNonSystemCollection();

            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                    .thenReturn(0);

            adapter.isUnique(nonSystemDef, "name", "Widget", null);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Integer.class), any(Object[].class));

            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("name"),
                    "isUnique SQL should use field name 'name', got: " + sql);
        }
    }
}
