package io.kelta.runtime.module.integration.mapping;

import com.dashjoin.jsonata.Jsonata;
import io.kelta.runtime.flow.StateDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a payload template against the current flow state. Used by
 * action handlers (PR 4's {@code CALL_API}, PR 5's email integration) to
 * build request bodies, headers, and other structured payloads from the
 * combination of step config and state data.
 *
 * <p><b>Resolution rules</b> (applied recursively to objects, arrays, and scalars):
 * <ol>
 *   <li>Strings starting with {@code =} are evaluated as JSONata against the
 *       state data and the result replaces the string. Useful for
 *       conditionals, list mapping, defaults, and formatters.</li>
 *   <li>Objects containing exactly one key {@code "$expr"} are treated the
 *       same — the expression's result replaces the whole object.</li>
 *   <li>All other strings flow through {@link StateDataResolver#resolveTemplate}
 *       so existing {@code ${$.path}} placeholders keep working.</li>
 *   <li>Object values and array elements recurse.</li>
 * </ol>
 *
 * <p>JSONata expressions are pre-compiled per call (the dashjoin runtime is
 * already cached internally). Compilation errors surface as
 * {@link PayloadMapperException} so handlers can fail flows cleanly.
 */
public class PayloadMapperService {

    private static final Logger log = LoggerFactory.getLogger(PayloadMapperService.class);
    private static final String EXPR_KEY = "$expr";

    private final StateDataResolver dataResolver;

    public PayloadMapperService(StateDataResolver dataResolver) {
        this.dataResolver = dataResolver;
    }

    /**
     * Walks {@code template} recursively and produces the resolved payload.
     * Pass {@code stateData} as the current flow state map.
     */
    public Object map(Object template, Map<String, Object> stateData) {
        if (template == null) {
            return null;
        }
        if (template instanceof String s) {
            return mapString(s, stateData);
        }
        if (template instanceof Map<?, ?> m) {
            return mapMap(m, stateData);
        }
        if (template instanceof List<?> l) {
            return mapList(l, stateData);
        }
        return template;
    }

    /**
     * Convenience for handlers that need an {@code Object → Map<String,Object>}
     * mapping. When {@link #map} returns a non-Map value (e.g., a JSONata
     * expression that produced a scalar), throws {@link PayloadMapperException}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> mapToObject(Object template, Map<String, Object> stateData) {
        Object resolved = map(template, stateData);
        if (resolved == null) {
            return new LinkedHashMap<>();
        }
        if (resolved instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new PayloadMapperException(
            "Mapping produced a " + resolved.getClass().getSimpleName()
                + " but an object was expected");
    }

    // -----------------------------------------------------------------------

    private Object mapString(String s, Map<String, Object> stateData) {
        if (s.startsWith("=")) {
            return evalJsonata(s.substring(1), stateData);
        }
        return dataResolver.resolveTemplate(s, stateData);
    }

    @SuppressWarnings("unchecked")
    private Object mapMap(Map<?, ?> map, Map<String, Object> stateData) {
        // {"$expr": "..."} replaces the whole object
        if (map.size() == 1 && map.containsKey(EXPR_KEY)) {
            Object expr = map.get(EXPR_KEY);
            if (expr instanceof String s) {
                return evalJsonata(s, stateData);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            out.put(key, map(entry.getValue(), stateData));
        }
        return out;
    }

    private Object mapList(List<?> list, Map<String, Object> stateData) {
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(map(item, stateData));
        }
        return out;
    }

    private Object evalJsonata(String expression, Map<String, Object> stateData) {
        try {
            Jsonata expr = Jsonata.jsonata(expression);
            return expr.evaluate(stateData);
        } catch (Exception e) {
            log.debug("JSONata expression failed: '{}': {}", expression, e.getMessage());
            throw new PayloadMapperException(
                "JSONata expression failed: " + e.getMessage(), e);
        }
    }
}
