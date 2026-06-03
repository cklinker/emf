package io.kelta.runtime.storage;

import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;

/**
 * SQL type mapping for {@link FieldType}.
 *
 * <p>Most types are a pure function of the enum, but a few (currently {@link FieldType#VECTOR})
 * need parameters from the field definition (e.g. dimension).
 */
final class FieldTypeSql {

    /** Default pgvector dimension when {@code fieldTypeConfig.dimension} is not provided. */
    static final int DEFAULT_VECTOR_DIMENSION = 1536;

    /**
     * pgvector imposes a 16000-dimension upper bound on indexable vectors and most
     * practical embeddings sit well below this. Reject obviously invalid inputs early.
     */
    static final int MAX_VECTOR_DIMENSION = 16000;

    private FieldTypeSql() {}

    /**
     * Returns the PostgreSQL column type for a field whose SQL type is fully determined
     * by its enum. For parameterized types (e.g. VECTOR) prefer {@link #mapFieldTypeToSql(FieldDefinition)}.
     */
    static String mapFieldTypeToSql(FieldType type) {
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
            case TEXT, RICH_TEXT -> "TEXT";
            case VECTOR -> "vector(" + DEFAULT_VECTOR_DIMENSION + ")";
            case ENCRYPTED -> "BYTEA";
            case EXTERNAL_ID -> "VARCHAR(255)";
            case GEOLOCATION -> "DOUBLE PRECISION";
            case LOOKUP -> "VARCHAR(36)";
            case MASTER_DETAIL -> "VARCHAR(36)";
            case FORMULA, ROLLUP_SUMMARY -> null;
        };
    }

    /**
     * Returns the PostgreSQL column type for the given field, honouring per-field
     * parameters (e.g. VECTOR dimension).
     */
    static String mapFieldTypeToSql(FieldDefinition field) {
        if (field.type() == FieldType.VECTOR) {
            return "vector(" + vectorDimension(field) + ")";
        }
        return mapFieldTypeToSql(field.type());
    }

    /**
     * Extracts and validates the {@code dimension} parameter for a VECTOR field.
     * Falls back to {@link #DEFAULT_VECTOR_DIMENSION} when unset.
     */
    static int vectorDimension(FieldDefinition field) {
        Object raw = field.getConfigValue("dimension");
        if (raw == null) {
            return DEFAULT_VECTOR_DIMENSION;
        }
        int dim;
        if (raw instanceof Number n) {
            dim = n.intValue();
        } else {
            try {
                dim = Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "VECTOR field '" + field.name() + "' has non-integer dimension: " + raw);
            }
        }
        if (dim < 1 || dim > MAX_VECTOR_DIMENSION) {
            throw new IllegalArgumentException(
                    "VECTOR field '" + field.name() + "' dimension " + dim
                            + " is out of range (1.." + MAX_VECTOR_DIMENSION + ")");
        }
        return dim;
    }
}
