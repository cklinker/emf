package com.emf.runtime.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Manages PostgreSQL sequences for AUTO_NUMBER fields and generates formatted values.
 */
@Service
public class AutoNumberService {

    private final JdbcTemplate jdbcTemplate;

    public AutoNumberService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a PostgreSQL sequence for an auto-number field.
     */
    public void createSequence(String sequenceName, long startValue) {
        jdbcTemplate.execute(
            "CREATE SEQUENCE IF NOT EXISTS " + sanitize(sequenceName)
            + " START WITH " + startValue
            + " INCREMENT BY 1"
        );
    }

    /**
     * Drops a sequence (when field is deleted).
     */
    public void dropSequence(String sequenceName) {
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS " + sanitize(sequenceName));
    }

    /**
     * Generates the next formatted auto-number value.
     *
     * @param sequenceName the PostgreSQL sequence name
     * @param prefix       e.g., "TICKET-"
     * @param padding      number of zero-padded digits, e.g., 4 -> "0001"
     * @return formatted value, e.g., "TICKET-0001"
     */
    public String generateNext(String sequenceName, String prefix, int padding) {
        Long nextVal = jdbcTemplate.queryForObject(
            "SELECT nextval('" + sanitize(sequenceName) + "')", Long.class
        );
        String padded = String.format("%0" + padding + "d", nextVal);
        return prefix + padded;
    }

    private String sanitize(String name) {
        if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid sequence name: " + name);
        }
        return name;
    }
}
