package com.emf.runtime.storage;

import com.emf.runtime.context.TenantContext;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.query.FilterCondition;
import com.emf.runtime.query.FilterOperator;
import com.emf.runtime.query.Pagination;
import com.emf.runtime.query.QueryRequest;
import com.emf.runtime.query.QueryResult;
import com.emf.runtime.query.SortField;
import com.emf.runtime.validation.TypeCoercionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Storage adapter implementation for Mode A (Physical Tables).
 *
 * <p>Each collection maps to a real PostgreSQL table with columns matching field definitions.
 * This is the default storage mode when no mode is specified.
 *
 * <p>When schema-per-tenant isolation is enabled, tenant collections are stored in
 * separate PostgreSQL schemas named by the tenant's slug. System collections remain
 * in the public schema. Schema-qualified table names are used to ensure queries
 * target the correct schema explicitly.
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

    /** Column names handled as system fields in create/update — skipped in the user-defined field loop. */
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
        "id", "created_at", "updated_at", "created_by", "updated_by", "tenant_id"
    );

    private final JdbcTemplate jdbcTemplate;
    private final SchemaMigrationEngine migrationEngine;
    private final boolean schemaPerTenantEnabled;

    /**
     * Creates a new PhysicalTableStorageAdapter.
     *
     * @param jdbcTemplate the JdbcTemplate for database operations
     * @param migrationEngine the schema migration engine for handling schema changes
     * @param schemaPerTenantEnabled whether to use separate schemas per tenant
     */
    public PhysicalTableStorageAdapter(
            JdbcTemplate jdbcTemplate,
            SchemaMigrationEngine migrationEngine,
            @Value("${emf.tenant-isolation.schema-per-tenant:false}") boolean schemaPerTenantEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.migrationEngine = migrationEngine;
        this.schemaPerTenantEnabled = schemaPerTenantEnabled;
    }

    @Override
    public void initializeCollection(CollectionDefinition definition) {
        // System collections use Flyway-managed tables — skip table creation
        if (definition.systemCollection()) {
            log.info("Skipping table creation for system collection '{}' (table '{}' managed by Flyway)",
                    definition.name(), getBaseTableName(definition));
            return;
        }

        TableRef tableRef = getTableRef(definition);
        String qualifiedName = tableRef.toSql();

        // Ensure the tenant schema exists before creating the table
        if (!tableRef.isPublicSchema()) {
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + tableRef.schema() + "\"");
        }

        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sql.append(qualifiedName).append(" (");
        sql.append("id VARCHAR(36) PRIMARY KEY, ");
        sql.append("created_by VARCHAR(36), ");
        sql.append("updated_by VARCHAR(36), ");
        sql.append("created_at TIMESTAMP NOT NULL, ");
        sql.append("updated_at TIMESTAMP NOT NULL");

        List<String> postCreateStatements = new ArrayList<>();

        for (FieldDefinition field : definition.fields()) {
            if (!field.type().hasPhysicalColumn()) {
                continue; // Skip FORMULA, ROLLUP_SUMMARY
            }

            String sqlType = mapFieldTypeToSql(field.type());
            String columnName = getColumnName(definition, field);
            sql.append(", ");
            sql.append(sanitizeIdentifier(columnName)).append(" ").append(sqlType);

            if (!field.nullable()) {
                sql.append(" NOT NULL");
            }

            if (field.unique()) {
                sql.append(" UNIQUE");
            }

            // Companion columns
            if (field.type() == FieldType.CURRENCY) {
                sql.append(", ");
                sql.append(sanitizeIdentifier(field.name() + "_currency_code")).append(" VARCHAR(3)");
            }
            if (field.type() == FieldType.GEOLOCATION) {
                sql.append(", ");
                sql.append(sanitizeIdentifier(field.name() + "_longitude")).append(" DOUBLE PRECISION");
            }

            // Unique index for EXTERNAL_ID
            if (field.type() == FieldType.EXTERNAL_ID) {
                String idxName = "idx_" + sanitizeIdentifier(getBaseTableName(definition))
                    + "_" + sanitizeIdentifier(field.name());
                postCreateStatements.add(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " + idxName
                    + " ON " + qualifiedName + "(" + sanitizeIdentifier(field.name()) + ")"
                );
            }

            // FK constraints for LOOKUP and MASTER_DETAIL
            if ((field.type() == FieldType.LOOKUP || field.type() == FieldType.MASTER_DETAIL)
                    && field.referenceConfig() != null) {
                String baseName = getBaseTableName(definition);
                String targetTableName = "tbl_" + sanitizeIdentifier(field.referenceConfig().targetCollection());
                // Target table is in the same schema as the source table
                TableRef targetRef = tableRef.isPublicSchema()
                        ? TableRef.publicSchema(targetTableName)
                        : TableRef.tenantSchema(tableRef.schema(), targetTableName);
                String targetCol = sanitizeIdentifier(field.referenceConfig().targetField());
                String fkName = "fk_" + sanitizeIdentifier(baseName) + "_" + sanitizeIdentifier(field.name());
                String onDelete = field.type() == FieldType.MASTER_DETAIL
                        ? "ON DELETE CASCADE" : "ON DELETE SET NULL";

                postCreateStatements.add(
                    "DO $$ BEGIN " +
                    "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '" + fkName + "') THEN " +
                    "ALTER TABLE " + qualifiedName +
                    " ADD CONSTRAINT " + fkName +
                    " FOREIGN KEY (" + sanitizeIdentifier(field.name()) + ")" +
                    " REFERENCES " + targetRef.toSql() + "(" + targetCol + ") " + onDelete + "; " +
                    "END IF; END $$"
                );
            }
        }

        sql.append(")");

        try {
            jdbcTemplate.execute(sql.toString());

            for (String stmt : postCreateStatements) {
                jdbcTemplate.execute(stmt);
            }

            // Record the migration in history
            migrationEngine.recordMigration(definition.name(),
                SchemaMigrationEngine.MigrationType.CREATE_TABLE, sql.toString());

            // Reconcile schema: if the table already existed, CREATE TABLE IF NOT EXISTS
            // was a no-op and the table may be missing columns added after its creation.
            // This introspects the actual table columns and adds any missing ones.
            migrationEngine.reconcileSchema(definition, tableRef);

            log.info("Initialized table '{}' for collection '{}'", qualifiedName, definition.name());
        } catch (DataAccessException e) {
            throw new StorageException("Failed to initialize table for collection: " + definition.name(), e);
        }
    }

    @Override
    public void updateCollectionSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        // Delegate schema migration to the migration engine
        TableRef tableRef = getTableRef(newDefinition);
        migrationEngine.migrateSchema(oldDefinition, newDefinition, tableRef);
        log.info("Schema update completed for collection '{}'", newDefinition.name());
    }

    @Override
    public QueryResult query(CollectionDefinition definition, QueryRequest request) {
        TableRef tableRef = getTableRef(definition);
        List<Object> params = new ArrayList<>();

        // Build SELECT clause
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(buildSelectClause(request.fields(), definition));
        sql.append(" FROM ").append(tableRef.toSql());

        // Build WHERE clause for filters
        List<FilterCondition> allFilters = new ArrayList<>();
        if (request.hasFilters()) {
            allFilters.addAll(request.filters());
        }

        if (!allFilters.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(allFilters, definition, params));
        }

        // Build ORDER BY clause
        if (request.hasSorting()) {
            sql.append(" ORDER BY ");
            sql.append(buildOrderByClause(request.sorting(), definition));
        }

        // Build LIMIT and OFFSET for pagination
        Pagination pagination = request.pagination();
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pagination.pageSize());
        params.add(pagination.offset());

        try {
            // Execute query
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            // Remap column names to field names for system collections
            remapColumnNames(definition, data);

            // Reconstruct companion column values into structured fields
            reconstructCompanionColumns(definition, data);

            // Get total count
            long totalCount = getTotalCount(tableRef, allFilters, definition);

            return QueryResult.of(data, totalCount, pagination);
        } catch (DataAccessException e) {
            throw new StorageException("Failed to query collection: " + definition.name(), e);
        }
    }

    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        TableRef tableRef = getTableRef(definition);
        String sql = "SELECT * FROM " + tableRef.toSql() + " WHERE id = ?";

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id);
            if (results.isEmpty()) {
                return Optional.empty();
            }
            // Remap column names to field names for system collections
            remapColumnNames(definition, results);
            // Reconstruct companion column values
            reconstructCompanionColumns(definition, results);
            return Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new StorageException("Failed to get record by ID from collection: " + definition.name(), e);
        }
    }

    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        TableRef tableRef = getTableRef(definition);

        // Build column names, placeholders, and values.
        // JSONB columns need ?::jsonb placeholders so PostgreSQL accepts the
        // value as JSONB instead of VARCHAR.
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Add system fields
        columns.add("id");
        placeholders.add("?");
        values.add(data.get("id"));
        columns.add("created_by");
        placeholders.add("?");
        values.add(data.get("createdBy"));
        columns.add("updated_by");
        placeholders.add("?");
        values.add(data.get("updatedBy"));
        columns.add("created_at");
        placeholders.add("?");
        values.add(convertValueForStorage(data.get("createdAt"), FieldType.DATETIME));
        columns.add("updated_at");
        placeholders.add("?");
        values.add(convertValueForStorage(data.get("updatedAt"), FieldType.DATETIME));

        // For tenant-scoped system collections, add tenant_id
        if (definition.systemCollection() && definition.tenantScoped() && data.containsKey("tenantId")) {
            columns.add("tenant_id");
            placeholders.add("?");
            values.add(data.get("tenantId"));
        }

        // Add user-defined fields
        for (FieldDefinition field : definition.fields()) {
            if (!field.type().hasPhysicalColumn()) {
                continue; // Skip FORMULA, ROLLUP_SUMMARY
            }
            if (data.containsKey(field.name())) {
                String columnName = getColumnName(definition, field);
                if (SYSTEM_COLUMNS.contains(columnName)) {
                    continue; // Already handled as a system field above
                }
                boolean isJsonb = field.type() == FieldType.JSON || field.type() == FieldType.ARRAY;
                columns.add(sanitizeIdentifier(columnName));
                placeholders.add(isJsonb ? "?::jsonb" : "?");
                values.add(convertValueForStorage(data.get(field.name()), field.type()));

                // Handle companion columns
                if (field.type() == FieldType.CURRENCY && data.containsKey(field.name() + "_currency_code")) {
                    columns.add(sanitizeIdentifier(columnName + "_currency_code"));
                    placeholders.add("?");
                    values.add(data.get(field.name() + "_currency_code"));
                }
                if (field.type() == FieldType.GEOLOCATION && data.get(field.name()) instanceof Map<?,?> geo) {
                    columns.add(sanitizeIdentifier(columnName + "_longitude"));
                    placeholders.add("?");
                    values.add(((Number) geo.get("longitude")).doubleValue());
                }
            }
        }

        String columnList = String.join(", ", columns);
        String placeholderList = String.join(", ", placeholders);

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
            tableRef.toSql(), columnList, placeholderList);

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
        TableRef tableRef = getTableRef(definition);

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

        // Update audit field
        if (data.containsKey("updatedBy")) {
            setClauses.add("updated_by = ?");
            values.add(data.get("updatedBy"));
        }

        // Update user-defined fields
        for (FieldDefinition field : definition.fields()) {
            if (data.containsKey(field.name())) {
                String columnName = getColumnName(definition, field);
                if (SYSTEM_COLUMNS.contains(columnName)) {
                    continue; // Already handled as a system field above
                }
                boolean isJsonb = field.type() == FieldType.JSON || field.type() == FieldType.ARRAY;
                setClauses.add(sanitizeIdentifier(columnName) + (isJsonb ? " = ?::jsonb" : " = ?"));
                values.add(convertValueForStorage(data.get(field.name()), field.type()));
            }
        }

        // Add ID for WHERE clause
        values.add(id);

        String sql = String.format("UPDATE %s SET %s WHERE id = ?",
            tableRef.toSql(), String.join(", ", setClauses));

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
        TableRef tableRef = getTableRef(definition);

        // Log cascade-affected MASTER_DETAIL child records before delete
        for (FieldDefinition field : definition.fields()) {
            if (field.type() == FieldType.MASTER_DETAIL && field.referenceConfig() != null
                    && field.referenceConfig().isMasterDetail()) {
                log.info("Cascade delete: record '{}' in '{}' will cascade via MASTER_DETAIL field '{}'",
                        id, definition.name(), field.name());
            }
        }

        String sql = "DELETE FROM " + tableRef.toSql() + " WHERE id = ?";

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
        TableRef tableRef = getTableRef(definition);
        String columnName = resolveColumnName(definition, fieldName);

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ");
        sql.append(tableRef.toSql());
        sql.append(" WHERE ").append(sanitizeIdentifier(columnName)).append(" = ?");

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
     * Resolves the table reference for a collection, applying schema-per-tenant
     * isolation when enabled.
     *
     * <p>System collections always resolve to the public schema. Tenant collections
     * resolve to the tenant's schema (named by slug) when schema-per-tenant is enabled,
     * or to the public schema when disabled.
     *
     * @param definition the collection definition
     * @return the resolved table reference
     */
    TableRef getTableRef(CollectionDefinition definition) {
        String tableName = getBaseTableName(definition);

        // System collections always in public schema
        if (definition.systemCollection()) {
            return TableRef.publicSchema(tableName);
        }

        // When schema-per-tenant is enabled, use tenant slug as schema
        if (schemaPerTenantEnabled) {
            String tenantSlug = TenantContext.getSlug();
            if (tenantSlug != null && !tenantSlug.isBlank()) {
                return TableRef.tenantSchema(tenantSlug, tableName);
            }
        }

        // Fallback to public schema
        return TableRef.publicSchema(tableName);
    }

    /**
     * Gets the base table name for a collection (without schema qualification).
     *
     * @param definition the collection definition
     * @return the base table name
     */
    private String getBaseTableName(CollectionDefinition definition) {
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
            case REFERENCE -> "VARCHAR(36)";
            case ARRAY -> "JSONB";
            case PICKLIST -> "VARCHAR(255)";
            case MULTI_PICKLIST -> "TEXT[]";
            case CURRENCY -> "NUMERIC(18,2)";
            case PERCENT -> "NUMERIC(8,4)";
            case AUTO_NUMBER -> "VARCHAR(100)";
            case PHONE -> "VARCHAR(40)";
            case EMAIL -> "VARCHAR(320)";
            case URL -> "VARCHAR(2048)";
            case RICH_TEXT -> "TEXT";
            case ENCRYPTED -> "BYTEA";
            case EXTERNAL_ID -> "VARCHAR(255)";
            case GEOLOCATION -> "DOUBLE PRECISION";
            case LOOKUP -> "VARCHAR(36)";
            case MASTER_DETAIL -> "VARCHAR(36)";
            case FORMULA, ROLLUP_SUMMARY -> null;
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

        // Always include id, created_at, updated_at, created_by, updated_by
        List<String> selectFields = new ArrayList<>();
        selectFields.add("id");
        selectFields.add("created_at");
        selectFields.add("updated_at");
        selectFields.add("created_by");
        selectFields.add("updated_by");

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
     * @param definition the collection definition (used for column name mapping)
     * @param params the parameter list to populate
     * @return the WHERE clause (without the WHERE keyword)
     */
    private String buildWhereClause(List<FilterCondition> filters, CollectionDefinition definition,
                                     List<Object> params) {
        return filters.stream()
            .map(filter -> buildFilterCondition(filter, definition, params))
            .collect(Collectors.joining(" AND "));
    }

    /**
     * Builds a single filter condition SQL fragment.
     *
     * @param filter the filter condition
     * @param definition the collection definition (used for column name mapping)
     * @param params the parameter list to populate
     * @return the SQL fragment for this filter
     */
    private String buildFilterCondition(FilterCondition filter, CollectionDefinition definition,
                                         List<Object> params) {
        String fieldName = sanitizeIdentifier(resolveColumnName(definition, filter.fieldName()));
        FilterOperator operator = filter.operator();
        Object value = filter.value();

        // Coerce string filter values to match the field's database type (e.g., "false" → Boolean.FALSE)
        FieldDefinition fieldDef = definition.getField(filter.fieldName());
        if (fieldDef != null && value instanceof String) {
            value = TypeCoercionService.coerceValue(value, fieldDef.type());
        }

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
            case IN -> {
                if (value instanceof Collection<?> coll) {
                    if (coll.isEmpty()) {
                        yield "1 = 0"; // always false for empty IN list
                    }
                    String ph = coll.stream()
                            .map(v -> {
                                params.add(v);
                                return "?";
                            })
                            .collect(Collectors.joining(", "));
                    yield fieldName + " IN (" + ph + ")";
                } else {
                    // Single value fallback
                    params.add(value);
                    yield fieldName + " = ?";
                }
            }
        };
    }

    /**
     * Builds the ORDER BY clause from sort fields.
     *
     * @param sorting the sort fields
     * @param definition the collection definition (used for column name mapping)
     * @return the ORDER BY clause (without the ORDER BY keyword)
     */
    private String buildOrderByClause(List<SortField> sorting, CollectionDefinition definition) {
        return sorting.stream()
            .map(sort -> sanitizeIdentifier(resolveColumnName(definition, sort.fieldName()))
                    + " " + sort.direction().name())
            .collect(Collectors.joining(", "));
    }

    /**
     * Gets the total count of records matching the filter conditions.
     *
     * @param tableRef the table reference
     * @param filters the filter conditions
     * @param definition the collection definition (used for column name mapping)
     * @return the total count
     */
    private long getTotalCount(TableRef tableRef, List<FilterCondition> filters,
                                CollectionDefinition definition) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ");
        sql.append(tableRef.toSql());

        List<Object> params = new ArrayList<>();
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(filters, definition, params));
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
            case JSON, ARRAY -> {
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
                // Handle LocalDate conversion (for DATE fields)
                if (value instanceof java.time.LocalDate localDate) {
                    yield java.sql.Date.valueOf(localDate);
                }
                // Handle LocalDateTime conversion
                if (value instanceof java.time.LocalDateTime localDateTime) {
                    yield java.sql.Timestamp.valueOf(localDateTime);
                }
                // Safety net: parse String if coercion was bypassed
                if (value instanceof String str) {
                    String trimmed = str.trim();
                    try {
                        yield java.sql.Timestamp.from(java.time.Instant.parse(trimmed));
                    } catch (java.time.format.DateTimeParseException e) {
                        // Try LocalDateTime
                    }
                    try {
                        java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(trimmed);
                        yield java.sql.Timestamp.from(ldt.toInstant(java.time.ZoneOffset.UTC));
                    } catch (java.time.format.DateTimeParseException e) {
                        // Try LocalDate
                    }
                    try {
                        java.time.LocalDate ld = java.time.LocalDate.parse(trimmed);
                        if (type == FieldType.DATE) {
                            yield java.sql.Date.valueOf(ld);
                        }
                        yield java.sql.Timestamp.from(ld.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
                    } catch (java.time.format.DateTimeParseException e) {
                        // Cannot parse — fall through and let JDBC handle it
                    }
                }
                yield value;
            }
            case MULTI_PICKLIST -> {
                if (value instanceof List<?> list) {
                    yield list.toArray(new String[0]);
                }
                yield value;
            }
            case GEOLOCATION -> {
                // Value is Map with latitude/longitude; store latitude in primary column
                if (value instanceof Map<?,?> geo) {
                    yield ((Number) geo.get("latitude")).doubleValue();
                }
                yield value;
            }
            default -> value;
        };
    }

    /**
     * Gets the effective column name for a field within a collection.
     * For system collections, fields may have a different physical column name
     * (e.g., API field "firstName" → DB column "first_name").
     *
     * @param definition the collection definition
     * @param field the field definition
     * @return the effective column name
     */
    private String getColumnName(CollectionDefinition definition, FieldDefinition field) {
        if (definition.systemCollection()) {
            // Check field-level column name first
            if (field.columnName() != null) {
                return field.columnName();
            }
            // Then check collection-level column mapping
            String mapped = definition.columnMapping().get(field.name());
            if (mapped != null) {
                return mapped;
            }
        }
        return field.name();
    }

    /**
     * Resolves an API field name to its physical database column name.
     * Handles system field names (createdAt → created_at, etc.) and
     * collection-level column mappings.
     *
     * @param definition the collection definition
     * @param fieldName the API field name
     * @return the physical column name
     */
    private String resolveColumnName(CollectionDefinition definition, String fieldName) {
        // System audit fields always map to snake_case columns
        return switch (fieldName) {
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "createdBy" -> "created_by";
            case "updatedBy" -> "updated_by";
            case "tenantId" -> "tenant_id";
            default -> {
                if (definition.systemCollection()) {
                    yield definition.getEffectiveColumnName(fieldName);
                }
                yield fieldName;
            }
        };
    }

    /**
     * Remaps column names in query results from physical database column names
     * back to API field names. This is necessary for system collections where
     * the column names (snake_case) differ from API field names (camelCase).
     *
     * <p>For non-system collections, this is a no-op since column names
     * match field names.
     *
     * @param definition the collection definition
     * @param records the query result records to remap in place
     */
    private void remapColumnNames(CollectionDefinition definition, List<Map<String, Object>> records) {
        if (!definition.systemCollection()) {
            return;
        }

        // Build reverse mapping: column name → field name
        Map<String, String> reverseMap = new HashMap<>();

        // System audit fields
        reverseMap.put("created_at", "createdAt");
        reverseMap.put("updated_at", "updatedAt");
        reverseMap.put("created_by", "createdBy");
        reverseMap.put("updated_by", "updatedBy");
        reverseMap.put("tenant_id", "tenantId");

        // Collection-level column mappings
        for (Map.Entry<String, String> entry : definition.columnMapping().entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }

        // Field-level column names
        for (FieldDefinition field : definition.fields()) {
            if (field.columnName() != null) {
                reverseMap.put(field.columnName(), field.name());
            }
        }

        // Apply remapping to each record and normalize JDBC types
        for (Map<String, Object> record : records) {
            Map<String, Object> remapped = new HashMap<>();
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                String columnName = entry.getKey();
                String fName = reverseMap.getOrDefault(columnName, columnName);
                Object value = entry.getValue();
                // Convert java.sql.Timestamp to java.time.Instant so downstream
                // validation (isValidDateTime) and serialization work correctly.
                if (value instanceof java.sql.Timestamp ts) {
                    value = ts.toInstant();
                }
                remapped.put(fName, value);
            }
            record.clear();
            record.putAll(remapped);
        }
    }

    /**
     * Post-processes query results to convert JDBC-specific types into
     * JSON-serializable Java types and reconstructs structured values
     * from companion columns.
     *
     * <ul>
     *   <li>MULTI_PICKLIST: converts {@code java.sql.Array} (PgArray) to {@code List<String>}</li>
     *   <li>CURRENCY: combines primary column (amount) with _currency_code companion</li>
     *   <li>GEOLOCATION: combines primary column (latitude) with _longitude companion into a Map</li>
     * </ul>
     */
    private void reconstructCompanionColumns(CollectionDefinition definition, List<Map<String, Object>> records) {
        for (FieldDefinition field : definition.fields()) {
            if (field.type() == FieldType.MULTI_PICKLIST) {
                for (Map<String, Object> record : records) {
                    Object value = record.get(field.name());
                    if (value instanceof java.sql.Array sqlArray) {
                        try {
                            Object array = sqlArray.getArray();
                            if (array instanceof String[] strings) {
                                record.put(field.name(), Arrays.asList(strings));
                            } else {
                                record.put(field.name(), List.of());
                            }
                        } catch (SQLException e) {
                            log.warn("Failed to convert SQL array for field '{}': {}", field.name(), e.getMessage());
                            record.put(field.name(), List.of());
                        }
                    }
                }
            } else if (field.type() == FieldType.CURRENCY) {
                String codeKey = field.name() + "_currency_code";
                for (Map<String, Object> record : records) {
                    // Ensure currency_code is accessible via the companion key name
                    // The raw column name from JDBC may already be present
                    if (!record.containsKey(codeKey)) {
                        // Nothing to reconstruct
                        continue;
                    }
                }
            } else if (field.type() == FieldType.GEOLOCATION) {
                String lngKey = field.name() + "_longitude";
                for (Map<String, Object> record : records) {
                    Object lat = record.get(field.name());
                    Object lng = record.get(lngKey);
                    if (lat instanceof Number && lng instanceof Number) {
                        Map<String, Object> geo = new HashMap<>();
                        geo.put("latitude", ((Number) lat).doubleValue());
                        geo.put("longitude", ((Number) lng).doubleValue());
                        record.put(field.name(), geo);
                        record.remove(lngKey);
                    }
                }
            }
        }
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
