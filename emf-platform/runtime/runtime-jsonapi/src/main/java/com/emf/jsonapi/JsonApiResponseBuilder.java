package com.emf.jsonapi;

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
        Map<String, Object> errorObj = new LinkedHashMap<>();
        errorObj.put("status", status);
        errorObj.put("title", title);
        errorObj.put("detail", detail);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("errors", List.of(errorObj));
        return document;
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
