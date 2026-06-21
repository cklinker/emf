package io.kelta.worker.dependency;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A directed edge in the metadata dependency graph, running from a dependent node to the node it
 * depends on. "{@code from} {@code kind} {@code to}" reads as, e.g., "field LOOKUP collection".
 *
 * @param from the dependent node
 * @param to   the node depended upon
 * @param kind why the dependency exists
 * @since 1.0.0
 */
public record MetadataEdge(MetadataNode from, MetadataNode to, DependencyKind kind) {

    public MetadataEdge {
        if (from == null || to == null || kind == null) {
            throw new IllegalArgumentException("MetadataEdge requires non-null from, to, and kind");
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("from", from.toMap());
        map.put("to", to.toMap());
        map.put("kind", kind.name());
        return map;
    }
}
