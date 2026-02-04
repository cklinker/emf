package com.emf.runtime.storage;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Engine for managing schema migrations in Mode A (Physical Tables) storage.
 * 
 * <p>This engine handles:
 * <ul>
 *   <li>Migration history tracking in the {@code emf_migrations} table</li>
 *   <li>Schema diff detection (added fields, removed fields, type changes)</li>
 *   <li>ALTER TABLE generation for adding columns</li>
 *   <li>Column deprecation (mark but don't drop)</li>
 *   <li>Type change validation and execution</li>
 * </ul>
 * 
 * <h2>Migration History Table</h2>
 * <p>The {@code emf_migrations} table tracks all schema changes with columns:
 * <ul>
 *   <li>id - auto-increment primary key</li>
 *   <li>collection_name - the collection being migrated</li>
 *   <li>migration_type - CREATE_TABLE, ADD_COLUMN, DEPRECATE_COLUMN, ALTER_COLUMN_TYPE</li>
 *   <li>sql_statement - the executed SQL</li>
 *   <li>executed_at - timestamp of execution</li>
 * </ul>
 * 
 * <h2>Type Change Compatibility</h2>
 * <p>The following type changes are allowed:
 * <ul>
 *   <li>STRING → any type (with data loss risk)</li>
 *   <li>INTEGER → LONG, DOUBLE, STRING</li>
 *   <li>LONG → DOUBLE, STRING</li>
 *   <li>DOUBLE → STRING</li>
 *   <li>BOOLEAN → STRING</li>
 *   <li>DATE → DATETIME, STRING</li>
 *   <li>DATETIME → STRING</li>
 *   <li>JSON → STRING</li>
 * </ul>
 * 
 * @see StorageAdapter
 * @see PhysicalTableStorageAdapter
 * @since 1.0.0
 */
@Service
public class SchemaMigrationEngine {
    
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationEngine.class);
    
    /**
     * Migration types for tracking in the history table.
     */
    public enum MigrationType {
        CREATE_TABLE,
        ADD_COLUMN,
        DEPRECATE_COLUMN,
        ALTER_COLUMN_TYPE
    }
    
    private static final String MIGRATIONS_TABLE = "emf_migrations";
    
    /**
     * Type compatibility map: for each source type, defines which target types are allowed.
     */
    private static final Map<FieldType, Set<FieldType>> TYPE_COMPATIBILITY = new EnumMap<>(FieldType.class);
    
    static {
        // STRING can change to any type (with data loss risk)
        TYPE_COMPATIBILITY.put(FieldType.STRING, EnumSet.allOf(FieldType.class));
        
        // INTEGER can change to LONG, DOUBLE, STRING
        TYPE_COMPATIBILITY.put(FieldType.INTEGER, EnumSet.of(
            FieldType.INTEGER, FieldType.LONG, FieldType.DOUBLE, FieldType.STRING));
        
        // LONG can change to DOUBLE, STRING
        TYPE_COMPATIBILITY.put(FieldType.LONG, EnumSet.of(
            FieldType.LONG, FieldType.DOUBLE, FieldType.STRING));
        
        // DOUBLE can change to STRING
        TYPE_COMPATIBILITY.put(FieldType.DOUBLE, EnumSet.of(
            FieldType.DOUBLE, FieldType.STRING));
        
        // BOOLEAN can change to STRING
        TYPE_COMPATIBILITY.put(FieldType.BOOLEAN, EnumSet.of(
            FieldType.BOOLEAN, FieldType.STRING));
        
        // DATE can change to DATETIME, STRING
        TYPE_COMPATIBILITY.put(FieldType.DATE, EnumSet.of(
            FieldType.DATE, FieldType.DATETIME, FieldType.STRING));
        
        // DATETIME can change to STRING
        TYPE_COMPATIBILITY.put(FieldType.DATETIME, EnumSet.of(
            FieldType.DATETIME, FieldType.STRING));
        
        // JSON can change to STRING
        TYPE_COMPATIBILITY.put(FieldType.JSON, EnumSet.of(
            FieldType.JSON, FieldType.STRING));
    }
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Creates a new SchemaMigrationEngine.
     * 
     * @param jdbcTemplate the JdbcTemplate for database operations
     */
    public SchemaMigrationEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initializeMigrationTable();
    }
    
    /**
     * Initializes the migration history table if it doesn't exist.
     */
    private void initializeMigrationTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS emf_migrations (
                id SERIAL PRIMARY KEY,
                collection_name VARCHAR(255) NOT NULL,
                migration_type VARCHAR(50) NOT NULL,
                sql_statement TEXT NOT NULL,
                executed_at TIMESTAMP NOT NULL
            )
            """;
        
        try {
            jdbcTemplate.execute(sql);
            log.debug("Migration history table initialized");
        } catch (Exception e) {
            log.warn("Could not initialize migration table (may already exist): {}", e.getMessage());
        }
    }
    
    /**
     * Records a migration in the history table.
     * 
     * @param collectionName the collection being migrated
     * @param migrationType the type of migration
     * @param sqlStatement the SQL statement that was executed
     */
    public void recordMigration(String collectionName, MigrationType migrationType, String sqlStatement) {
        String sql = """
            INSERT INTO emf_migrations (collection_name, migration_type, sql_statement, executed_at)
            VALUES (?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql, collectionName, migrationType.name(), sqlStatement, 
            Timestamp.from(Instant.now()));
        
        log.info("Recorded migration for collection '{}': {}", collectionName, migrationType);
    }
    
    /**
     * Records a migration in the history table using a string migration type.
     * 
     * @param collectionName the collection being migrated
     * @param migrationType the type of migration as a string
     * @param sqlStatement the SQL statement that was executed
     */
    public void recordMigration(String collectionName, String migrationType, String sqlStatement) {
        recordMigration(collectionName, MigrationType.valueOf(migrationType), sqlStatement);
    }
    
    /**
     * Migrates the schema from an old collection definition to a new one.
     * 
     * <p>This method:
     * <ol>
     *   <li>Detects added fields and generates ALTER TABLE ADD COLUMN statements</li>
     *   <li>Detects removed fields and marks them as deprecated (adds comment)</li>
     *   <li>Detects type changes, validates compatibility, and generates ALTER TABLE ALTER COLUMN TYPE</li>
     * </ol>
     * 
     * @param oldDefinition the previous collection definition
     * @param newDefinition the new collection definition
     * @throws IncompatibleSchemaChangeException if a type change is incompatible
     * @throws StorageException if migration fails
     */
    public void migrateSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        String tableName = getTableName(newDefinition);
        List<MigrationAction> migrations = new ArrayList<>();
        
        // Detect added fields
        for (FieldDefinition newField : newDefinition.fields()) {
            if (oldDefinition.getField(newField.name()) == null) {
                migrations.add(createAddColumnMigration(tableName, newDefinition.name(), newField));
            }
        }
        
        // Detect removed fields (mark as deprecated, don't drop)
        for (FieldDefinition oldField : oldDefinition.fields()) {
            if (newDefinition.getField(oldField.name()) == null) {
                migrations.add(createDeprecateColumnMigration(tableName, newDefinition.name(), oldField));
            }
        }
        
        // Detect type changes
        for (FieldDefinition newField : newDefinition.fields()) {
            FieldDefinition oldField = oldDefinition.getField(newField.name());
            if (oldField != null && !oldField.type().equals(newField.type())) {
                validateTypeChange(newDefinition.name(), oldField, newField);
                migrations.add(createAlterColumnTypeMigration(tableName, newDefinition.name(), oldField, newField));
            }
        }
        
        // Execute all migrations
        for (MigrationAction migration : migrations) {
            executeMigration(migration);
        }
        
        log.info("Schema migration completed for collection '{}': {} changes applied", 
            newDefinition.name(), migrations.size());
    }
    
    /**
     * Detects schema differences between two collection definitions.
     * 
     * @param oldDefinition the previous collection definition
     * @param newDefinition the new collection definition
     * @return a list of detected schema differences
     */
    public List<SchemaDiff> detectDifferences(CollectionDefinition oldDefinition, 
            CollectionDefinition newDefinition) {
        List<SchemaDiff> diffs = new ArrayList<>();
        
        // Detect added fields
        for (FieldDefinition newField : newDefinition.fields()) {
            if (oldDefinition.getField(newField.name()) == null) {
                diffs.add(new SchemaDiff(SchemaDiff.DiffType.FIELD_ADDED, newField.name(), 
                    null, newField.type()));
            }
        }
        
        // Detect removed fields
        for (FieldDefinition oldField : oldDefinition.fields()) {
            if (newDefinition.getField(oldField.name()) == null) {
                diffs.add(new SchemaDiff(SchemaDiff.DiffType.FIELD_REMOVED, oldField.name(), 
                    oldField.type(), null));
            }
        }
        
        // Detect type changes
        for (FieldDefinition newField : newDefinition.fields()) {
            FieldDefinition oldField = oldDefinition.getField(newField.name());
            if (oldField != null && !oldField.type().equals(newField.type())) {
                diffs.add(new SchemaDiff(SchemaDiff.DiffType.TYPE_CHANGED, newField.name(), 
                    oldField.type(), newField.type()));
            }
        }
        
        return diffs;
    }
    
    /**
     * Validates that a type change is compatible.
     * 
     * @param collectionName the collection name
     * @param oldField the old field definition
     * @param newField the new field definition
     * @throws IncompatibleSchemaChangeException if the type change is not allowed
     */
    public void validateTypeChange(String collectionName, FieldDefinition oldField, 
            FieldDefinition newField) {
        FieldType oldType = oldField.type();
        FieldType newType = newField.type();
        
        Set<FieldType> allowedTypes = TYPE_COMPATIBILITY.get(oldType);
        if (allowedTypes == null || !allowedTypes.contains(newType)) {
            throw new IncompatibleSchemaChangeException(collectionName, oldField.name(), 
                oldType, newType);
        }
        
        log.debug("Type change validated for field '{}': {} -> {}", 
            oldField.name(), oldType, newType);
    }
    
    /**
     * Checks if a type change is compatible without throwing an exception.
     * 
     * @param oldType the current field type
     * @param newType the requested new field type
     * @return true if the type change is allowed, false otherwise
     */
    public boolean isTypeChangeCompatible(FieldType oldType, FieldType newType) {
        if (oldType == newType) {
            return true;
        }
        Set<FieldType> allowedTypes = TYPE_COMPATIBILITY.get(oldType);
        return allowedTypes != null && allowedTypes.contains(newType);
    }
    
    /**
     * Gets the migration history for a collection.
     * 
     * @param collectionName the collection name
     * @return list of migration records
     */
    public List<MigrationRecord> getMigrationHistory(String collectionName) {
        String sql = """
            SELECT id, collection_name, migration_type, sql_statement, executed_at
            FROM emf_migrations
            WHERE collection_name = ?
            ORDER BY executed_at ASC
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MigrationRecord(
            rs.getLong("id"),
            rs.getString("collection_name"),
            MigrationType.valueOf(rs.getString("migration_type")),
            rs.getString("sql_statement"),
            rs.getTimestamp("executed_at").toInstant()
        ), collectionName);
    }
    
    /**
     * Gets all migration history.
     * 
     * @return list of all migration records
     */
    public List<MigrationRecord> getAllMigrationHistory() {
        String sql = """
            SELECT id, collection_name, migration_type, sql_statement, executed_at
            FROM emf_migrations
            ORDER BY executed_at ASC
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MigrationRecord(
            rs.getLong("id"),
            rs.getString("collection_name"),
            MigrationType.valueOf(rs.getString("migration_type")),
            rs.getString("sql_statement"),
            rs.getTimestamp("executed_at").toInstant()
        ));
    }
    
    // ==================== Private Helper Methods ====================
    
    private String getTableName(CollectionDefinition definition) {
        if (definition.storageConfig() != null && definition.storageConfig().tableName() != null) {
            return definition.storageConfig().tableName();
        }
        return "tbl_" + definition.name();
    }
    
    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        return identifier;
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
            case JSON -> "JSONB";
        };
    }
    
    private MigrationAction createAddColumnMigration(String tableName, String collectionName, 
            FieldDefinition field) {
        StringBuilder sql = new StringBuilder("ALTER TABLE ");
        sql.append(sanitizeIdentifier(tableName));
        sql.append(" ADD COLUMN ");
        sql.append(sanitizeIdentifier(field.name()));
        sql.append(" ");
        sql.append(mapFieldTypeToSql(field.type()));
        
        // Note: We don't add NOT NULL for new columns as existing rows would fail
        // The application layer handles nullability validation
        
        if (field.unique()) {
            sql.append(" UNIQUE");
        }
        
        return new MigrationAction(collectionName, MigrationType.ADD_COLUMN, sql.toString());
    }
    
    private MigrationAction createDeprecateColumnMigration(String tableName, String collectionName, 
            FieldDefinition field) {
        // Mark column as deprecated by adding a comment
        // We don't drop the column to preserve data
        String sql = String.format(
            "COMMENT ON COLUMN %s.%s IS 'DEPRECATED: This column is no longer in use as of %s'",
            sanitizeIdentifier(tableName),
            sanitizeIdentifier(field.name()),
            Instant.now().toString()
        );
        
        return new MigrationAction(collectionName, MigrationType.DEPRECATE_COLUMN, sql);
    }
    
    private MigrationAction createAlterColumnTypeMigration(String tableName, String collectionName,
            FieldDefinition oldField, FieldDefinition newField) {
        String newSqlType = mapFieldTypeToSql(newField.type());
        
        // PostgreSQL syntax for changing column type with USING clause for type conversion
        String sql = String.format(
            "ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s",
            sanitizeIdentifier(tableName),
            sanitizeIdentifier(newField.name()),
            newSqlType,
            sanitizeIdentifier(newField.name()),
            newSqlType
        );
        
        return new MigrationAction(collectionName, MigrationType.ALTER_COLUMN_TYPE, sql);
    }
    
    private void executeMigration(MigrationAction migration) {
        try {
            jdbcTemplate.execute(migration.sql());
            recordMigration(migration.collectionName(), migration.type(), migration.sql());
            log.debug("Executed migration: {} - {}", migration.type(), migration.sql());
        } catch (Exception e) {
            throw new StorageException(
                String.format("Failed to execute migration for collection '%s': %s", 
                    migration.collectionName(), e.getMessage()), e);
        }
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Represents a migration action to be executed.
     */
    private record MigrationAction(
        String collectionName,
        MigrationType type,
        String sql
    ) {}
    
    /**
     * Represents a schema difference between two collection definitions.
     */
    public record SchemaDiff(
        DiffType diffType,
        String fieldName,
        FieldType oldType,
        FieldType newType
    ) {
        public enum DiffType {
            FIELD_ADDED,
            FIELD_REMOVED,
            TYPE_CHANGED
        }
    }
    
    /**
     * Represents a migration record from the history table.
     */
    public record MigrationRecord(
        long id,
        String collectionName,
        MigrationType migrationType,
        String sqlStatement,
        Instant executedAt
    ) {}
}
