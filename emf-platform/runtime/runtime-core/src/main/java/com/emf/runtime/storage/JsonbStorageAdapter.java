package com.emf.runtime.storage;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.query.FilterCondition;
import com.emf.runtime.query.FilterOperator;
import com.emf.runtime.query.Pagination;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.query.SortField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Storage adapter implementation for Mode B (JSONB Document Store).
 * 
 * <p>All collections are stored in a single shared table {@code emf_collections} with
 * data stored as JSONB. This mode is useful for highly dynamic schemas or when
 * table creation is restricted.
 * 
 * <h2>Table Structure</h2>
 * <pre>
 * CREATE TABLE emf_collections (
 *     id VARCHAR(36) PRIMARY KEY,
 *     collection_name VARCHAR(255) NOT NULL,
 *     data JSONB NOT NULL,
 *     created_at TIMESTAMP NOT NULL,
 *     updated_at TIMESTAMP NOT NULL
 * );
 * CREATE INDEX idx_emf_collections_name ON emf_collections(collection_name);
 * </pre>
 * 
 * <h2>JSONB Query Operators</h2>
 * <ul>
 *   <li>{@code data->>'fieldName'} - Extract field as text for string comparisons</li>
 *   <li>{@code data->'fieldName'} - Extract field as JSON for nested operations</li>
 *   <li>{@code (data->>'fieldName')::numeric} - Cast to numeric for number comparisons</li>
 * </ul>
 * 
 * @see StorageAdapter
 * @see com.emf.runtime.model.StorageMode#JSONB_STORE
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(name = "emf.storage.mode", havingValue = "JSONB_STORE")
public class JsonbStorageAdapter implements StorageAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(JsonbStorageAdapter.class);
    
    /**
     * The shared table name for all collections in JSONB mode.
     */
    public static final String TABLE_NAME = "emf_collections";
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    // Track which collections have been initialized to avoid redundant table creation
    private volatile boolean tableInitialized = false;
    
    /**
     * Creates a new JsonbStorageAdapter.
     * 
     * @param jdbcTemplate the JdbcTemplate for database operations
     */
    public JsonbStorageAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Register Java 8 time module
    }
    
    /**
     * Creates a new JsonbStorageAdapter with a custom ObjectMapper.
     * 
     * @param jdbcTemplate the JdbcTemplate for database operations
     * @param objectMapper the ObjectMapper for JSON serialization
     */
    public JsonbStorageAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void initializeCollection(CollectionDefinition definition) {
        // Ensure the shared table exists (idempotent)
        ensureTableExists();
        log.info("Initialized JSONB storage for collection '{}'", definition.name());
    }
    
    /**
     * Ensures the shared emf_collections table exists.
     * This method is idempotent and thread-safe.
     */
    private synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS emf_collections (
                id VARCHAR(36) PRIMARY KEY,
                collection_name VARCHAR(255) NOT NULL,
                data JSONB NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """;
        
        String createIndexSql = """
            CREATE INDEX IF NOT EXISTS idx_emf_collections_name 
            ON emf_collections(collection_name)
            """;
        
        try {
            jdbcTemplate.execute(createTableSql);
            jdbcTemplate.execute(createIndexSql);
            tableInitialized = true;
            log.info("Initialized shared JSONB table '{}'", TABLE_NAME);
        } catch (DataAccessException e) {
            throw new StorageException("Failed to initialize JSONB storage table", e);
        }
    }
    
    @Override
    public void updateCollectionSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        // No-op for JSONB mode - schema is flexible
        // JSONB storage doesn't require schema migrations since data is stored as JSON
        log.debug("Schema update for collection '{}' - no action needed for JSONB mode", 
            newDefinition.name());
    }
    
    @Override
    public QueryResult query(CollectionDefinition definition, QueryRequest request) {
        ensureTableExists();
        
        List<Object> params = new ArrayList<>();
        
        // Build SELECT query - fetch all data and filter in Java for H2 compatibility
        // For PostgreSQL production use, this could be optimized with native JSONB operators
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, collection_name, data, created_at, updated_at FROM ");
        sql.append(TABLE_NAME);
        sql.append(" WHERE collection_name = ?");
        params.add(definition.name());
        
        try {
            // Fetch all records for this collection
            List<String> requestedFields = request.fields();
            List<Map<String, Object>> allData = jdbcTemplate.query(
                sql.toString(),
                new JsonbRowMapper(List.of()), // Get all fields first
                params.toArray()
            );
            
            // Apply filters in Java (for H2 compatibility)
            List<Map<String, Object>> filteredData = allData;
            if (request.hasFilters()) {
                filteredData = applyFiltersInMemory(allData, request.filters(), definition);
            }
            
            // Apply sorting in Java
            if (request.hasSorting()) {
                filteredData = applySortingInMemory(filteredData, request.sorting(), definition);
            }
            
            // Get total count before pagination
            long totalCount = filteredData.size();
            
            // Apply pagination
            Pagination pagination = request.pagination();
            int startIndex = pagination.offset();
            int endIndex = Math.min(startIndex + pagination.pageSize(), filteredData.size());
            
            List<Map<String, Object>> pagedData;
            if (startIndex >= filteredData.size()) {
                pagedData = List.of();
            } else {
                pagedData = filteredData.subList(startIndex, endIndex);
            }
            
            // Apply field selection
            if (request.hasFieldSelection()) {
                pagedData = applyFieldSelection(pagedData, requestedFields);
            }
            
            return QueryResult.of(pagedData, totalCount, pagination);
        } catch (DataAccessException e) {
            throw new StorageException("Failed to query collection: " + definition.name(), e);
        }
    }
    
    /**
     * Applies filters to data in memory (for H2 compatibility).
     */
    private List<Map<String, Object>> applyFiltersInMemory(List<Map<String, Object>> data,
            List<FilterCondition> filters, CollectionDefinition definition) {
        return data.stream()
            .filter(record -> matchesAllFilters(record, filters, definition))
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if a record matches all filter conditions.
     */
    private boolean matchesAllFilters(Map<String, Object> record, List<FilterCondition> filters,
            CollectionDefinition definition) {
        for (FilterCondition filter : filters) {
            if (!matchesFilter(record, filter, definition)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Checks if a record matches a single filter condition.
     */
    private boolean matchesFilter(Map<String, Object> record, FilterCondition filter,
            CollectionDefinition definition) {
        String fieldName = filter.fieldName();
        Object recordValue = record.get(fieldName);
        Object filterValue = filter.value();
        FilterOperator operator = filter.operator();
        
        FieldDefinition fieldDef = definition.getField(fieldName);
        boolean isNumeric = fieldDef != null && isNumericType(fieldDef.type());
        
        return switch (operator) {
            case EQ -> {
                if (recordValue == null && filterValue == null) yield true;
                if (recordValue == null || filterValue == null) yield false;
                if (isNumeric) {
                    yield toDouble(recordValue) == toDouble(filterValue);
                }
                yield recordValue.toString().equals(filterValue.toString());
            }
            case NEQ -> {
                if (recordValue == null && filterValue == null) yield false;
                if (recordValue == null || filterValue == null) yield true;
                if (isNumeric) {
                    yield toDouble(recordValue) != toDouble(filterValue);
                }
                yield !recordValue.toString().equals(filterValue.toString());
            }
            case GT -> {
                if (recordValue == null) yield false;
                if (isNumeric) {
                    yield toDouble(recordValue) > toDouble(filterValue);
                }
                yield recordValue.toString().compareTo(filterValue.toString()) > 0;
            }
            case LT -> {
                if (recordValue == null) yield false;
                if (isNumeric) {
                    yield toDouble(recordValue) < toDouble(filterValue);
                }
                yield recordValue.toString().compareTo(filterValue.toString()) < 0;
            }
            case GTE -> {
                if (recordValue == null) yield false;
                if (isNumeric) {
                    yield toDouble(recordValue) >= toDouble(filterValue);
                }
                yield recordValue.toString().compareTo(filterValue.toString()) >= 0;
            }
            case LTE -> {
                if (recordValue == null) yield false;
                if (isNumeric) {
                    yield toDouble(recordValue) <= toDouble(filterValue);
                }
                yield recordValue.toString().compareTo(filterValue.toString()) <= 0;
            }
            case ISNULL -> {
                boolean checkNull = filterValue instanceof Boolean ? (Boolean) filterValue 
                    : Boolean.parseBoolean(filterValue.toString());
                yield checkNull ? (recordValue == null) : (recordValue != null);
            }
            case CONTAINS -> {
                if (recordValue == null) yield false;
                yield recordValue.toString().contains(filterValue.toString());
            }
            case STARTS -> {
                if (recordValue == null) yield false;
                yield recordValue.toString().startsWith(filterValue.toString());
            }
            case ENDS -> {
                if (recordValue == null) yield false;
                yield recordValue.toString().endsWith(filterValue.toString());
            }
            case ICONTAINS -> {
                if (recordValue == null) yield false;
                yield recordValue.toString().toLowerCase().contains(filterValue.toString().toLowerCase());
            }
            case ISTARTS -> {
                if (recordValue == null) yield false;
                yield recordValue.toString().toLowerCase().startsWith(filterValue.toString().toLowerCase());
            }
            case IENDS -> {
                if (recordValue == null) yield false;
                yield recordValue.toString().toLowerCase().endsWith(filterValue.toString().toLowerCase());
            }
            case IEQ -> {
                if (recordValue == null && filterValue == null) yield true;
                if (recordValue == null || filterValue == null) yield false;
                yield recordValue.toString().equalsIgnoreCase(filterValue.toString());
            }
        };
    }
    
    /**
     * Applies sorting to data in memory.
     */
    private List<Map<String, Object>> applySortingInMemory(List<Map<String, Object>> data,
            List<SortField> sorting, CollectionDefinition definition) {
        List<Map<String, Object>> sortedData = new ArrayList<>(data);
        
        sortedData.sort((a, b) -> {
            for (SortField sort : sorting) {
                String fieldName = sort.fieldName();
                Object valueA = a.get(fieldName);
                Object valueB = b.get(fieldName);
                
                FieldDefinition fieldDef = definition.getField(fieldName);
                boolean isNumeric = fieldDef != null && isNumericType(fieldDef.type());
                
                int comparison;
                if (valueA == null && valueB == null) {
                    comparison = 0;
                } else if (valueA == null) {
                    comparison = -1;
                } else if (valueB == null) {
                    comparison = 1;
                } else if (isNumeric) {
                    comparison = Double.compare(toDouble(valueA), toDouble(valueB));
                } else {
                    comparison = valueA.toString().compareTo(valueB.toString());
                }
                
                if (sort.direction() == com.emf.runtime.query.SortDirection.DESC) {
                    comparison = -comparison;
                }
                
                if (comparison != 0) {
                    return comparison;
                }
            }
            return 0;
        });
        
        return sortedData;
    }
    
    /**
     * Applies field selection to data.
     */
    private List<Map<String, Object>> applyFieldSelection(List<Map<String, Object>> data,
            List<String> fields) {
        Set<String> fieldSet = Set.copyOf(fields);
        
        return data.stream()
            .map(record -> {
                Map<String, Object> filtered = new HashMap<>();
                // Always include system fields
                filtered.put("id", record.get("id"));
                if (record.containsKey("createdAt")) {
                    filtered.put("createdAt", record.get("createdAt"));
                }
                if (record.containsKey("updatedAt")) {
                    filtered.put("updatedAt", record.get("updatedAt"));
                }
                // Include requested fields
                for (String field : fieldSet) {
                    if (record.containsKey(field)) {
                        filtered.put(field, record.get(field));
                    }
                }
                return filtered;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Converts a value to double for numeric comparisons.
     */
    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
    
    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        ensureTableExists();
        
        String sql = "SELECT id, collection_name, data, created_at, updated_at FROM " + 
            TABLE_NAME + " WHERE id = ? AND collection_name = ?";
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.query(
                sql,
                new JsonbRowMapper(List.of()),
                id,
                definition.name()
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new StorageException("Failed to get record by ID from collection: " + definition.name(), e);
        }
    }
    
    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        ensureTableExists();
        
        String id = (String) data.get("id");
        Instant createdAt = (Instant) data.get("createdAt");
        Instant updatedAt = (Instant) data.get("updatedAt");
        
        // Prepare JSONB data (exclude system fields that are stored in separate columns)
        Map<String, Object> jsonbData = new HashMap<>(data);
        jsonbData.remove("id");
        jsonbData.remove("createdAt");
        jsonbData.remove("updatedAt");
        
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(jsonbData);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize data to JSON", e);
        }
        
        String sql = "INSERT INTO " + TABLE_NAME + 
            " (id, collection_name, data, created_at, updated_at) VALUES (?, ?, ?::jsonb, ?, ?)";
        
        try {
            jdbcTemplate.update(sql, 
                id, 
                definition.name(), 
                jsonData,
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            log.debug("Created record with ID '{}' in collection '{}'", id, definition.name());
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
        ensureTableExists();
        
        // Check if record exists
        Optional<Map<String, Object>> existing = getById(definition, id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        
        Instant updatedAt = (Instant) data.get("updatedAt");
        
        // Merge existing data with updates
        Map<String, Object> existingData = existing.get();
        Map<String, Object> mergedData = new HashMap<>(existingData);
        
        // Update only the fields that are present in the update data
        for (FieldDefinition field : definition.fields()) {
            if (data.containsKey(field.name())) {
                mergedData.put(field.name(), data.get(field.name()));
            }
        }
        
        // Prepare JSONB data (exclude system fields)
        Map<String, Object> jsonbData = new HashMap<>(mergedData);
        jsonbData.remove("id");
        jsonbData.remove("createdAt");
        jsonbData.remove("updatedAt");
        
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(jsonbData);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize data to JSON", e);
        }
        
        String sql = "UPDATE " + TABLE_NAME + 
            " SET data = ?::jsonb, updated_at = ? WHERE id = ? AND collection_name = ?";
        
        try {
            int rowsAffected = jdbcTemplate.update(sql, 
                jsonData,
                Timestamp.from(updatedAt),
                id,
                definition.name()
            );
            
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
        ensureTableExists();
        
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ? AND collection_name = ?";
        
        try {
            int rowsAffected = jdbcTemplate.update(sql, id, definition.name());
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
        ensureTableExists();
        
        // Validate field name
        sanitizeJsonPath(fieldName);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(TABLE_NAME);
        sql.append(" WHERE collection_name = ?");
        
        List<Object> params = new ArrayList<>();
        params.add(definition.name());
        
        if (excludeId != null) {
            sql.append(" AND id != ?");
            params.add(excludeId);
        }
        
        try {
            // Query all records for this collection and check uniqueness in Java
            // This is more compatible across databases (H2 vs PostgreSQL)
            String querySql = sql.toString().replace("SELECT COUNT(*)", "SELECT data");
            List<String> dataList = jdbcTemplate.queryForList(querySql, String.class, params.toArray());
            
            String valueStr = value != null ? value.toString() : null;
            
            for (String jsonData : dataList) {
                if (jsonData != null) {
                    try {
                        String normalizedJson = normalizeJsonString(jsonData);
                        Map<String, Object> dataMap = objectMapper.readValue(
                            normalizedJson,
                            new TypeReference<Map<String, Object>>() {}
                        );
                        
                        Object fieldValue = dataMap.get(fieldName);
                        if (fieldValue != null && fieldValue.toString().equals(valueStr)) {
                            return false; // Found a duplicate
                        }
                        if (fieldValue == null && valueStr == null) {
                            return false; // Both are null, considered duplicate
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse JSONB data during uniqueness check: {}", e.getMessage());
                    }
                }
            }
            
            return true; // No duplicates found
        } catch (DataAccessException e) {
            throw new StorageException("Failed to check uniqueness for field: " + fieldName, e);
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Checks if a field type is numeric.
     * 
     * @param type the field type
     * @return true if the type is numeric
     */
    private boolean isNumericType(com.emf.runtime.model.FieldType type) {
        return type == com.emf.runtime.model.FieldType.INTEGER ||
               type == com.emf.runtime.model.FieldType.LONG ||
               type == com.emf.runtime.model.FieldType.DOUBLE;
    }
    
    /**
     * Sanitizes a JSON path to prevent injection.
     * Only allows alphanumeric characters and underscores.
     * 
     * @param path the JSON path
     * @return the sanitized path
     */
    private String sanitizeJsonPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("JSON path cannot be null or blank");
        }
        // Only allow alphanumeric characters and underscores
        if (!path.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid JSON path: " + path);
        }
        return path;
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
    
    /**
     * Row mapper for JSONB query results.
     */
    private class JsonbRowMapper implements RowMapper<Map<String, Object>> {
        
        private final List<String> requestedFields;
        private final Set<String> requestedFieldSet;
        
        JsonbRowMapper(List<String> requestedFields) {
            this.requestedFields = requestedFields;
            this.requestedFieldSet = requestedFields != null && !requestedFields.isEmpty() 
                ? Set.copyOf(requestedFields) 
                : Set.of();
        }
        
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> record = new HashMap<>();
            
            // Always include system fields
            record.put("id", rs.getString("id"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                record.put("createdAt", createdAt.toInstant());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                record.put("updatedAt", updatedAt.toInstant());
            }
            
            // Parse JSONB data
            String jsonData = rs.getString("data");
            if (jsonData != null) {
                try {
                    // Handle H2's double-escaped JSON strings
                    String normalizedJson = normalizeJsonString(jsonData);
                    
                    Map<String, Object> dataMap = objectMapper.readValue(
                        normalizedJson, 
                        new TypeReference<Map<String, Object>>() {}
                    );
                    
                    // Apply field selection if specified
                    if (!requestedFieldSet.isEmpty()) {
                        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                            if (requestedFieldSet.contains(entry.getKey())) {
                                record.put(entry.getKey(), entry.getValue());
                            }
                        }
                    } else {
                        record.putAll(dataMap);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse JSONB data for record {}: {}", 
                        rs.getString("id"), e.getMessage());
                }
            }
            
            return record;
        }
    }
    
    /**
     * Normalizes a JSON string that may be double-escaped (e.g., from H2).
     * H2 stores JSON as a string which gets escaped, so we need to unescape it.
     * 
     * @param jsonData the potentially double-escaped JSON string
     * @return the normalized JSON string
     */
    private String normalizeJsonString(String jsonData) {
        if (jsonData == null) {
            return null;
        }
        
        // Check if the string is double-quoted (H2 behavior)
        // H2 returns JSON as: "{\"key\":\"value\"}" (with outer quotes and escaped inner quotes)
        if (jsonData.startsWith("\"") && jsonData.endsWith("\"")) {
            // Remove outer quotes and unescape
            String unquoted = jsonData.substring(1, jsonData.length() - 1);
            // Unescape the escaped quotes
            return unquoted.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        
        return jsonData;
    }
}
