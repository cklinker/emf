package com.emf.runtime.validation;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Coerces incoming data values to the Java types expected by the validation engine.
 *
 * <p>When JSON is deserialized by Jackson (e.g., from an HTTP request body),
 * numeric values that arrive as strings (e.g., {@code "10"} instead of {@code 10})
 * will be rejected by the validation engine's type checks. This service converts
 * such string values to the appropriate Java type (Integer, Long, Double, Boolean,
 * Instant, LocalDate) based on the field definition, allowing validation to proceed
 * correctly.
 *
 * <p>Coercion rules:
 * <ul>
 *   <li>STRING values for numeric fields → parsed to Integer/Long/Double</li>
 *   <li>STRING values for boolean fields → parsed to Boolean ("true"/"false")</li>
 *   <li>STRING values for DATETIME fields → parsed to Instant (ISO-8601 formats)</li>
 *   <li>STRING values for DATE fields → parsed to LocalDate (ISO-8601 "yyyy-MM-dd")</li>
 *   <li>Integer values for Long/Double fields → widened to Long/Double</li>
 *   <li>Values that cannot be coerced are left unchanged (validation will catch them)</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe.
 *
 * @since 1.0.0
 */
public final class TypeCoercionService {

    private static final Logger logger = LoggerFactory.getLogger(TypeCoercionService.class);

    private TypeCoercionService() {
        // Utility class — no instantiation
    }

    /**
     * Coerces values in the data map to match the expected field types in the collection definition.
     *
     * <p>The data map is mutated in place. Only values that are present in the map and whose
     * type does not match the expected field type are coerced.
     *
     * @param definition the collection definition containing field type information
     * @param data       the mutable data map to coerce (modified in place)
     * @throws NullPointerException if definition or data is null
     */
    public static void coerce(CollectionDefinition definition, Map<String, Object> data) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        for (FieldDefinition field : definition.fields()) {
            String fieldName = field.name();
            if (!data.containsKey(fieldName)) {
                continue;
            }

            Object value = data.get(fieldName);
            if (value == null) {
                continue;
            }

            Object coerced = coerceValue(value, field.type());
            if (coerced != value) {
                data.put(fieldName, coerced);
                logger.trace("Coerced field '{}' from {} to {} (type={})",
                        fieldName, value.getClass().getSimpleName(),
                        coerced.getClass().getSimpleName(), field.type());
            }
        }
    }

    /**
     * Coerces a single value to match the expected field type.
     *
     * @param value        the value to coerce
     * @param expectedType the expected field type
     * @return the coerced value, or the original value if coercion is not needed or not possible
     */
    public static Object coerceValue(Object value, FieldType expectedType) {
        return switch (expectedType) {
            case INTEGER -> coerceToInteger(value);
            case LONG -> coerceToLong(value);
            case DOUBLE, CURRENCY, PERCENT -> coerceToDouble(value);
            case BOOLEAN -> coerceToBoolean(value);
            case DATE -> coerceToLocalDate(value);
            case DATETIME -> coerceToInstant(value);
            default -> value;
        };
    }

    /**
     * Attempts to coerce a value to Integer.
     */
    private static Object coerceToInteger(Object value) {
        if (value instanceof Integer) {
            return value;
        }
        if (value instanceof Number num) {
            double d = num.doubleValue();
            if (d == Math.floor(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return num.intValue();
            }
            return value; // Not an integer-compatible number
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException e) {
                // Try parsing as double first (e.g., "10.0")
                try {
                    double d = Double.parseDouble(str.trim());
                    if (d == Math.floor(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                        return (int) d;
                    }
                } catch (NumberFormatException e2) {
                    // Cannot coerce
                }
                return value;
            }
        }
        return value;
    }

    /**
     * Attempts to coerce a value to Long.
     */
    private static Object coerceToLong(Object value) {
        if (value instanceof Long) {
            return value;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Number num) {
            double d = num.doubleValue();
            if (d == Math.floor(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                return num.longValue();
            }
            return value;
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                try {
                    double d = Double.parseDouble(str.trim());
                    if (d == Math.floor(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                        return (long) d;
                    }
                } catch (NumberFormatException e2) {
                    // Cannot coerce
                }
                return value;
            }
        }
        return value;
    }

    /**
     * Attempts to coerce a value to Double.
     */
    private static Object coerceToDouble(Object value) {
        if (value instanceof Double) {
            return value;
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return value;
            }
        }
        return value;
    }

    /**
     * Attempts to coerce a value to Boolean.
     */
    private static Object coerceToBoolean(Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String str) {
            String lower = str.trim().toLowerCase();
            if ("true".equals(lower)) {
                return Boolean.TRUE;
            }
            if ("false".equals(lower)) {
                return Boolean.FALSE;
            }
        }
        return value;
    }

    /**
     * Attempts to coerce a value to {@link Instant} for DATETIME fields.
     *
     * <p>Supports multiple ISO-8601 formats:
     * <ul>
     *   <li>{@code "2025-06-01T00:00:00Z"} — full instant with UTC offset</li>
     *   <li>{@code "2025-06-01T00:00:00"} — local datetime, assumed UTC</li>
     *   <li>{@code "2025-06-01T00:00"} — local datetime without seconds, assumed UTC</li>
     *   <li>{@code "2025-06-01"} — date only, interpreted as start of day UTC</li>
     * </ul>
     */
    private static Object coerceToInstant(Object value) {
        if (value instanceof Instant) {
            return value;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return value;
            }
            // Try full Instant format first (with Z or offset)
            try {
                return Instant.parse(trimmed);
            } catch (DateTimeParseException e) {
                // Fall through to LocalDateTime parsing
            }
            // Try LocalDateTime format (no offset — assume UTC)
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed);
                return ldt.toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                // Fall through to date-only parsing
            }
            // Try date-only format (start of day UTC)
            try {
                LocalDate ld = LocalDate.parse(trimmed);
                return ld.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                // Cannot coerce — return original for validation to catch
            }
        }
        return value;
    }

    /**
     * Attempts to coerce a value to {@link LocalDate} for DATE fields.
     *
     * <p>Supports ISO-8601 date format: {@code "2025-06-01"}.
     */
    private static Object coerceToLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return value;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return value;
            }
            try {
                return LocalDate.parse(trimmed);
            } catch (DateTimeParseException e) {
                // Cannot coerce — return original for validation to catch
            }
        }
        return value;
    }
}
