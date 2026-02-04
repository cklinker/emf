package com.emf.runtime.storage;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.query.FilterCondition;
import com.emf.runtime.query.FilterOperator;
import com.emf.runtime.query.Pagination;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.query.SortField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Storage adapter implementation for Mode A (Physical Tables).
 * 
 * <p>Each collection maps to a real PostgreSQL table with columns matching field definitions.
 * This is the default storage mode when no mode is specified.
 * 
 * <h2>SQL Type Mapping</h2>
 * <ul>
 *   <li>STRING → TEXT</li>
 *   <li>INTEGER → INTEGER</li>
 *   <li>LONG → BIGINT</li>
 *   <li>DOUBLE → DOUBLE PRECISION</li>
 *   <li>BOOLEAN → BOOLEAN</li>
 *   <li>DATE → DATE</li>
 *   <li>DATETIME → TIMESTAMP</li>
 *   <li>JSON → JSONB</li>
 * </ul>
 * 
 * <h2>Filter Operators</h2>
 * Supports all filter operators including eq, neq, gt, lt, gte, lte, isnull,
 * contains, starts, ends, icontains, istarts, iends, and ieq.
 * 
 * @see StorageAdapter
 * @see SchemaMigrationEngine
 * @see com.emf.runtime.model.StorageMode#PHYSICAL_TABLES
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(name = "emf.storage.mode", havingValue = "PHYSICAL_TABLES", matchIfMissing = true)
public class PhysicalTableStorageAdapter implements StorageAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(PhysicalTableStorageAdapter.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final SchemaMigrationEngine migrationEngine;
    
    /**
     * Creates a new PhysicalTableStorageAdapter.
     * 
     * @param jdbcTemplate the JdbcTemplate for database operations
     * @param migrationEngine the schema migration engine for handling schema changes
     */
    public PhysicalTableStorageAdapter(JdbcTemplate jdbcTemplate, SchemaMigrationEngine migrationEngine) {
        this.jdbcTemplate = jdbcTemplate;
        this.migrationEngine = migrationEngine;
    }
    
    @Override
    public void initializeCollection(CollectionDefinition definition) {
        String tableName = getTableName(definition);
        
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sql.append(sanitizeIdentifier(tableName)).append(" (");
        sql.append("id VARCHAR(36) PRIMARY KEY, ");
        sql.append("created_at TIMESTAMP NOT NULL, ");
        sql.append("updated_at TIMESTAMP NOT NULL");
        
        for (FieldDefinition field : definition.fields()) {
            sql.append(", ");
            sql.append(sanitizeIdentifier(field.name())).append(" ");
            sql.append(mapFieldTypeToSql(field.type()));
            
            if (!field.nullable()) {
                sql.append(" NOT NULL");
            }
            
            if (field.unique()) {
                sql.append(" UNIQUE");
            }
        }
        
        sql.append(")");
        
        try {
            jdbcTemplate.execute(sql.toString());
            
            // Record the migration in history
            migrationEngine.recordMigration(definition.name(), 
                SchemaMigrationEngine.MigrationType.CREATE_TABLE, sql.toString());
            
            log.info("Initialized table '{}' for collection '{}'", tableName, definition.name());
        } catch (DataAccessException e) {
            throw new StorageException("Failed to initialize table for collection: " + definition.name(), e);
        }
    }
    
    @Override
    public void updateCollectionSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        // Delegate schema migration to the migration engine
        migrationEngine.migrateSchema(oldDefinition, newDefinition);
        log.info("Schema update completed for collection '{}'", newDefinition.name());
    }
    
    @Override
    public QueryResult query(CollectionDefinition definition, QueryRequest request) {
        String tableName = getTableName(definition);
        List<Object> params = new ArrayList<>();
        
        // Build SELECT clause
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(buildSelectClause(request.fields(), definition));
        sql.append(" FROM ").append(sanitizeIdentifier(tableName));
        
        // Build WHERE clause for filters
        if (request.hasFilters()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(request.filters(), params));
        }
        
        // Build ORDER BY clause
        if (request.hasSorting()) {
            sql.append(" ORDER BY ");
            sql.append(buildOrderByClause(request.sorting()));
        }
        
        // Build LIMIT and OFFSET for pagination
        Pagination pagination = request.pagination();
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pagination.pageSize());
        params.add(pagination.offset());
        
        try {
            // Execute query
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            
            // Get total count
            long totalCount = getTotalCount(tableName, request.filters());
            
            return QueryResult.of(data, totalCount, pagination);
        } catch (DataAccessException e) {
            throw new StorageException("Failed to query collection: " + definition.name(), e);
        }
    }

    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        String tableName = getTableName(definition);
        String sql = "SELECT * FROM " + sanitizeIdentifier(tableName) + " WHERE id = ?";
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new StorageException("Failed to get record by ID from collection: " + definition.name(), e);
        }
    }
    
    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        String tableName = getTableName(definition);
        
        // Build column names and placeholders
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        // Add system fields
        columns.add("id");
        values.add(data.get("id"));
        columns.add("created_at");
        values.add(convertValueForStorage(data.get("createdAt"), FieldType.DATETIME));
        columns.add("updated_at");
        values.add(convertValueForStorage(data.get("updatedAt"), FieldType.DATETIME));
        
        // Add user-defined fields
        for (FieldDefinition field : definition.fields()) {
            if (data.containsKey(field.name())) {
                columns.add(sanitizeIdentifier(field.name()));
                values.add(convertValueForStorage(data.get(field.name()), field.type()));
            }
        }
        
        String columnList = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
            sanitizeIdentifier(tableName), columnList, placeholders);
        
        try {
            jdbcTemplate.update(sql, values.toArray());
            log.debug("Created record with ID '{}' in collection '{}'", data.get("id"), definition.name());
            return data;
        } catch (DuplicateKeyException e) {
            // Determine which field caused the violation
            String fieldName = detectUniqueViolationField(definition, data, e);
            throw new UniqueConstraintViolationException(
                definition.name(), fieldName, data.get(fieldName), e);
        } catch (DataAccessException e) {
            throw new StorageException("Failed to create record in collection: " + definition.name(), e);
        }
    }
    
    @Override
    public Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data) {
        String tableName = getTableName(definition);
        
        // Check if record exists
        Optional<Map<String, Object>> existing = getById(definition, id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        
        // Build SET clause
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        // Always update updated_at
        setClauses.add("updated_at = ?");
        values.add(convertValueForStorage(data.get("updatedAt"), FieldType.DATETIME));
        
        // Update user-defined fields
        for (FieldDefinition field : definition.fields()) {
            if (data.containsKey(field.name())) {
                setClauses.add(sanitizeIdentifier(field.name()) + " = ?");
                values.add(convertValueForStorage(data.get(field.name()), field.type()));
            }
        }
        
        // Add ID for WHERE clause
        values.add(id);
        
        String sql = String.format("UPDATE %s SET %s WHERE id = ?",
            sanitizeIdentifier(tableName), String.join(", ", setClauses));
        
        try {
            int rowsAffected = jdbcTemplate.update(sql, values.toArray());
            if (rowsAffected == 0) {
                return Optional.empty();
            }
            
            log.debug("Updated record with ID '{}' in collection '{}'", id, definition.name());
            
            // Return the updated record
            return getById(definition, id);
        } catch (DuplicateKeyException e) {
            String fieldName = detectUniqueViolationField(definition, data, e);
            throw new UniqueConstraintViolationException(
                definition.name(), fieldName, data.get(fieldName), e);
        } catch (DataAccessException e) {
            throw new StorageException("Failed to update record in collection: " + definition.name(), e);
        }
    }
    
    @Override
    public boolean delete(CollectionDefinition definition, String id) {
        String tableName = getTableName(definition);
        String sql = "DELETE FROM " + sanitizeIdentifier(tableName) + " WHERE id = ?";
        
        try {
            int rowsAffected = jdbcTemplate.update(sql, id);
            if (rowsAffected > 0) {
                log.debug("Deleted record with ID '{}' from collection '{}'", id, definition.name());
            }
            return rowsAffected > 0;
        } catch (DataAccessException e) {
            throw new StorageException("Failed to delete record from collection: " + definition.name(), e);
        }
    }
    
    @Override
    public boolean isUnique(CollectionDefinition definition, String fieldName, Object value, String excludeId) {
        String tableName = getTableName(definition);
        
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ");
        sql.append(sanitizeIdentifier(tableName));
        sql.append(" WHERE ").append(sanitizeIdentifier(fieldName)).append(" = ?");
        
        List<Object> params = new ArrayList<>();
        params.add(value);
        
        if (excludeId != null) {
            sql.append(" AND id != ?");
            params.add(excludeId);
        }
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
            return count == null || count == 0;
        } catch (DataAccessException e) {
            throw new StorageException("Failed to check uniqueness for field: " + fieldName, e);
        }
    }

    // ==================== Helper Methods ====================
    
    /**
     * Gets the table name for a collection.
     * 
     * @param definition the collection definition
     * @return the table name
     */
    private String getTableName(CollectionDefinition definition) {
        if (definition.storageConfig() != null && definition.storageConfig().tableName() != null) {
            return definition.storageConfig().tableName();
        }
        return "tbl_" + definition.name();
    }
    
    /**
     * Sanitizes an identifier (table name or column name) to prevent SQL injection.
     * Only allows alphanumeric characters and underscores.
     * 
     * @param identifier the identifier to sanitize
     * @return the sanitized identifier
     */
    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }
        // Only allow alphanumeric characters and underscores
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        return identifier;
    }
    
    /**
     * Maps a FieldType to the corresponding PostgreSQL SQL type.
     * 
     * @param type the field type
     * @return the SQL type string
     */
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
    
    /**
     * Builds the SELECT clause for a query.
     * 
     * @param fields the requested fields (empty means all fields)
     * @param definition the collection definition
     * @return the SELECT clause
     */
    private String buildSelectClause(List<String> fields, CollectionDefinition definition) {
        if (fields == null || fields.isEmpty()) {
            return "*";
        }
        
        // Always include id, created_at, updated_at
        List<String> selectFields = new ArrayList<>();
        selectFields.add("id");
        selectFields.add("created_at");
        selectFields.add("updated_at");
        
        for (String field : fields) {
            if (!selectFields.contains(field)) {
                selectFields.add(sanitizeIdentifier(field));
            }
        }
        
        return String.join(", ", selectFields);
    }
    
    /**
     * Builds the WHERE clause from filter conditions.
     * 
     * @param filters the filter conditions
     * @param params the parameter list to populate
     * @return the WHERE clause (without the WHERE keyword)
     */
    private String buildWhereClause(List<FilterCondition> filters, List<Object> params) {
        return filters.stream()
            .map(filter -> buildFilterCondition(filter, params))
            .collect(Collectors.joining(" AND "));
    }
    
    /**
     * Builds a single filter condition SQL fragment.
     * 
     * @param filter the filter condition
     * @param params the parameter list to populate
     * @return the SQL fragment for this filter
     */
    private String buildFilterCondition(FilterCondition filter, List<Object> params) {
        String fieldName = sanitizeIdentifier(filter.fieldName());
        FilterOperator operator = filter.operator();
        Object value = filter.value();
        
        return switch (operator) {
            case EQ -> {
                params.add(value);
                yield fieldName + " = ?";
            }
            case NEQ -> {
                params.add(value);
                yield fieldName + " != ?";
            }
            case GT -> {
                params.add(value);
                yield fieldName + " > ?";
            }
            case LT -> {
                params.add(value);
                yield fieldName + " < ?";
            }
            case GTE -> {
                params.add(value);
                yield fieldName + " >= ?";
            }
            case LTE -> {
                params.add(value);
                yield fieldName + " <= ?";
            }
            case ISNULL -> {
                // value is a boolean indicating whether to check for null or not null
                boolean isNull = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
                yield isNull ? fieldName + " IS NULL" : fieldName + " IS NOT NULL";
            }
            case CONTAINS -> {
                params.add("%" + value + "%");
                yield fieldName + " LIKE ?";
            }
            case STARTS -> {
                params.add(value + "%");
                yield fieldName + " LIKE ?";
            }
            case ENDS -> {
                params.add("%" + value);
                yield fieldName + " LIKE ?";
            }
            case ICONTAINS -> {
                params.add("%" + value.toString().toLowerCase() + "%");
                yield "LOWER(" + fieldName + ") LIKE ?";
            }
            case ISTARTS -> {
                params.add(value.toString().toLowerCase() + "%");
                yield "LOWER(" + fieldName + ") LIKE ?";
            }
            case IENDS -> {
                params.add("%" + value.toString().toLowerCase());
                yield "LOWER(" + fieldName + ") LIKE ?";
            }
            case IEQ -> {
                params.add(value.toString().toLowerCase());
                yield "LOWER(" + fieldName + ") = ?";
            }
        };
    }
    
    /**
     * Builds the ORDER BY clause from sort fields.
     * 
     * @param sorting the sort fields
     * @return the ORDER BY clause (without the ORDER BY keyword)
     */
    private String buildOrderByClause(List<SortField> sorting) {
        return sorting.stream()
            .map(sort -> sanitizeIdentifier(sort.fieldName()) + " " + sort.direction().name())
            .collect(Collectors.joining(", "));
    }
    
    /**
     * Gets the total count of records matching the filter conditions.
     * 
     * @param tableName the table name
     * @param filters the filter conditions
     * @return the total count
     */
    private long getTotalCount(String tableName, List<FilterCondition> filters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ");
        sql.append(sanitizeIdentifier(tableName));
        
        List<Object> params = new ArrayList<>();
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(filters, params));
        }
        
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }
    
    /**
     * Converts a value for storage based on the field type.
     * 
     * @param value the value to convert
     * @param type the field type
     * @return the converted value
     */
    private Object convertValueForStorage(Object value, FieldType type) {
        if (value == null) {
            return null;
        }
        
        return switch (type) {
            case JSON -> {
                // Convert Map/List to JSON string for JSONB storage
                if (value instanceof Map || value instanceof List) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = 
                            new com.fasterxml.jackson.databind.ObjectMapper();
                        yield mapper.writeValueAsString(value);
                    } catch (Exception e) {
                        throw new StorageException("Failed to convert value to JSON", e);
                    }
                }
                yield value;
            }
            case DATE, DATETIME -> {
                // Handle Instant conversion
                if (value instanceof java.time.Instant instant) {
                    yield java.sql.Timestamp.from(instant);
                }
                yield value;
            }
            default -> value;
        };
    }
    
    /**
     * Attempts to detect which field caused a unique constraint violation.
     * 
     * @param definition the collection definition
     * @param data the data that was being inserted/updated
     * @param e the exception
     * @return the field name that likely caused the violation, or "unknown"
     */
    private String detectUniqueViolationField(CollectionDefinition definition, Map<String, Object> data, 
            DuplicateKeyException e) {
        // Check each unique field to see which one has a duplicate
        for (FieldDefinition field : definition.fields()) {
            if (field.unique() && data.containsKey(field.name())) {
                if (!isUnique(definition, field.name(), data.get(field.name()), (String) data.get("id"))) {
                    return field.name();
                }
            }
        }
        
        // Check if it's the primary key
        if (data.containsKey("id")) {
            return "id";
        }
        
        return "unknown";
    }
}
