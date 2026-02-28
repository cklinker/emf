package com.emf.runtime.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves JSONPath-based data flow between states.
 * <p>
 * Implements the three-phase data transformation used by the state machine:
 * <ol>
 *   <li><b>InputPath</b> — select a subset of the state as input to the current state</li>
 *   <li><b>ResultPath</b> — place the step's result into the state at a given path</li>
 *   <li><b>OutputPath</b> — select a subset of the state to pass to the next state</li>
 * </ol>
 * <p>
 * The default for all paths is {@code "$"} (the entire state object).
 * A path of {@code null} means "use the default" (i.e., "$").
 *
 * @since 1.0.0
 */
public class StateDataResolver {

    private static final String ROOT_PATH = "$";
    private final Configuration jsonPathConfig;
    private final ObjectMapper objectMapper;

    public StateDataResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider(objectMapper))
            .mappingProvider(new JacksonMappingProvider(objectMapper))
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
    }

    /**
     * Applies InputPath to select a subset of the state data as input for the current step.
     *
     * @param stateData the full state data
     * @param inputPath the JSONPath expression (null means "$" = entire state)
     * @return the selected subset of data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyInputPath(Map<String, Object> stateData, String inputPath) {
        if (stateData == null) {
            return Map.of();
        }
        String path = effectivePath(inputPath);
        if (ROOT_PATH.equals(path)) {
            return stateData;
        }

        Object result = JsonPath.using(jsonPathConfig).parse(stateData).read(path);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        // If the result is not a map, wrap it
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("result", result);
        return wrapped;
    }

    /**
     * Applies ResultPath to merge a step's result into the state data.
     *
     * @param stateData  the current state data
     * @param result     the result from the step execution
     * @param resultPath the JSONPath where the result should be placed (null means "$" = replace entire state)
     * @return the updated state data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyResultPath(Map<String, Object> stateData, Object result, String resultPath) {
        String path = effectivePath(resultPath);
        if (ROOT_PATH.equals(path)) {
            // Replace entire state with result
            if (result instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) result);
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("result", result);
            return wrapped;
        }

        // Place result at the specified path in a copy of state data
        Map<String, Object> updated = deepCopy(stateData);
        setNestedValue(updated, path, result);
        return updated;
    }

    /**
     * Applies OutputPath to select a subset of the state data to pass to the next state.
     *
     * @param stateData  the state data after ResultPath has been applied
     * @param outputPath the JSONPath expression (null means "$" = entire state)
     * @return the selected subset of data for the next state
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyOutputPath(Map<String, Object> stateData, String outputPath) {
        if (stateData == null) {
            return Map.of();
        }
        String path = effectivePath(outputPath);
        if (ROOT_PATH.equals(path)) {
            return stateData;
        }

        Object result = JsonPath.using(jsonPathConfig).parse(stateData).read(path);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("result", result);
        return wrapped;
    }

    /**
     * Reads a value from state data at the given JSONPath.
     *
     * @param stateData the state data
     * @param path      the JSONPath expression
     * @return the value at the path, or null if not found
     */
    public Object readPath(Map<String, Object> stateData, String path) {
        if (stateData == null || path == null) {
            return null;
        }
        return JsonPath.using(jsonPathConfig).parse(stateData).read(path);
    }

    /**
     * Resolves template expressions in a string by replacing {@code ${$.path}} patterns
     * with values from the state data.
     *
     * @param template  the template string containing ${$.path} expressions
     * @param stateData the state data to resolve paths against
     * @return the resolved string with all expressions replaced
     */
    public String resolveTemplate(String template, Map<String, Object> stateData) {
        if (template == null || stateData == null || !template.contains("${")) {
            return template;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            if (i + 1 < template.length() && template.charAt(i) == '$' && template.charAt(i + 1) == '{') {
                int end = template.indexOf('}', i + 2);
                if (end > 0) {
                    String path = template.substring(i + 2, end);
                    Object value = readPath(stateData, path);
                    result.append(value != null ? value.toString() : "");
                    i = end + 1;
                    continue;
                }
            }
            result.append(template.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * Recursively resolves {@code ${$.path}} template expressions in a nested structure
     * of Maps, Lists, and Strings. Non-string leaf values are left as-is.
     *
     * @param value     the value to resolve (Map, List, String, or other)
     * @param stateData the state data to resolve paths against
     * @return the resolved value with all template expressions replaced
     */
    @SuppressWarnings("unchecked")
    public Object resolveDeep(Object value, Map<String, Object> stateData) {
        if (value == null || stateData == null) {
            return value;
        }
        if (value instanceof String str) {
            if (!str.contains("${")) {
                return str;
            }
            // If the entire string is a single template expression, return the raw value
            // (preserving numeric/boolean types rather than converting to String)
            if (str.startsWith("${") && str.endsWith("}") && str.indexOf("}", 2) == str.length() - 1) {
                String path = str.substring(2, str.length() - 1);
                Object resolved = readPath(stateData, path);
                return resolved != null ? resolved : "";
            }
            return resolveTemplate(str, stateData);
        }
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolved.put(entry.getKey(), resolveDeep(entry.getValue(), stateData));
            }
            return resolved;
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> resolved = new ArrayList<>(list.size());
            for (Object item : list) {
                resolved.add(resolveDeep(item, stateData));
            }
            return resolved;
        }
        // Numbers, booleans, etc. — return as-is
        return value;
    }

    private String effectivePath(String path) {
        return (path == null || path.isBlank()) ? ROOT_PATH : path;
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> data, String jsonPath, Object value) {
        // Strip leading "$." to get the key path
        String keyPath = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
        String[] parts = keyPath.split("\\.");

        Map<String, Object> current = data;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.get(parts[i]);
            if (child instanceof Map) {
                current = (Map<String, Object>) child;
            } else {
                Map<String, Object> newMap = new LinkedHashMap<>();
                current.put(parts[i], newMap);
                current = newMap;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> original) {
        if (original == null) {
            return new LinkedHashMap<>();
        }
        // Use Jackson for a reliable deep copy
        return objectMapper.convertValue(original, new TypeReference<>() {});
    }
}
