package io.kelta.worker.util;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Shared CSV formatting helpers used by {@code DataExportService} and {@code ReportExecutionService}.
 */
public final class CsvFormatUtils {

    private CsvFormatUtils() {}

    /**
     * Converts a record field value to its CSV-safe string representation.
     * Maps and Lists are serialized as JSON; all other values use {@code toString()}.
     */
    public static String formatValue(Object value, ObjectMapper objectMapper) {
        if (value == null) return "";
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return value.toString();
    }

    /**
     * Escapes a CSV field value per RFC 4180.
     * Wraps the value in double-quotes and escapes internal double-quotes if the value
     * contains commas, quotes, or newlines.
     */
    public static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
