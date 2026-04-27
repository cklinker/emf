package io.kelta.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny helpers for building {@link io.modelcontextprotocol.spec.McpSchema.JsonSchema}
 * input descriptors. The MCP SDK's {@code JsonSchema} record takes positional
 * args; these helpers keep tool authors away from null-laden constructors and
 * give consistent shapes.
 */
public final class Schemas {

    private Schemas() {}

    /** Empty no-args schema. */
    public static McpSchema.JsonSchema empty() {
        return new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
    }

    /** Object schema with the given properties and required field names. */
    public static McpSchema.JsonSchema object(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema("object", properties, required, false, null, null);
    }

    /** Convenience for a string property descriptor. */
    public static Map<String, Object> string(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        return m;
    }

    /** Convenience for an integer property descriptor with optional bounds. */
    public static Map<String, Object> integer(String description, Integer min, Integer max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", description);
        if (min != null) m.put("minimum", min);
        if (max != null) m.put("maximum", max);
        return m;
    }

    /** Convenience for a boolean property descriptor with default. */
    public static Map<String, Object> bool(String description, boolean defaultValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "boolean");
        m.put("description", description);
        m.put("default", defaultValue);
        return m;
    }

    /** Convenience for a free-form object property (e.g. filter / params). */
    public static Map<String, Object> freeObject(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("description", description);
        m.put("additionalProperties", true);
        return m;
    }
}
