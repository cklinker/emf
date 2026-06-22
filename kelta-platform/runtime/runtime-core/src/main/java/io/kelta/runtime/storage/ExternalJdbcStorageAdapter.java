package io.kelta.runtime.storage;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.AggregationSpec;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.SortDirection;
import io.kelta.runtime.query.SortField;
import io.kelta.runtime.storage.ExternalJdbcConnectionProvider.JdbcConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link StorageAdapter} backed by a foreign JDBC database table rather than a
 * Kelta-owned physical table.
 *
 * <p>Connection + mapping come from {@code storageConfig().adapterConfig()}:
 * <ul>
 *   <li>{@code jdbcUrl}  — required</li>
 *   <li>{@code username} / {@code password} — optional</li>
 *   <li>{@code table}    — foreign table name; default the collection name</li>
 *   <li>{@code idColumn} — primary-key column mapped to the record id; default {@code id}</li>
 * </ul>
 *
 * <p>A {@link ExternalJdbcConnectionProvider} resolves the pooled {@link JdbcTemplate}.
 * Pagination, sort, and {@code EQ} filters translate to SQL ({@code LIMIT/OFFSET},
 * {@code ORDER BY}, {@code WHERE col = ?}); values are always bound as parameters, and
 * every identifier (table, column, sort/filter field) is validated against a strict
 * pattern before interpolation, so untrusted config or field names cannot inject SQL.
 * Schema operations are no-ops (the foreign DB owns its schema); {@code aggregate} and
 * {@code semanticSearch} are unsupported.
 *
 * <p>Not a Spring bean: instantiated with a concrete provider by the wiring slice; the
 * {@link DispatchingStorageAdapter} discovers it via the {@link ExternalStorageAdapter} marker.
 *
 * @since 1.0.0
 */
public class ExternalJdbcStorageAdapter implements ExternalStorageAdapter {

    public static final String STORAGE_TYPE = "external-jdbc";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final ExternalJdbcConnectionProvider connectionProvider;
    private final CredentialProvider credentialProvider;

    public ExternalJdbcStorageAdapter(ExternalJdbcConnectionProvider connectionProvider) {
        this(connectionProvider, null);
    }

    public ExternalJdbcStorageAdapter(ExternalJdbcConnectionProvider connectionProvider,
                                      CredentialProvider credentialProvider) {
        this.connectionProvider = connectionProvider;
        this.credentialProvider = credentialProvider;
    }

    @Override
    public String storageType() {
        return STORAGE_TYPE;
    }

    // --- read ---------------------------------------------------------------

    @Override
    public QueryResult query(CollectionDefinition definition, QueryRequest request) {
        JdbcConf cfg = JdbcConf.from(definition);
        JdbcTemplate jdbc = jdbc(cfg);

        List<Object> whereParams = new ArrayList<>();
        String where = buildWhere(request, whereParams);

        Pagination pagination = request != null && request.pagination() != null
                ? request.pagination()
                : Pagination.defaults();

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(cfg.table()).append(where);
        sql.append(buildOrderBy(request));
        sql.append(" LIMIT ? OFFSET ?");
        List<Object> params = new ArrayList<>(whereParams);
        params.add(pagination.pageSize());
        params.add((long) (pagination.pageNumber() - 1) * pagination.pageSize());

        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
            rows.forEach(row -> mapId(row, cfg));

            Long total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + cfg.table() + where, Long.class, whereParams.toArray());
            long totalCount = total != null ? total : rows.size();
            int totalPages = pagination.pageSize() > 0
                    ? (int) Math.ceil((double) totalCount / pagination.pageSize())
                    : 0;
            return new QueryResult(rows,
                    new PaginationMetadata(totalCount, pagination.pageNumber(), pagination.pageSize(), totalPages));
        } catch (DataAccessException e) {
            throw new StorageException("External JDBC query failed for collection: " + definition.name(), e);
        }
    }

    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        JdbcConf cfg = JdbcConf.from(definition);
        return findById(jdbc(cfg), cfg, id);
    }

    // --- write --------------------------------------------------------------

    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        JdbcConf cfg = JdbcConf.from(definition);
        JdbcTemplate jdbc = jdbc(cfg);

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            columns.add(identifier(entry.getKey()));
            values.add(entry.getValue());
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + cfg.table() + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
        try {
            jdbc.update(sql, values.toArray());
        } catch (DataAccessException e) {
            throw new StorageException("External JDBC create failed for collection: " + definition.name(), e);
        }

        Object id = data.get(cfg.idColumn());
        if (id != null) {
            return findById(jdbc, cfg, id.toString()).orElseGet(() -> mapId(new LinkedHashMap<>(data), cfg));
        }
        return mapId(new LinkedHashMap<>(data), cfg);
    }

    @Override
    public Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data) {
        JdbcConf cfg = JdbcConf.from(definition);
        JdbcTemplate jdbc = jdbc(cfg);

        List<String> assignments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey().equals(cfg.idColumn()) || entry.getKey().equals("id")) {
                continue; // never reassign the primary key
            }
            assignments.add(identifier(entry.getKey()) + " = ?");
            values.add(entry.getValue());
        }
        if (assignments.isEmpty()) {
            return findById(jdbc, cfg, id);
        }
        values.add(id);
        String sql = "UPDATE " + cfg.table() + " SET " + String.join(", ", assignments)
                + " WHERE " + cfg.idColumn() + " = ?";
        try {
            int rows = jdbc.update(sql, values.toArray());
            return rows == 0 ? Optional.empty() : findById(jdbc, cfg, id);
        } catch (DataAccessException e) {
            throw new StorageException("External JDBC update failed for collection: " + definition.name(), e);
        }
    }

    @Override
    public boolean delete(CollectionDefinition definition, String id) {
        JdbcConf cfg = JdbcConf.from(definition);
        try {
            return jdbc(cfg).update("DELETE FROM " + cfg.table() + " WHERE " + cfg.idColumn() + " = ?", id) > 0;
        } catch (DataAccessException e) {
            throw new StorageException("External JDBC delete failed for collection: " + definition.name(), e);
        }
    }

    @Override
    public boolean isUnique(CollectionDefinition definition, String fieldName, Object value, String excludeId) {
        JdbcConf cfg = JdbcConf.from(definition);
        String column = identifier(fieldName);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(cfg.table())
                .append(" WHERE ").append(column).append(" = ?");
        List<Object> params = new ArrayList<>();
        params.add(value);
        if (excludeId != null) {
            sql.append(" AND ").append(cfg.idColumn()).append(" <> ?");
            params.add(excludeId);
        }
        try {
            Long count = jdbc(cfg).queryForObject(sql.toString(), Long.class, params.toArray());
            return count == null || count == 0;
        } catch (DataAccessException e) {
            throw new StorageException("External JDBC uniqueness check failed for collection: " + definition.name(), e);
        }
    }

    // --- schema (foreign DB owns it) ---------------------------------------

    @Override
    public void initializeCollection(CollectionDefinition definition) {
        // No-op: the foreign database owns its schema.
    }

    @Override
    public void updateCollectionSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        // No-op: the foreign database owns its schema.
    }

    @Override
    public Map<String, Object> aggregate(CollectionDefinition definition,
                                         List<FilterCondition> filters,
                                         List<AggregationSpec> specs) {
        throw new UnsupportedOperationException("Aggregation is not supported by the external-jdbc adapter");
    }

    // --- helpers ------------------------------------------------------------

    private JdbcTemplate jdbc(JdbcConf cfg) {
        String username = cfg.username();
        String password = cfg.password();
        // A vault credentialRef (basic_auth) supplies the connection user/password,
        // so the secret never lives in adapter_config; inline values are the fallback.
        if (cfg.credentialRef() != null && !cfg.credentialRef().isBlank() && credentialProvider != null) {
            ResolvedCredential cred = credentialProvider.resolve(cfg.credentialRef())
                    .orElseThrow(() -> new StorageException("Credential not found: " + cfg.credentialRef()));
            Object user = cred.secret("username");
            Object pass = cred.secret("password");
            if (user != null) {
                username = user.toString();
            }
            if (pass != null) {
                password = pass.toString();
            }
        }
        return connectionProvider.jdbcTemplate(new JdbcConfig(cfg.jdbcUrl(), username, password));
    }

    private Optional<Map<String, Object>> findById(JdbcTemplate jdbc, JdbcConf cfg, String id) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM " + cfg.table() + " WHERE " + cfg.idColumn() + " = ? LIMIT 1", id);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapId(rows.get(0), cfg));
        } catch (DataAccessException e) {
            throw new StorageException("External JDBC getById failed for table: " + cfg.table(), e);
        }
    }

    /** Copy the id column's value into the canonical {@code id} key. */
    private Map<String, Object> mapId(Map<String, Object> row, JdbcConf cfg) {
        Object id = row.get(cfg.idColumn());
        if (id != null) {
            row.put("id", id.toString());
        }
        return row;
    }

    private String buildWhere(QueryRequest request, List<Object> params) {
        if (request == null || request.filters() == null || request.filters().isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        for (FilterCondition filter : request.filters()) {
            if (filter.operator() == FilterOperator.EQ && filter.value() != null) {
                clauses.add(identifier(filter.fieldName()) + " = ?");
                params.add(filter.value());
            }
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private String buildOrderBy(QueryRequest request) {
        if (request == null || request.sorting() == null || request.sorting().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (SortField field : request.sorting()) {
            String dir = field.direction() == SortDirection.DESC ? " DESC" : " ASC";
            parts.add(identifier(field.fieldName()) + dir);
        }
        return " ORDER BY " + String.join(", ", parts);
    }

    /** Validate an identifier before SQL interpolation — values use {@code ?} binds, identifiers can't. */
    private static String identifier(String raw) {
        if (raw == null || !SAFE_IDENTIFIER.matcher(raw).matches()) {
            throw new StorageException("Unsafe SQL identifier: " + raw);
        }
        return raw;
    }

    /** Parsed view of a collection's external-JDBC {@code adapterConfig}. */
    private record JdbcConf(String jdbcUrl, String username, String password, String table, String idColumn,
                            String credentialRef) {

        static JdbcConf from(CollectionDefinition definition) {
            Map<String, String> cfg = definition.storageConfig() != null
                    ? definition.storageConfig().adapterConfig()
                    : Map.of();
            String jdbcUrl = cfg.get("jdbcUrl");
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new StorageException(
                        "external-jdbc collection '" + definition.name() + "' is missing adapterConfig.jdbcUrl");
            }
            String table = identifier(cfg.getOrDefault("table", definition.name()));
            String idColumn = identifier(cfg.getOrDefault("idColumn", "id"));
            return new JdbcConf(jdbcUrl, cfg.get("username"), cfg.get("password"), table, idColumn,
                    cfg.get("credentialRef"));
        }
    }
}
