package com.emf.runtime.storage;

import java.util.Objects;

/**
 * Represents a schema-qualified table reference for PostgreSQL.
 *
 * <p>Encapsulates the schema and table name, rendering the appropriate SQL identifier:
 * <ul>
 *   <li>For the {@code public} schema: returns the bare table name (e.g., {@code platform_user})</li>
 *   <li>For tenant schemas: returns a double-quoted qualified name (e.g., {@code "acme-corp"."tbl_orders"})</li>
 * </ul>
 *
 * <p>Double-quoting is used for tenant schemas because tenant slugs may contain hyphens,
 * which require quoting in PostgreSQL identifiers.
 *
 * @param schema the PostgreSQL schema name
 * @param tableName the table name (without schema prefix)
 * @since 1.0.0
 */
public record TableRef(String schema, String tableName) {

    private static final String PUBLIC_SCHEMA = "public";

    public TableRef {
        Objects.requireNonNull(schema, "schema cannot be null");
        Objects.requireNonNull(tableName, "tableName cannot be null");
        validateIdentifierPart(schema);
        validateIdentifierPart(tableName);
    }

    /**
     * Creates a table reference in the public schema.
     */
    public static TableRef publicSchema(String tableName) {
        return new TableRef(PUBLIC_SCHEMA, tableName);
    }

    /**
     * Creates a table reference in a tenant-specific schema.
     */
    public static TableRef tenantSchema(String tenantSlug, String tableName) {
        return new TableRef(tenantSlug, tableName);
    }

    /**
     * Returns true if this table is in the public schema.
     */
    public boolean isPublicSchema() {
        return PUBLIC_SCHEMA.equals(schema);
    }

    /**
     * Renders the SQL identifier for use in queries.
     *
     * <p>For the public schema, returns the bare table name to maintain backward
     * compatibility with existing SQL. For tenant schemas, returns a fully-qualified
     * double-quoted identifier.
     *
     * @return the SQL table identifier
     */
    public String toSql() {
        if (isPublicSchema()) {
            return tableName;
        }
        return "\"" + schema + "\".\"" + tableName + "\"";
    }

    /**
     * Validates that an identifier part contains only safe characters.
     * Allows lowercase letters, digits, underscores, and hyphens (for tenant slugs).
     *
     * @param part the identifier part to validate
     * @throws IllegalArgumentException if the part contains invalid characters
     */
    private static void validateIdentifierPart(String part) {
        if (part.isBlank()) {
            throw new IllegalArgumentException("Identifier part cannot be blank");
        }
        if (!part.matches("^[a-zA-Z_][a-zA-Z0-9_-]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + part);
        }
    }
}
