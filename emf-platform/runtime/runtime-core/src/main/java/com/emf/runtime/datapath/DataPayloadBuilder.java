package com.emf.runtime.datapath;

import com.emf.runtime.model.CollectionDefinition;

import java.util.*;

/**
 * Builds a complete data payload for workflow action execution from a list
 * of named DataPaths.
 *
 * <p>Given a list of field definitions (each with a name and a DataPath),
 * this builder resolves all paths against a source record and assembles
 * a named map of resolved values. For example:
 * <pre>
 *   Input:  [{name: "customerEmail", path: "order_id.customer_id.email"},
 *            {name: "orderTotal",    path: "order_id.total"}]
 *   Output: {"customerEmail": "john@example.com", "orderTotal": 150.00}
 * </pre>
 *
 * <p>Internally uses {@link DataPathResolver#resolveAll} for shared prefix
 * optimization when multiple paths share common traversal hops.
 *
 * @since 1.0.0
 */
public class DataPayloadBuilder {

    private final DataPathResolver resolver;

    /**
     * Creates a new DataPayloadBuilder.
     *
     * @param resolver the data path resolver for resolving expressions
     */
    public DataPayloadBuilder(DataPathResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
    }

    /**
     * Builds a named payload map from a list of data payload field definitions.
     *
     * @param fields           the named field definitions with DataPath expressions
     * @param sourceRecord     the starting record data
     * @param sourceCollection the starting collection definition
     * @return map of field name → resolved value
     */
    public Map<String, Object> buildPayload(List<DataPayloadField> fields,
                                             Map<String, Object> sourceRecord,
                                             CollectionDefinition sourceCollection) {
        Objects.requireNonNull(fields, "fields cannot be null");
        Objects.requireNonNull(sourceRecord, "sourceRecord cannot be null");
        Objects.requireNonNull(sourceCollection, "sourceCollection cannot be null");

        if (fields.isEmpty()) {
            return Collections.emptyMap();
        }

        // Parse all paths
        List<DataPath> paths = new ArrayList<>(fields.size());
        Map<String, String> expressionToName = new LinkedHashMap<>();

        for (DataPayloadField field : fields) {
            DataPath path = DataPath.parse(field.path(), sourceCollection.name());
            paths.add(path);
            expressionToName.put(path.expression(), field.name());
        }

        // Resolve all paths with shared prefix optimization
        Map<String, Object> resolvedByExpression = resolver.resolveAll(
            paths, sourceRecord, sourceCollection);

        // Map expressions back to named output keys
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : expressionToName.entrySet()) {
            String expression = entry.getKey();
            String name = entry.getValue();
            payload.put(name, resolvedByExpression.get(expression));
        }

        return payload;
    }

    /**
     * A named field definition for building data payloads.
     *
     * @param name the output key name (e.g., "customerEmail")
     * @param path the DataPath expression (e.g., "order_id.customer_id.email")
     */
    public record DataPayloadField(
        String name,
        String path
    ) {
        public DataPayloadField {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(path, "path cannot be null");
        }
    }
}
