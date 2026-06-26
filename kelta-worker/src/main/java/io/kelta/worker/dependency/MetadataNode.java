package io.kelta.worker.dependency;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A node in the metadata dependency graph: one configuration object, identified by its
 * {@link MetadataType} and id. Equality is by (type, id) so {@code name} is purely descriptive.
 *
 * @param type the kind of metadata
 * @param id   the metadata object's id (stable identifier)
 * @param name a human-friendly label (display name), may be null
 * @since 1.0.0
 */
public record MetadataNode(MetadataType type, String id, String name) {

    public MetadataNode {
        if (type == null || id == null) {
            throw new IllegalArgumentException("MetadataNode requires non-null type and id");
        }
    }

    public static MetadataNode of(MetadataType type, String id, String name) {
        return new MetadataNode(type, id, name);
    }

    /** A stable composite key for graph adjacency maps and de-duplication. */
    public String key() {
        return type.name() + ":" + id;
    }

    /** JSON:API-friendly map ({@code type}, {@code id}, {@code name}) for responses. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        map.put("id", id);
        map.put("name", name);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof MetadataNode other
                && type == other.type
                && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + id.hashCode();
    }
}
