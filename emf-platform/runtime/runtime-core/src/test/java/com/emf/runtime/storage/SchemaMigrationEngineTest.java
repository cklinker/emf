package com.emf.runtime.storage;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.StorageConfig;
import com.emf.runtime.model.StorageMode;
import com.emf.runtime.storage.SchemaMigrationEngine.MigrationRecord;
import com.emf.runtime.storage.SchemaMigrationEngine.MigrationType;
import com.emf.runtime.storage.SchemaMigrationEngine.SchemaDiff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchemaMigrationEngine.
 * 
 * Tests migration history tracking, schema diff detection, ALTER TABLE generation,
 * column deprecation, and type change validation.
 * 
 * Validates Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */
class SchemaMigrationEngineTest {
    
    private JdbcTemplate jdbcTemplate;
    private SchemaMigrationEngine migrationEngine;
    
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
    }
    
    private CollectionDefinition createCollection(String name, List<FieldDefinition> fields) {
        StorageConfig storageConfig = new StorageConfig(
            StorageMode.PHYSICAL_TABLES,
            "tbl_" + name,
            Map.of()
        );
        
        return new CollectionDefinition(
            name,
            name,
            "Test collection",
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
    
    private void createTestTable(String tableName, List<FieldDefinition> fields) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sql.append(tableName).append(" (");
        sql.append("id VARCHAR(36) PRIMARY KEY, ");
        sql.append("created_at TIMESTAMP NOT NULL, ");
        sql.append("updated_at TIMESTAMP NOT NULL");
        
        for (FieldDefinition field : fields) {
            sql.append(", ");
            sql.append(field.name()).append(" ");
            sql.append(mapFieldTypeToSql(field.type()));
        }
        
        sql.append(")");
        jdbcTemplate.execute(sql.toString());
    }
    
    private String mapFieldTypeToSql(FieldType type) {
        return switch (type) {
            case STRING -> "TEXT";
            case INTEGER -> "INTEGER";
            case LONG -> "BIGINT";
            case DOUBLE -> "DOUBLE PRECISION";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case DATETIME -> "TIMESTAMP";
            case JSON -> "CLOB"; // H2 doesn't have JSONB, use CLOB
        };
    }
    
    @Nested
    @DisplayName("Migration History Table Tests")
    class MigrationHistoryTests {
        
        @Test
        @DisplayName("Should create migration history table on initialization")
        void shouldCreateMigrationHistoryTable() {
            // The table should be created in setUp via constructor
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emf_migrations", Integer.class);
            assertEquals(0, count);
        }
        
        @Test
        @DisplayName("Should record migration with all required fields")
        void shouldRecordMigrationWithAllFields() {
            String sql = "CREATE TABLE test_table (id VARCHAR(36) PRIMARY KEY)";
            
            migrationEngine.recordMigration("test_collection", MigrationType.CREATE_TABLE, sql);
            
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test_collection");
            
            assertEquals(1, history.size());
            MigrationRecord record = history.get(0);
            assertEquals("test_collection", record.collectionName());
            assertEquals(MigrationType.CREATE_TABLE, record.migrationType());
            assertEquals(sql, record.sqlStatement());
            assertNotNull(record.executedAt());
            assertTrue(record.id() > 0);
        }
        
        @Test
        @DisplayName("Should record multiple migrations in order")
        void shouldRecordMultipleMigrationsInOrder() {
            migrationEngine.recordMigration("test", MigrationType.CREATE_TABLE, "CREATE TABLE...");
            migrationEngine.recordMigration("test", MigrationType.ADD_COLUMN, "ALTER TABLE ADD...");
            migrationEngine.recordMigration("test", MigrationType.DEPRECATE_COLUMN, "COMMENT ON...");
            
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            
            assertEquals(3, history.size());
            assertEquals(MigrationType.CREATE_TABLE, history.get(0).migrationType());
            assertEquals(MigrationType.ADD_COLUMN, history.get(1).migrationType());
            assertEquals(MigrationType.DEPRECATE_COLUMN, history.get(2).migrationType());
        }
        
        @Test
        @DisplayName("Should filter migration history by collection name")
        void shouldFilterMigrationHistoryByCollectionName() {
            migrationEngine.recordMigration("collection_a", MigrationType.CREATE_TABLE, "SQL A");
            migrationEngine.recordMigration("collection_b", MigrationType.CREATE_TABLE, "SQL B");
            migrationEngine.recordMigration("collection_a", MigrationType.ADD_COLUMN, "SQL A2");
            
            List<MigrationRecord> historyA = migrationEngine.getMigrationHistory("collection_a");
            List<MigrationRecord> historyB = migrationEngine.getMigrationHistory("collection_b");
            
            assertEquals(2, historyA.size());
            assertEquals(1, historyB.size());
        }
        
        @Test
        @DisplayName("Should get all migration history")
        void shouldGetAllMigrationHistory() {
            migrationEngine.recordMigration("collection_a", MigrationType.CREATE_TABLE, "SQL A");
            migrationEngine.recordMigration("collection_b", MigrationType.CREATE_TABLE, "SQL B");
            
            List<MigrationRecord> allHistory = migrationEngine.getAllMigrationHistory();
            
            assertEquals(2, allHistory.size());
        }
    }
    
    @Nested
    @DisplayName("Schema Diff Detection Tests")
    class SchemaDiffTests {
        
        @Test
        @DisplayName("Should detect added fields")
        void shouldDetectAddedFields() {
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null)
            );
            
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("description", FieldType.STRING, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            List<SchemaDiff> diffs = migrationEngine.detectDifferences(oldDef, newDef);
            
            assertEquals(1, diffs.size());
            assertEquals(SchemaDiff.DiffType.FIELD_ADDED, diffs.get(0).diffType());
            assertEquals("description", diffs.get(0).fieldName());
            assertNull(diffs.get(0).oldType());
            assertEquals(FieldType.STRING, diffs.get(0).newType());
        }
        
        @Test
        @DisplayName("Should detect removed fields")
        void shouldDetectRemovedFields() {
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("description", FieldType.STRING, true, false, false, null, null, null, null)
            );
            
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            List<SchemaDiff> diffs = migrationEngine.detectDifferences(oldDef, newDef);
            
            assertEquals(1, diffs.size());
            assertEquals(SchemaDiff.DiffType.FIELD_REMOVED, diffs.get(0).diffType());
            assertEquals("description", diffs.get(0).fieldName());
            assertEquals(FieldType.STRING, diffs.get(0).oldType());
            assertNull(diffs.get(0).newType());
        }
        
        @Test
        @DisplayName("Should detect type changes")
        void shouldDetectTypeChanges() {
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("count", FieldType.INTEGER, false, false, false, null, null, null, null)
            );
            
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("count", FieldType.LONG, false, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            List<SchemaDiff> diffs = migrationEngine.detectDifferences(oldDef, newDef);
            
            assertEquals(1, diffs.size());
            assertEquals(SchemaDiff.DiffType.TYPE_CHANGED, diffs.get(0).diffType());
            assertEquals("count", diffs.get(0).fieldName());
            assertEquals(FieldType.INTEGER, diffs.get(0).oldType());
            assertEquals(FieldType.LONG, diffs.get(0).newType());
        }
        
        @Test
        @DisplayName("Should detect multiple changes")
        void shouldDetectMultipleChanges() {
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("count", FieldType.INTEGER, false, false, false, null, null, null, null),
                new FieldDefinition("old_field", FieldType.STRING, true, false, false, null, null, null, null)
            );
            
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("count", FieldType.LONG, false, false, false, null, null, null, null),
                new FieldDefinition("new_field", FieldType.BOOLEAN, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            List<SchemaDiff> diffs = migrationEngine.detectDifferences(oldDef, newDef);
            
            assertEquals(3, diffs.size());
            
            // Verify we have one of each type
            assertTrue(diffs.stream().anyMatch(d -> d.diffType() == SchemaDiff.DiffType.FIELD_ADDED));
            assertTrue(diffs.stream().anyMatch(d -> d.diffType() == SchemaDiff.DiffType.FIELD_REMOVED));
            assertTrue(diffs.stream().anyMatch(d -> d.diffType() == SchemaDiff.DiffType.TYPE_CHANGED));
        }
        
        @Test
        @DisplayName("Should return empty list when no changes")
        void shouldReturnEmptyListWhenNoChanges() {
            List<FieldDefinition> fields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", fields);
            CollectionDefinition newDef = createCollection("test", fields);
            
            List<SchemaDiff> diffs = migrationEngine.detectDifferences(oldDef, newDef);
            
            assertTrue(diffs.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Type Change Compatibility Tests")
    class TypeChangeCompatibilityTests {
        
        @Test
        @DisplayName("STRING can change to any type")
        void stringCanChangeToAnyType() {
            for (FieldType targetType : FieldType.values()) {
                assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.STRING, targetType),
                    "STRING should be compatible with " + targetType);
            }
        }
        
        @Test
        @DisplayName("INTEGER can change to LONG, DOUBLE, STRING")
        void integerCanChangeToLongDoubleString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.INTEGER));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.LONG));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.DOUBLE));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.BOOLEAN));
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.DATE));
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.DATETIME));
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.INTEGER, FieldType.JSON));
        }
        
        @Test
        @DisplayName("LONG can change to DOUBLE, STRING")
        void longCanChangeToDoubleString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.LONG, FieldType.LONG));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.LONG, FieldType.DOUBLE));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.LONG, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.LONG, FieldType.INTEGER));
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.LONG, FieldType.BOOLEAN));
        }
        
        @Test
        @DisplayName("DOUBLE can change to STRING")
        void doubleCanChangeToString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DOUBLE, FieldType.DOUBLE));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DOUBLE, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.DOUBLE, FieldType.INTEGER));
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.DOUBLE, FieldType.LONG));
        }
        
        @Test
        @DisplayName("BOOLEAN can change to STRING")
        void booleanCanChangeToString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.BOOLEAN, FieldType.BOOLEAN));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.BOOLEAN, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.BOOLEAN, FieldType.INTEGER));
        }
        
        @Test
        @DisplayName("DATE can change to DATETIME, STRING")
        void dateCanChangeToDatetimeString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DATE, FieldType.DATE));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DATE, FieldType.DATETIME));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DATE, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.DATE, FieldType.INTEGER));
        }
        
        @Test
        @DisplayName("DATETIME can change to STRING")
        void datetimeCanChangeToString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DATETIME, FieldType.DATETIME));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.DATETIME, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.DATETIME, FieldType.DATE));
        }
        
        @Test
        @DisplayName("JSON can change to STRING")
        void jsonCanChangeToString() {
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.JSON, FieldType.JSON));
            assertTrue(migrationEngine.isTypeChangeCompatible(FieldType.JSON, FieldType.STRING));
            
            assertFalse(migrationEngine.isTypeChangeCompatible(FieldType.JSON, FieldType.INTEGER));
        }
        
        @Test
        @DisplayName("Should throw IncompatibleSchemaChangeException for incompatible changes")
        void shouldThrowExceptionForIncompatibleChanges() {
            FieldDefinition oldField = new FieldDefinition("count", FieldType.BOOLEAN, 
                false, false, false, null, null, null, null);
            FieldDefinition newField = new FieldDefinition("count", FieldType.INTEGER, 
                false, false, false, null, null, null, null);
            
            IncompatibleSchemaChangeException exception = assertThrows(
                IncompatibleSchemaChangeException.class,
                () -> migrationEngine.validateTypeChange("test_collection", oldField, newField)
            );
            
            assertEquals("test_collection", exception.getCollectionName());
            assertEquals("count", exception.getFieldName());
            assertEquals(FieldType.BOOLEAN, exception.getOldType());
            assertEquals(FieldType.INTEGER, exception.getNewType());
        }
        
        @Test
        @DisplayName("Should not throw for compatible type changes")
        void shouldNotThrowForCompatibleChanges() {
            FieldDefinition oldField = new FieldDefinition("count", FieldType.INTEGER, 
                false, false, false, null, null, null, null);
            FieldDefinition newField = new FieldDefinition("count", FieldType.LONG, 
                false, false, false, null, null, null, null);
            
            assertDoesNotThrow(() -> 
                migrationEngine.validateTypeChange("test_collection", oldField, newField));
        }
    }
    
    @Nested
    @DisplayName("Schema Migration Execution Tests")
    class SchemaMigrationExecutionTests {
        
        @BeforeEach
        void cleanupTestTable() {
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS tbl_test");
            } catch (Exception e) {
                // Ignore
            }
        }
        
        private void setupTestTable(String tableName, List<FieldDefinition> fields) {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
            sql.append(tableName).append(" (");
            sql.append("id VARCHAR(36) PRIMARY KEY, ");
            sql.append("created_at TIMESTAMP NOT NULL, ");
            sql.append("updated_at TIMESTAMP NOT NULL");
            
            for (FieldDefinition field : fields) {
                sql.append(", ");
                sql.append(field.name()).append(" ");
                sql.append(mapFieldTypeToSql(field.type()));
            }
            
            sql.append(")");
            jdbcTemplate.execute(sql.toString());
        }
        
        @Test
        @DisplayName("Should add column when field is added")
        void shouldAddColumnWhenFieldIsAdded() {
            // Create initial table
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null)
            );
            setupTestTable("tbl_test", oldFields);
            
            // Define new schema with additional field
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("description", FieldType.STRING, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            // Execute migration
            migrationEngine.migrateSchema(oldDef, newDef);
            
            // Verify column was added by inserting data
            jdbcTemplate.update(
                "INSERT INTO tbl_test (id, created_at, updated_at, name, description) VALUES (?, ?, ?, ?, ?)",
                "1", Instant.now(), Instant.now(), "Test", "Test Description"
            );
            
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_test WHERE description = 'Test Description'", Integer.class);
            assertEquals(1, count);
            
            // Verify migration was recorded
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            assertTrue(history.stream().anyMatch(r -> r.migrationType() == MigrationType.ADD_COLUMN));
        }
        
        @Test
        @DisplayName("Should deprecate column when field is removed")
        void shouldDeprecateColumnWhenFieldIsRemoved() {
            // Create initial table with two fields
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("old_field", FieldType.STRING, true, false, false, null, null, null, null)
            );
            setupTestTable("tbl_test", oldFields);
            
            // Define new schema without old_field
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            // Execute migration
            migrationEngine.migrateSchema(oldDef, newDef);
            
            // Verify column still exists (not dropped)
            jdbcTemplate.update(
                "INSERT INTO tbl_test (id, created_at, updated_at, name, old_field) VALUES (?, ?, ?, ?, ?)",
                "1", Instant.now(), Instant.now(), "Test", "Old Value"
            );
            
            // Verify migration was recorded as DEPRECATE_COLUMN
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            assertTrue(history.stream().anyMatch(r -> r.migrationType() == MigrationType.DEPRECATE_COLUMN));
        }
        
        @Test
        @DisplayName("Should alter column type for compatible changes")
        void shouldAlterColumnTypeForCompatibleChanges() {
            // Create initial table with INTEGER field
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("count", FieldType.INTEGER, true, false, false, null, null, null, null)
            );
            setupTestTable("tbl_test", oldFields);
            
            // Insert some data
            jdbcTemplate.update(
                "INSERT INTO tbl_test (id, created_at, updated_at, count) VALUES (?, ?, ?, ?)",
                "1", Instant.now(), Instant.now(), 100
            );
            
            // Define new schema with LONG type
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("count", FieldType.LONG, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            // Execute migration
            migrationEngine.migrateSchema(oldDef, newDef);
            
            // Verify data is preserved and can hold larger values
            jdbcTemplate.update(
                "INSERT INTO tbl_test (id, created_at, updated_at, count) VALUES (?, ?, ?, ?)",
                "2", Instant.now(), Instant.now(), 9999999999L
            );
            
            Long count = jdbcTemplate.queryForObject(
                "SELECT count FROM tbl_test WHERE id = '2'", Long.class);
            assertEquals(9999999999L, count);
            
            // Verify migration was recorded
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            assertTrue(history.stream().anyMatch(r -> r.migrationType() == MigrationType.ALTER_COLUMN_TYPE));
        }
        
        @Test
        @DisplayName("Should throw exception for incompatible type changes during migration")
        void shouldThrowExceptionForIncompatibleTypeChanges() {
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("flag", FieldType.BOOLEAN, true, false, false, null, null, null, null)
            );
            setupTestTable("tbl_test", oldFields);
            
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("flag", FieldType.INTEGER, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            assertThrows(IncompatibleSchemaChangeException.class,
                () -> migrationEngine.migrateSchema(oldDef, newDef));
        }
        
        @Test
        @DisplayName("Should handle multiple changes in single migration")
        void shouldHandleMultipleChangesInSingleMigration() {
            // Create initial table
            List<FieldDefinition> oldFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("count", FieldType.INTEGER, true, false, false, null, null, null, null),
                new FieldDefinition("old_field", FieldType.STRING, true, false, false, null, null, null, null)
            );
            setupTestTable("tbl_test", oldFields);
            
            // Define new schema with multiple changes
            List<FieldDefinition> newFields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null),
                new FieldDefinition("count", FieldType.LONG, true, false, false, null, null, null, null),
                new FieldDefinition("new_field", FieldType.BOOLEAN, true, false, false, null, null, null, null)
            );
            
            CollectionDefinition oldDef = createCollection("test", oldFields);
            CollectionDefinition newDef = createCollection("test", newFields);
            
            // Execute migration
            migrationEngine.migrateSchema(oldDef, newDef);
            
            // Verify all migrations were recorded
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            assertEquals(3, history.size());
            
            assertTrue(history.stream().anyMatch(r -> r.migrationType() == MigrationType.ADD_COLUMN));
            assertTrue(history.stream().anyMatch(r -> r.migrationType() == MigrationType.DEPRECATE_COLUMN));
            assertTrue(history.stream().anyMatch(r -> r.migrationType() == MigrationType.ALTER_COLUMN_TYPE));
        }
        
        @Test
        @DisplayName("Should do nothing when schemas are identical")
        void shouldDoNothingWhenSchemasAreIdentical() {
            List<FieldDefinition> fields = List.of(
                new FieldDefinition("name", FieldType.STRING, false, false, false, null, null, null, null)
            );
            setupTestTable("tbl_test", fields);
            
            CollectionDefinition oldDef = createCollection("test", fields);
            CollectionDefinition newDef = createCollection("test", fields);
            
            // Execute migration
            migrationEngine.migrateSchema(oldDef, newDef);
            
            // Verify no migrations were recorded
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            assertTrue(history.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Migration Record String Type Tests")
    class MigrationRecordStringTypeTests {
        
        @Test
        @DisplayName("Should accept string migration type")
        void shouldAcceptStringMigrationType() {
            migrationEngine.recordMigration("test", "CREATE_TABLE", "CREATE TABLE...");
            
            List<MigrationRecord> history = migrationEngine.getMigrationHistory("test");
            assertEquals(1, history.size());
            assertEquals(MigrationType.CREATE_TABLE, history.get(0).migrationType());
        }
        
        @Test
        @DisplayName("Should throw for invalid string migration type")
        void shouldThrowForInvalidStringMigrationType() {
            assertThrows(IllegalArgumentException.class,
                () -> migrationEngine.recordMigration("test", "INVALID_TYPE", "SQL"));
        }
    }
}
