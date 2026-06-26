package io.kelta.worker.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /**
     * Orders {@code changeSet} for safe deployment: a node's dependencies (the targets of its
     * outgoing edges) appear before it, so applying the list in order never references a
     * not-yet-deployed item. Only nodes present in the graph are considered, and only edges
     * <em>between</em> change-set members constrain the order (Kahn's algorithm). Ties break by
     * (type, id) for a stable, reproducible plan. Nodes trapped in a cycle within the change set
     * cannot be ordered and are omitted — callers detect them via {@code changeSet \ result} (and
     * {@link #findCycles()} explains why).
     */
    public List<MetadataNode> topologicalOrder(Collection<MetadataNode> changeSet) {
        Map<String, MetadataNode> scope = new LinkedHashMap<>();
        for (MetadataNode n : changeSet) {
            MetadataNode resolved = nodes.get(n.key());
            if (resolved != null) {
                scope.put(resolved.key(), resolved);
            }
        }

        // depCount[X] = number of X's dependency edges whose target is also in scope.
        Map<String, Integer> depCount = new HashMap<>();
        for (MetadataNode n : scope.values()) {
            int count = 0;
            for (MetadataEdge edge : outgoing.getOrDefault(n.key(), List.of())) {
                if (scope.containsKey(edge.to().key())) {
                    count++;
                }
            }
            depCount.put(n.key(), count);
        }

        List<MetadataNode> ready = new ArrayList<>();
        for (MetadataNode n : scope.values()) {
            if (depCount.get(n.key()) == 0) {
                ready.add(n);
            }
        }
        ready.sort(NODE_ORDER);

        List<MetadataNode> order = new ArrayList<>(scope.size());
        Set<String> placed = new HashSet<>();
        while (!ready.isEmpty()) {
            MetadataNode current = ready.remove(0);
            if (!placed.add(current.key())) {
                continue;
            }
            order.add(current);
            List<MetadataNode> unlocked = new ArrayList<>();
            for (MetadataEdge edge : incoming.getOrDefault(current.key(), List.of())) {
                MetadataNode dependent = edge.from();
                if (!scope.containsKey(dependent.key()) || placed.contains(dependent.key())) {
                    continue;
                }
                int remaining = depCount.get(dependent.key()) - 1;
                depCount.put(dependent.key(), remaining);
                if (remaining == 0) {
                    unlocked.add(dependent);
                }
            }
            unlocked.sort(NODE_ORDER);
            ready.addAll(unlocked);
        }
        return order;
    }

    private static final Comparator<MetadataNode> NODE_ORDER =
            Comparator.comparing((MetadataNode n) -> n.type().name()).thenComparing(MetadataNode::id);

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
