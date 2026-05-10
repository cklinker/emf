package io.kelta.runtime.service;

import io.kelta.runtime.storage.TableRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes ROLLUP_SUMMARY field values by executing aggregate queries
 * against child collection tables.
 */
@Service
public class RollupSummaryService {

    private final JdbcTemplate jdbcTemplate;

    public RollupSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Computes rollup summary values for a parent record.
     *
     * <p>Schema-per-tenant aware: the caller passes a {@link TableRef} that
     * resolves to the child collection's tenant-qualified table (or the
     * public schema for system collections).
     *
     * @param childTable        schema-qualified reference to the child table
     * @param foreignKeyColumn  FK column on the child table pointing at the parent
     * @param parentRecordId    the parent record's ID
     * @param aggregateFunction COUNT, SUM, MIN, MAX, or AVG
     * @param aggregateColumn   the column to aggregate (null for COUNT)
     * @param filter            optional equality filter applied to child rows
     * @return the computed value, or null when JDBC returns null
     */
    public Object compute(TableRef childTable, String foreignKeyColumn,
                          String parentRecordId, String aggregateFunction,
                          String aggregateColumn, Map<String, Object> filter) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(switch (aggregateFunction) {
            case "COUNT" -> "COUNT(*)";
            case "SUM" -> "SUM(" + sanitizeColumn(aggregateColumn) + ")";
            case "MIN" -> "MIN(" + sanitizeColumn(aggregateColumn) + ")";
            case "MAX" -> "MAX(" + sanitizeColumn(aggregateColumn) + ")";
            case "AVG" -> "AVG(" + sanitizeColumn(aggregateColumn) + ")";
            default -> throw new IllegalArgumentException("Unknown aggregate function: " + aggregateFunction);
        });
        sql.append(" FROM ").append(childTable.toSql());
        sql.append(" WHERE ").append(sanitizeColumn(foreignKeyColumn)).append(" = ?");

        List<Object> params = new ArrayList<>();
        params.add(parentRecordId);

        if (filter != null && !filter.isEmpty()) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                sql.append(" AND ").append(sanitizeColumn(entry.getKey())).append(" = ?");
                params.add(entry.getValue());
            }
        }

        return jdbcTemplate.queryForObject(sql.toString(), Object.class, params.toArray());
    }

    private String sanitizeColumn(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        return identifier;
    }
}
