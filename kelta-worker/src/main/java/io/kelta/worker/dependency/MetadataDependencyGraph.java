package io.kelta.worker.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A directed graph of metadata dependencies for a single tenant.
 *
 * <p>Edges run from a dependent node to the node it depends on (see {@link MetadataEdge}). The
 * graph answers two impact questions:
 * <ul>
 *   <li><b>dependents</b> — "what breaks if this changes?" — the transitive closure over
 *       <em>incoming</em> edges (everything that points at the node).</li>
 *   <li><b>dependencies</b> — "what does this rely on?" — the transitive closure over
 *       <em>outgoing</em> edges.</li>
 * </ul>
 * and detects cycles (e.g. circular master-detail chains) via strongly-connected components.
 *
 * <p>Not thread-safe; build once, then query.
 *
 * @since 1.0.0
 */
public class MetadataDependencyGraph {

    private final Map<String, MetadataNode> nodes = new HashMap<>();
    private final List<MetadataEdge> edges = new ArrayList<>();
    private final Map<String, List<MetadataEdge>> outgoing = new HashMap<>();
    private final Map<String, List<MetadataEdge>> incoming = new HashMap<>();

    /** Registers a node, filling in a name if a prior registration had none. */
    public MetadataNode addNode(MetadataNode node) {
        MetadataNode existing = nodes.get(node.key());
        if (existing == null) {
            nodes.put(node.key(), node);
            return node;
        }
        if (existing.name() == null && node.name() != null) {
            MetadataNode merged = new MetadataNode(node.type(), node.id(), node.name());
            nodes.put(node.key(), merged);
            return merged;
        }
        return existing;
    }

    /** Adds a dependency edge, registering both endpoints. Ignores self-edges to the same node. */
    public void addEdge(MetadataNode from, MetadataNode to, DependencyKind kind) {
        addNode(from);
        addNode(to);
        if (from.equals(to)) {
            return;
        }
        MetadataEdge edge = new MetadataEdge(from, to, kind);
        edges.add(edge);
        outgoing.computeIfAbsent(from.key(), k -> new ArrayList<>()).add(edge);
        incoming.computeIfAbsent(to.key(), k -> new ArrayList<>()).add(edge);
    }

    public List<MetadataNode> nodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<MetadataEdge> edges() {
        return new ArrayList<>(edges);
    }

    public boolean contains(MetadataNode node) {
        return nodes.containsKey(node.key());
    }

    public MetadataNode resolve(MetadataType type, String id) {
        return nodes.get(type.name() + ":" + id);
    }

    /** Direct edges pointing at {@code node} (its immediate dependents). */
    public List<MetadataEdge> directDependents(MetadataNode node) {
        return new ArrayList<>(incoming.getOrDefault(node.key(), List.of()));
    }

    /** Direct edges leaving {@code node} (its immediate dependencies). */
    public List<MetadataEdge> directDependencies(MetadataNode node) {
        return new ArrayList<>(outgoing.getOrDefault(node.key(), List.of()));
    }

    /** Everything that transitively depends on {@code node} — "what breaks if it changes". */
    public Set<MetadataNode> transitiveDependents(MetadataNode node) {
        return traverse(node, incoming, true);
    }

    /** Everything {@code node} transitively depends on. */
    public Set<MetadataNode> transitiveDependencies(MetadataNode node) {
        return traverse(node, outgoing, false);
    }

    private Set<MetadataNode> traverse(MetadataNode start,
                                       Map<String, List<MetadataEdge>> adjacency,
                                       boolean followToFrom) {
        Set<MetadataNode> visited = new LinkedHashSet<>();
        Deque<MetadataNode> queue = new ArrayDeque<>();
        queue.add(start);
        Set<String> seen = new HashSet<>();
        seen.add(start.key());
        while (!queue.isEmpty()) {
            MetadataNode current = queue.poll();
            for (MetadataEdge edge : adjacency.getOrDefault(current.key(), List.of())) {
                MetadataNode next = followToFrom ? edge.from() : edge.to();
                if (seen.add(next.key())) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /**
     * Returns strongly-connected components of size &gt; 1 — i.e. cycles such as circular
     * master-detail chains. A graph with no cycles returns an empty list.
     */
    public List<List<MetadataNode>> findCycles() {
        return new Tarjan().run();
    }

    public boolean hasCycle() {
        return !findCycles().isEmpty();
    }

    /** Iterative Tarjan's strongly-connected-components, returning only components of size &gt; 1. */
    private final class Tarjan {
        private int index = 0;
        private final Map<String, Integer> indexOf = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<MetadataNode> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<MetadataNode>> cycles = new ArrayList<>();

        List<List<MetadataNode>> run() {
            for (MetadataNode node : nodes.values()) {
                if (!indexOf.containsKey(node.key())) {
                    strongConnect(node);
                }
            }
            return cycles;
        }

        // Recursion depth is bounded by the metadata graph size (small per tenant).
        private void strongConnect(MetadataNode v) {
            indexOf.put(v.key(), index);
            lowLink.put(v.key(), index);
            index++;
            stack.push(v);
            onStack.add(v.key());

            for (MetadataEdge edge : outgoing.getOrDefault(v.key(), List.of())) {
                MetadataNode w = edge.to();
                if (!indexOf.containsKey(w.key())) {
                    strongConnect(w);
                    lowLink.put(v.key(), Math.min(lowLink.get(v.key()), lowLink.get(w.key())));
                } else if (onStack.contains(w.key())) {
                    lowLink.put(v.key(), Math.min(lowLink.get(v.key()), indexOf.get(w.key())));
                }
            }

            if (lowLink.get(v.key()).equals(indexOf.get(v.key()))) {
                List<MetadataNode> component = new ArrayList<>();
                MetadataNode w;
                do {
                    w = stack.pop();
                    onStack.remove(w.key());
                    component.add(w);
                } while (!w.equals(v));
                if (component.size() > 1) {
                    cycles.add(component);
                }
            }
        }
    }
}
