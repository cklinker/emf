package com.emf.runtime.service;

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
     * @param childTableName    the child collection's table name
     * @param foreignKeyField   the FK field on the child table pointing to parent
     * @param parentRecordId    the parent record's ID
     * @param aggregateFunction COUNT, SUM, MIN, MAX, or AVG
     * @param aggregateField    the field to aggregate (null for COUNT)
     * @param filter            optional filter criteria for child records
     * @return the computed value
     */
    public Object compute(String childTableName, String foreignKeyField,
                          String parentRecordId, String aggregateFunction,
                          String aggregateField, Map<String, Object> filter) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(switch (aggregateFunction) {
            case "COUNT" -> "COUNT(*)";
            case "SUM" -> "SUM(" + sanitize(aggregateField) + ")";
            case "MIN" -> "MIN(" + sanitize(aggregateField) + ")";
            case "MAX" -> "MAX(" + sanitize(aggregateField) + ")";
            case "AVG" -> "AVG(" + sanitize(aggregateField) + ")";
            default -> throw new IllegalArgumentException("Unknown aggregate function: " + aggregateFunction);
        });
        sql.append(" FROM ").append(sanitize(childTableName));
        sql.append(" WHERE ").append(sanitize(foreignKeyField)).append(" = ?");

        List<Object> params = new ArrayList<>();
        params.add(parentRecordId);

        if (filter != null && !filter.isEmpty()) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                sql.append(" AND ").append(sanitize(entry.getKey())).append(" = ?");
                params.add(entry.getValue());
            }
        }

        return jdbcTemplate.queryForObject(sql.toString(), Object.class, params.toArray());
    }

    private String sanitize(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        return identifier;
    }
}
