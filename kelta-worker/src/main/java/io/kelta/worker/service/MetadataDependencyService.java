package io.kelta.worker.service;

import io.kelta.worker.dependency.DependencyKind;
import io.kelta.worker.dependency.MetadataDependencyGraph;
import io.kelta.worker.dependency.MetadataEdge;
import io.kelta.worker.dependency.MetadataNode;
import io.kelta.worker.dependency.MetadataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds a tenant's metadata dependency graph on demand and answers impact-analysis queries
 * ("what breaks if X changes?" / "what does X depend on?").
 *
 * <p>The graph is computed from the live configuration tables (collections, fields, flows,
 * layouts, validation rules, record types, list views, unique constraints, profile permissions),
 * not persisted — so it is always current. Each extractor is best-effort: a failure to read one
 * metadata type is logged and skipped so a single schema variance never fails the whole graph.
 *
 * @since 1.0.0
 */
@Service
public class MetadataDependencyService {

    private static final Logger log = LoggerFactory.getLogger(MetadataDependencyService.class);

    /** Forward = a node's dependencies; reverse = its dependents (impact). */
    public enum Direction { DEPENDENTS, DEPENDENCIES }

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MetadataDependencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the full dependency graph for a tenant.
     */
    public MetadataDependencyGraph buildGraph(String tenantId) {
        MetadataDependencyGraph graph = new MetadataDependencyGraph();

        Set<String> collectionIds = new LinkedHashSet<>();
        Set<String> flowIds = new LinkedHashSet<>();

        extract("collections", () -> extractCollections(tenantId, graph, collectionIds));
        extract("fields", () -> extractFields(tenantId, graph));
        extract("flows", () -> extractFlows(tenantId, graph, flowIds));
        // Flow references depend on collection/flow node sets being loaded first.
        extract("flow-references", () -> extractFlowReferences(tenantId, graph, collectionIds, flowIds));
        extract("layouts", () -> extractLayouts(tenantId, graph));
        extract("layout-fields", () -> extractLayoutFields(tenantId, graph));
        extract("layout-related-lists", () -> extractLayoutRelatedLists(tenantId, graph));
        extract("validation-rules", () -> extractValidationRules(tenantId, graph));
        extract("record-types", () -> extractRecordTypes(tenantId, graph));
        extract("list-views", () -> extractListViews(tenantId, graph));
        extract("unique-constraints", () -> extractUniqueConstraints(tenantId, graph));
        extract("object-permissions", () -> extractObjectPermissions(tenantId, graph));
        extract("field-permissions", () -> extractFieldPermissions(tenantId, graph));

        return graph;
    }

    /**
     * Returns an impact-analysis result for one metadata node, in the requested direction.
     */
    public Map<String, Object> impact(String tenantId, MetadataType type, String id, Direction direction) {
        MetadataDependencyGraph graph = buildGraph(tenantId);
        MetadataNode node = graph.resolve(type, id);
        if (node == null) {
            node = MetadataNode.of(type, id, null);
        }

        List<MetadataEdge> direct = direction == Direction.DEPENDENTS
                ? graph.directDependents(node)
                : graph.directDependencies(node);
        Set<MetadataNode> transitive = direction == Direction.DEPENDENTS
                ? graph.transitiveDependents(node)
                : graph.transitiveDependencies(node);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node", node.toMap());
        result.put("direction", direction.name().toLowerCase());
        result.put("found", graph.contains(node));
        result.put("direct", direct.stream().map(MetadataEdge::toMap).toList());
        result.put("transitive", transitive.stream().map(MetadataNode::toMap).toList());
        result.put("transitiveCount", transitive.size());
        return result;
    }

    /**
     * Returns the full graph (nodes, edges, detected cycles) for visualization / governance.
     */
    public Map<String, Object> graphSummary(String tenantId) {
        MetadataDependencyGraph graph = buildGraph(tenantId);
        List<List<Map<String, Object>>> cycles = graph.findCycles().stream()
                .map(cycle -> cycle.stream().map(MetadataNode::toMap).toList())
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", graph.nodes().stream().map(MetadataNode::toMap).toList());
        result.put("edges", graph.edges().stream().map(MetadataEdge::toMap).toList());
        result.put("nodeCount", graph.nodes().size());
        result.put("edgeCount", graph.edges().size());
        result.put("cycles", cycles);
        result.put("hasCycle", !cycles.isEmpty());
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // Extractors — each best-effort, scoped to the tenant.
    // ---------------------------------------------------------------------------------------

    private void extract(String label, Runnable extractor) {
        try {
            extractor.run();
        } catch (Exception e) {
            log.warn("Dependency-graph extractor '{}' skipped: {}", label, e.getMessage());
        }
    }

    private void extractCollections(String tenantId, MetadataDependencyGraph graph, Set<String> ids) {
        forEachRow("SELECT id, name FROM collection WHERE tenant_id = ?", tenantId, row -> {
            String id = (String) row.get("id");
            graph.addNode(MetadataNode.of(MetadataType.COLLECTION, id, (String) row.get("name")));
            ids.add(id);
        });
    }

    private void extractFields(String tenantId, MetadataDependencyGraph graph) {
        String sql = """
                SELECT f.id, f.name, f.collection_id, f.relationship_type, f.reference_collection_id
                FROM field f JOIN collection c ON f.collection_id = c.id
                WHERE c.tenant_id = ?
                """;
        forEachRow(sql, tenantId, row -> {
            String fieldId = (String) row.get("id");
            String collectionId = (String) row.get("collection_id");
            MetadataNode field = graph.addNode(
                    MetadataNode.of(MetadataType.FIELD, fieldId, (String) row.get("name")));
            MetadataNode collection = MetadataNode.of(MetadataType.COLLECTION, collectionId, null);
            graph.addEdge(field, collection, DependencyKind.FIELD_OF_COLLECTION);

            String refCollectionId = (String) row.get("reference_collection_id");
            String relationshipType = (String) row.get("relationship_type");
            if (refCollectionId != null && relationshipType != null) {
                MetadataNode target = MetadataNode.of(MetadataType.COLLECTION, refCollectionId, null);
                boolean masterDetail = "MASTER_DETAIL".equalsIgnoreCase(relationshipType);
                graph.addEdge(field, target,
                        masterDetail ? DependencyKind.MASTER_DETAIL : DependencyKind.LOOKUP);
                if (masterDetail) {
                    // Collection-level cascade edge so circular master-detail forms a cycle.
                    graph.addEdge(collection, target, DependencyKind.MASTER_DETAIL);
                }
            }
        });
    }

    private void extractFlows(String tenantId, MetadataDependencyGraph graph, Set<String> ids) {
        forEachRow("SELECT id, name FROM flow WHERE tenant_id = ?", tenantId, row -> {
            String id = (String) row.get("id");
            graph.addNode(MetadataNode.of(MetadataType.FLOW, id, (String) row.get("name")));
            ids.add(id);
        });
    }

    private void extractFlowReferences(String tenantId, MetadataDependencyGraph graph,
                                       Set<String> collectionIds, Set<String> flowIds) {
        forEachRow("SELECT id, definition, trigger_config FROM flow WHERE tenant_id = ?", tenantId, row -> {
            String flowId = (String) row.get("id");
            MetadataNode flow = MetadataNode.of(MetadataType.FLOW, flowId, null);

            Set<String> collectionRefs = new LinkedHashSet<>();
            Set<String> flowRefs = new LinkedHashSet<>();
            collectJsonRefs(asText(row.get("definition")), collectionRefs, flowRefs);
            collectJsonRefs(asText(row.get("trigger_config")), collectionRefs, flowRefs);

            for (String ref : collectionRefs) {
                if (collectionIds.contains(ref)) {
                    graph.addEdge(flow, MetadataNode.of(MetadataType.COLLECTION, ref, null),
                            DependencyKind.FLOW_REFERENCES_COLLECTION);
                }
            }
            for (String ref : flowRefs) {
                if (flowIds.contains(ref) && !ref.equals(flowId)) {
                    graph.addEdge(flow, MetadataNode.of(MetadataType.FLOW, ref, null),
                            DependencyKind.FLOW_INVOKES_FLOW);
                }
            }
        });
    }

    private void extractLayouts(String tenantId, MetadataDependencyGraph graph) {
        forEachRow("SELECT id, name, collection_id FROM page_layout WHERE tenant_id = ?", tenantId, row -> {
            MetadataNode layout = graph.addNode(
                    MetadataNode.of(MetadataType.LAYOUT, (String) row.get("id"), (String) row.get("name")));
            graph.addEdge(layout,
                    MetadataNode.of(MetadataType.COLLECTION, (String) row.get("collection_id"), null),
                    DependencyKind.LAYOUT_OF_COLLECTION);
        });
    }

    private void extractLayoutFields(String tenantId, MetadataDependencyGraph graph) {
        String sql = """
                SELECT pl.id AS layout_id, lf.field_id
                FROM layout_field lf
                JOIN layout_section ls ON lf.section_id = ls.id
                JOIN page_layout pl ON ls.layout_id = pl.id
                WHERE pl.tenant_id = ?
                """;
        forEachRow(sql, tenantId, row -> graph.addEdge(
                MetadataNode.of(MetadataType.LAYOUT, (String) row.get("layout_id"), null),
                MetadataNode.of(MetadataType.FIELD, (String) row.get("field_id"), null),
                DependencyKind.LAYOUT_USES_FIELD));
    }

    private void extractLayoutRelatedLists(String tenantId, MetadataDependencyGraph graph) {
        String sql = """
                SELECT lrl.layout_id, lrl.related_collection_id
                FROM layout_related_list lrl
                JOIN page_layout pl ON lrl.layout_id = pl.id
                WHERE pl.tenant_id = ?
                """;
        forEachRow(sql, tenantId, row -> graph.addEdge(
                MetadataNode.of(MetadataType.LAYOUT, (String) row.get("layout_id"), null),
                MetadataNode.of(MetadataType.COLLECTION, (String) row.get("related_collection_id"), null),
                DependencyKind.LAYOUT_RELATED_COLLECTION));
    }

    private void extractValidationRules(String tenantId, MetadataDependencyGraph graph) {
        forEachRow("SELECT id, name, collection_id FROM validation_rule WHERE tenant_id = ?", tenantId, row ->
                graph.addEdge(
                        graph.addNode(MetadataNode.of(MetadataType.VALIDATION_RULE,
                                (String) row.get("id"), (String) row.get("name"))),
                        MetadataNode.of(MetadataType.COLLECTION, (String) row.get("collection_id"), null),
                        DependencyKind.VALIDATION_RULE_OF_COLLECTION));
    }

    private void extractRecordTypes(String tenantId, MetadataDependencyGraph graph) {
        forEachRow("SELECT id, name, collection_id FROM record_type WHERE tenant_id = ?", tenantId, row ->
                graph.addEdge(
                        graph.addNode(MetadataNode.of(MetadataType.RECORD_TYPE,
                                (String) row.get("id"), (String) row.get("name"))),
                        MetadataNode.of(MetadataType.COLLECTION, (String) row.get("collection_id"), null),
                        DependencyKind.RECORD_TYPE_OF_COLLECTION));
    }

    private void extractListViews(String tenantId, MetadataDependencyGraph graph) {
        forEachRow("SELECT id, name, collection_id FROM list_view WHERE tenant_id = ?", tenantId, row ->
                graph.addEdge(
                        graph.addNode(MetadataNode.of(MetadataType.LIST_VIEW,
                                (String) row.get("id"), (String) row.get("name"))),
                        MetadataNode.of(MetadataType.COLLECTION, (String) row.get("collection_id"), null),
                        DependencyKind.LIST_VIEW_OF_COLLECTION));
    }

    private void extractUniqueConstraints(String tenantId, MetadataDependencyGraph graph) {
        forEachRow("SELECT id, collection_id, field_id FROM unique_constraint WHERE tenant_id = ?", tenantId, row -> {
            MetadataNode constraint = graph.addNode(
                    MetadataNode.of(MetadataType.UNIQUE_CONSTRAINT, (String) row.get("id"), null));
            String collectionId = (String) row.get("collection_id");
            if (collectionId != null) {
                graph.addEdge(constraint, MetadataNode.of(MetadataType.COLLECTION, collectionId, null),
                        DependencyKind.UNIQUE_CONSTRAINT_OF_COLLECTION);
            }
            String fieldId = (String) row.get("field_id");
            if (fieldId != null) {
                graph.addEdge(constraint, MetadataNode.of(MetadataType.FIELD, fieldId, null),
                        DependencyKind.UNIQUE_CONSTRAINT_USES_FIELD);
            }
        });
    }

    private void extractObjectPermissions(String tenantId, MetadataDependencyGraph graph) {
        String sql = """
                SELECT po.profile_id, po.collection_id, p.name AS profile_name
                FROM profile_object_permission po
                JOIN profile p ON po.profile_id = p.id
                WHERE p.tenant_id = ?
                """;
        forEachRow(sql, tenantId, row -> graph.addEdge(
                MetadataNode.of(MetadataType.PROFILE, (String) row.get("profile_id"),
                        (String) row.get("profile_name")),
                MetadataNode.of(MetadataType.COLLECTION, (String) row.get("collection_id"), null),
                DependencyKind.OBJECT_PERMISSION));
    }

    private void extractFieldPermissions(String tenantId, MetadataDependencyGraph graph) {
        String sql = """
                SELECT pf.profile_id, pf.field_id, p.name AS profile_name
                FROM profile_field_permission pf
                JOIN profile p ON pf.profile_id = p.id
                WHERE p.tenant_id = ?
                """;
        forEachRow(sql, tenantId, row -> graph.addEdge(
                MetadataNode.of(MetadataType.PROFILE, (String) row.get("profile_id"),
                        (String) row.get("profile_name")),
                MetadataNode.of(MetadataType.FIELD, (String) row.get("field_id"), null),
                DependencyKind.FIELD_PERMISSION));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private void forEachRow(String sql, String tenantId, Consumer<Map<String, Object>> consumer) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId);
        for (Map<String, Object> row : rows) {
            consumer.accept(row);
        }
    }

    private static String asText(Object jsonbValue) {
        return jsonbValue == null ? null : jsonbValue.toString();
    }

    /**
     * Recursively collects values of {@code collectionId} / {@code targetCollectionId} (collection
     * references) and {@code flowId} (sub-flow references) from a flow definition / trigger JSON.
     */
    private void collectJsonRefs(String json, Set<String> collectionRefs, Set<String> flowRefs) {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.debug("Could not parse flow JSON for refs: {}", e.getMessage());
            return;
        }
        walk(root, collectionRefs, flowRefs);
    }

    private void walk(JsonNode node, Set<String> collectionRefs, Set<String> flowRefs) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    if ("collectionId".equals(key) || "targetCollectionId".equals(key)) {
                        collectionRefs.add(value.asText());
                    } else if ("flowId".equals(key)) {
                        flowRefs.add(value.asText());
                    }
                }
                walk(value, collectionRefs, flowRefs);
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, collectionRefs, flowRefs);
            }
        }
    }
}
