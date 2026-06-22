package io.kelta.worker.service.api;

import io.kelta.runtime.model.FieldType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Derives a flat Kelta collection shape from an OpenAPI operation's response
 * schema, so a GET-list operation can be materialized as an external-rest
 * virtual collection (Rec 4f).
 *
 * <p>Pure mapping logic (no I/O) — given the operation's {@code responseSchemas}
 * JSON (the standard OpenAPI Responses object, already dereferenced by the
 * parser), it picks the success response, finds the JSON item schema (unwrapping
 * an array or an array-valued wrapper property), and maps each scalar property to
 * a {@link FieldType}. It also infers the {@code dataPath} (where the row array
 * lives in the response) and the {@code idAttribute}.
 */
@Component
public class OpenApiCollectionMapper {

    private static final Logger log = LoggerFactory.getLogger(OpenApiCollectionMapper.class);

    /** Valid Kelta field-name pattern; properties that don't match are skipped. */
    private static final Pattern FIELD_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** One derived field. */
    public record DerivedField(String name, FieldType type) {}

    /**
     * @param fields            scalar fields derived from the item schema
     * @param dataPath          JSON path to the row array in the response ("" = root array)
     * @param idAttribute       the property to use as the record id ("id" when present)
     */
    public record DerivedSchema(List<DerivedField> fields, String dataPath, String idAttribute) {}

    /**
     * Maps an operation's response schema to a collection shape.
     *
     * @throws IllegalArgumentException if no usable JSON object schema can be found
     */
    public DerivedSchema map(JsonNode responseSchemas) {
        JsonNode success = pickSuccessResponse(responseSchemas);
        if (success == null) {
            throw new IllegalArgumentException(
                    "Operation has no 2xx response with a JSON schema to materialize");
        }
        JsonNode schema = success.path("content").path("application/json").path("schema");
        if (schema.isMissingNode() || schema.isNull()) {
            // Fall back to the first declared content type's schema.
            JsonNode content = success.path("content");
            if (content.isObject() && content.properties().iterator().hasNext()) {
                schema = content.properties().iterator().next().getValue().path("schema");
            }
        }
        if (schema.isMissingNode() || schema.isNull()) {
            throw new IllegalArgumentException("Success response declares no schema");
        }

        String dataPath = "";
        JsonNode itemSchema = schema;
        if ("array".equals(schema.path("type").asText(null))) {
            // Response is a bare array of items.
            itemSchema = schema.path("items");
        } else if (schema.path("properties").isObject()) {
            // Wrapper object — find the first array-valued property (e.g. {data:[...]}).
            String arrayProp = firstArrayProperty(schema.path("properties"));
            if (arrayProp != null) {
                dataPath = arrayProp;
                itemSchema = schema.path("properties").path(arrayProp).path("items");
            }
            // else: the object itself is the item (single-object endpoint).
        }

        JsonNode properties = itemSchema.path("properties");
        if (!properties.isObject() || !properties.properties().iterator().hasNext()) {
            throw new IllegalArgumentException(
                    "Could not derive item properties from the response schema");
        }

        List<DerivedField> fields = new ArrayList<>();
        String idAttribute = null;
        for (Map.Entry<String, JsonNode> entry : iterate(properties)) {
            String name = entry.getKey();
            if (!FIELD_NAME.matcher(name).matches()) {
                log.debug("Skipping property '{}' — not a valid field name", name);
                continue;
            }
            if ("id".equalsIgnoreCase(name) && idAttribute == null) {
                idAttribute = name;
            }
            fields.add(new DerivedField(name, mapType(entry.getValue())));
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("No mappable properties found in the item schema");
        }
        if (idAttribute == null) {
            idAttribute = fields.get(0).name();
        }
        return new DerivedSchema(fields, dataPath, idAttribute);
    }

    private JsonNode pickSuccessResponse(JsonNode responses) {
        if (responses == null || !responses.isObject()) {
            return null;
        }
        for (String code : List.of("200", "201", "2XX", "default")) {
            JsonNode r = responses.path(code);
            if (r.isObject()) {
                return r;
            }
        }
        // Any 2xx key.
        for (Map.Entry<String, JsonNode> e : iterate(responses)) {
            if (e.getKey().startsWith("2") && e.getValue().isObject()) {
                return e.getValue();
            }
        }
        return null;
    }

    private String firstArrayProperty(JsonNode properties) {
        for (Map.Entry<String, JsonNode> e : iterate(properties)) {
            if ("array".equals(e.getValue().path("type").asText(null))) {
                return e.getKey();
            }
        }
        return null;
    }

    private FieldType mapType(JsonNode propSchema) {
        String type = propSchema.path("type").asText("string");
        String format = propSchema.path("format").asText(null);
        return switch (type) {
            case "integer" -> "int64".equals(format) ? FieldType.LONG : FieldType.INTEGER;
            case "number" -> FieldType.DOUBLE;
            case "boolean" -> FieldType.BOOLEAN;
            case "array", "object" -> FieldType.JSON;
            default -> switch (format == null ? "" : format) {
                case "date" -> FieldType.DATE;
                case "date-time" -> FieldType.DATETIME;
                case "email" -> FieldType.EMAIL;
                case "uri", "url" -> FieldType.URL;
                default -> FieldType.STRING;
            };
        };
    }

    private Iterable<Map.Entry<String, JsonNode>> iterate(JsonNode objectNode) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        objectNode.properties().forEach(p -> map.put(p.getKey(), p.getValue()));
        return map.entrySet();
    }
}
