package io.kelta.jsonapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for building JSON:API-compliant response envelopes.
 *
 * <p>Wraps plain data maps into the standard JSON:API structure with
 * {@code data}, {@code meta}, and {@code errors} top-level members.
 *
 * @since 1.0.0
 */
public final class JsonApiResponseBuilder {

    private JsonApiResponseBuilder() {}

    /**
     * Wraps a single record as a JSON:API single-resource response.
     *
     * @param type       the JSON:API resource type (e.g. "governor-limits")
     * @param id         the resource ID
     * @param attributes the resource attributes
     * @return a JSON:API document map with {@code data} as a single resource object
     */
    public static Map<String, Object> single(String type, String id, Map<String, Object> attributes) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", type);
        resource.put("id", id);
        resource.put("attributes", attributes);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("data", resource);
        return document;
    }

    /**
     * Wraps a single record as a JSON:API single-resource response with meta.
     *
     * @param type       the JSON:API resource type
     * @param id         the resource ID
     * @param attributes the resource attributes
     * @param meta       top-level meta object
     * @return a JSON:API document map
     */
    public static Map<String, Object> single(String type, String id,
                                              Map<String, Object> attributes,
                                              Map<String, Object> meta) {
        Map<String, Object> document = single(type, id, attributes);
        if (meta != null && !meta.isEmpty()) {
            document.put("meta", meta);
        }
        return document;
    }

    /**
     * Wraps a list of records as a JSON:API collection response.
     *
     * <p>Each record map must contain an {@code id} key. All other keys
     * become attributes.
     *
     * @param type    the JSON:API resource type
     * @param records the list of record maps
     * @return a JSON:API document map with {@code data} as an array
     */
    public static Map<String, Object> collection(String type, List<Map<String, Object>> records) {
        List<Map<String, Object>> resources = records.stream()
                .map(record -> toResource(type, record))
                .toList();

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("data", resources);
        return document;
    }

    /**
     * Wraps a list of records as a JSON:API collection response with meta.
     *
     * @param type    the JSON:API resource type
     * @param records the list of record maps
     * @param meta    top-level meta object (e.g. pagination info)
     * @return a JSON:API document map
     */
    public static Map<String, Object> collection(String type,
                                                   List<Map<String, Object>> records,
                                                   Map<String, Object> meta) {
        Map<String, Object> document = collection(type, records);
        if (meta != null && !meta.isEmpty()) {
            document.put("meta", meta);
        }
        return document;
    }

    /**
     * Builds a JSON:API error response.
     *
     * @param status HTTP status code (e.g. "400", "500")
     * @param title  short error title
     * @param detail detailed error message
     * @return a JSON:API document map with {@code errors} array
     */
    public static Map<String, Object> error(String status, String title, String detail) {
        return error(status, deriveCode(title, status), title, detail, null);
    }

    /**
     * Builds a JSON:API error response with a machine-readable {@code code}.
     *
     * @param status HTTP status code (e.g. "400", "500")
     * @param code   machine-readable error code (UPPER_SNAKE_CASE, e.g. {@code NOT_FOUND})
     * @param title  short error title
     * @param detail detailed error message
     * @return a JSON:API document map with {@code errors} array
     */
    public static Map<String, Object> error(String status, String code, String title, String detail) {
        return error(status, code, title, detail, null);
    }

    /**
     * Builds a JSON:API error response with a field-level {@code source.pointer}.
     *
     * @param status  HTTP status code (e.g. "400", "500")
     * @param code    machine-readable error code (UPPER_SNAKE_CASE)
     * @param title   short error title
     * @param detail  detailed error message
     * @param pointer JSON Pointer to the offending field (e.g. {@code /data/attributes/email}),
     *                or {@code null} for a non-field-level error
     * @return a JSON:API document map with {@code errors} array
     */
    public static Map<String, Object> error(String status, String code, String title,
                                            String detail, String pointer) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("errors", List.of(errorObject(status, code, title, detail, pointer)));
        return document;
    }

    /**
     * Builds a single JSON:API error object. Useful when callers need to assemble
     * multiple errors into one response.
     *
     * <p>All four of {@code status}, {@code code}, {@code title}, {@code detail}
     * are required by platform convention so clients can always render an
     * actionable message. {@code pointer} is optional.
     *
     * @param status  HTTP status code as a string
     * @param code    machine-readable error code (UPPER_SNAKE_CASE)
     * @param title   short, stable error title
     * @param detail  detailed error message
     * @param pointer optional JSON Pointer to the offending field
     * @return a single error object map
     */
    public static Map<String, Object> errorObject(String status, String code, String title,
                                                   String detail, String pointer) {
        Map<String, Object> errorObj = new LinkedHashMap<>();
        errorObj.put("status", status);
        errorObj.put("code", code);
        errorObj.put("title", title);
        errorObj.put("detail", detail);
        if (pointer != null && !pointer.isBlank()) {
            errorObj.put("source", Map.of("pointer", pointer));
        }
        return errorObj;
    }

    /**
     * Wraps a list of pre-built error objects into a JSON:API errors document.
     *
     * @param errors one or more error object maps (see {@link #errorObject})
     * @return a JSON:API document map with {@code errors} array
     */
    public static Map<String, Object> errors(List<Map<String, Object>> errors) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("errors", List.copyOf(errors));
        return document;
    }

    /**
     * Derives a machine-readable code from a title string. Falls back to the
     * status code when the title is null/blank.
     *
     * <p>Used by the legacy 3-arg {@link #error(String, String, String)} overload
     * to keep older callers compliant without forcing every caller to supply a
     * code immediately.
     */
    private static String deriveCode(String title, String status) {
        String source = (title != null && !title.isBlank()) ? title : status;
        if (source == null) {
            return "ERROR";
        }
        String upper = source.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        if (upper.startsWith("_")) upper = upper.substring(1);
        if (upper.endsWith("_")) upper = upper.substring(0, upper.length() - 1);
        return upper.isEmpty() ? "ERROR" : upper;
    }

    /**
     * Converts a flat record map (with "id" key) into a JSON:API resource object map.
     */
    private static Map<String, Object> toResource(String type, Map<String, Object> record) {
        Map<String, Object> attributes = new LinkedHashMap<>(record);
        Object id = attributes.remove("id");

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", type);
        resource.put("id", id != null ? id.toString() : null);
        resource.put("attributes", attributes);
        return resource;
    }
}
