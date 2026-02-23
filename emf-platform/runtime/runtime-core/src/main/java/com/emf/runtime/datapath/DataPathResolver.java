package com.emf.runtime.datapath;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.ReferenceConfig;
import com.emf.runtime.query.QueryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Runtime resolver that follows a {@link DataPath} against live data.
 *
 * <p>Given a data path expression (e.g., {@code "order_id.customer_id.email"}),
 * a source record, and its collection definition, this resolver:
 * <ol>
 *   <li>For each relationship segment: reads the FK value from the current record,
 *       fetches the target record via {@code QueryEngine.getById()}</li>
 *   <li>For the terminal segment: returns the field value from the final record</li>
 * </ol>
 *
 * <p><strong>Null handling:</strong> If any FK value is null or the target record
 * is not found at any point in the traversal, the resolver returns null gracefully
 * (short-circuit). This is normal runtime behavior for optional relationships.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. It delegates to
 * thread-safe components (QueryEngine, CollectionDefinitionProvider).
 *
 * @since 1.0.0
 */
public class DataPathResolver {

    private static final Logger logger = LoggerFactory.getLogger(DataPathResolver.class);

    private final QueryEngine queryEngine;
    private final CollectionDefinitionProvider collectionProvider;

    /**
     * Creates a new DataPathResolver.
     *
     * @param queryEngine        the query engine for fetching records
     * @param collectionProvider the provider for collection definitions
     */
    public DataPathResolver(QueryEngine queryEngine,
                            CollectionDefinitionProvider collectionProvider) {
        this.queryEngine = Objects.requireNonNull(queryEngine, "queryEngine cannot be null");
        this.collectionProvider = Objects.requireNonNull(collectionProvider, "collectionProvider cannot be null");
    }

    /**
     * Resolves a single data path to its terminal value.
     *
     * @param path             the data path to resolve
     * @param sourceRecord     the starting record data
     * @param sourceCollection the starting collection definition
     * @return the resolved value, or null if any hop is null/missing
     */
    public Object resolve(DataPath path, Map<String, Object> sourceRecord,
                          CollectionDefinition sourceCollection) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(sourceRecord, "sourceRecord cannot be null");
        Objects.requireNonNull(sourceCollection, "sourceCollection cannot be null");

        // Simple (no traversal) — just read the field value
        if (path.isSimple()) {
            return sourceRecord.get(path.terminal().fieldName());
        }

        // Multi-hop resolution
        Map<String, Object> currentRecord = sourceRecord;
        CollectionDefinition currentCollection = sourceCollection;

        for (DataPathSegment segment : path.relationships()) {
            // Find the field definition for this relationship
            FieldDefinition fieldDef = currentCollection.getField(segment.fieldName());
            if (fieldDef == null) {
                logger.warn("Field '{}' not found in collection '{}' while resolving path '{}'",
                    segment.fieldName(), currentCollection.name(), path.expression());
                return null;
            }

            if (!fieldDef.type().isRelationship()) {
                logger.warn("Field '{}' in collection '{}' is not a relationship field (type: {}) " +
                    "while resolving path '{}'",
                    segment.fieldName(), currentCollection.name(), fieldDef.type(), path.expression());
                return null;
            }

            // Read FK value
            Object fkValue = currentRecord.get(segment.fieldName());
            if (fkValue == null) {
                logger.debug("FK value is null for field '{}' in collection '{}' " +
                    "while resolving path '{}' — short-circuiting",
                    segment.fieldName(), currentCollection.name(), path.expression());
                return null;
            }

            // Get target collection
            ReferenceConfig refConfig = fieldDef.referenceConfig();
            if (refConfig == null || refConfig.targetCollection() == null) {
                logger.warn("Field '{}' in collection '{}' has no reference config " +
                    "while resolving path '{}'",
                    segment.fieldName(), currentCollection.name(), path.expression());
                return null;
            }

            String targetCollectionName = refConfig.targetCollection();
            CollectionDefinition targetCollection = collectionProvider.getByName(targetCollectionName);
            if (targetCollection == null) {
                logger.warn("Target collection '{}' not found while resolving path '{}'",
                    targetCollectionName, path.expression());
                return null;
            }

            // Fetch target record
            Optional<Map<String, Object>> targetRecord =
                queryEngine.getById(targetCollection, fkValue.toString());
            if (targetRecord.isEmpty()) {
                logger.debug("Target record '{}' not found in collection '{}' " +
                    "while resolving path '{}'",
                    fkValue, targetCollectionName, path.expression());
                return null;
            }

            // Advance to next hop
            currentRecord = targetRecord.get();
            currentCollection = targetCollection;
        }

        // Read terminal field value
        return currentRecord.get(path.terminal().fieldName());
    }

    /**
     * Resolves multiple data paths, optimizing for shared prefixes.
     *
     * <p>Paths that share common relationship prefixes will reuse the same
     * intermediate record fetches. For example, resolving both
     * {@code "order_id.customer_id.email"} and {@code "order_id.customer_id.name"}
     * will fetch the order and customer records only once.
     *
     * @param paths            the data paths to resolve
     * @param sourceRecord     the starting record data
     * @param sourceCollection the starting collection definition
     * @return map of path expression → resolved value (null values included)
     */
    public Map<String, Object> resolveAll(List<DataPath> paths,
                                           Map<String, Object> sourceRecord,
                                           CollectionDefinition sourceCollection) {
        Objects.requireNonNull(paths, "paths cannot be null");
        Objects.requireNonNull(sourceRecord, "sourceRecord cannot be null");
        Objects.requireNonNull(sourceCollection, "sourceCollection cannot be null");

        if (paths.isEmpty()) {
            return Collections.emptyMap();
        }

        // Build a prefix tree for shared traversal optimization
        PrefixNode root = buildPrefixTree(paths);

        // Resolve using the prefix tree (DFS traversal)
        Map<String, Object> results = new LinkedHashMap<>();
        resolveTree(root, sourceRecord, sourceCollection, results);
        return results;
    }

    /**
     * Builds a prefix tree from the list of data paths.
     * Paths with shared prefixes share tree nodes.
     */
    private PrefixNode buildPrefixTree(List<DataPath> paths) {
        PrefixNode root = new PrefixNode(null, null);

        for (DataPath path : paths) {
            PrefixNode current = root;
            for (int i = 0; i < path.segments().size(); i++) {
                DataPathSegment segment = path.segments().get(i);
                String fieldName = segment.fieldName();

                PrefixNode child = current.children.get(fieldName);
                if (child == null) {
                    child = new PrefixNode(fieldName, segment);
                    current.children.put(fieldName, child);
                }

                if (i == path.segments().size() - 1) {
                    // This is a terminal node — mark with the full expression
                    child.terminalExpressions.add(path.expression());
                }

                current = child;
            }
        }

        return root;
    }

    /**
     * Resolves the prefix tree using DFS, reusing fetched records at each level.
     */
    private void resolveTree(PrefixNode node, Map<String, Object> currentRecord,
                             CollectionDefinition currentCollection,
                             Map<String, Object> results) {
        for (Map.Entry<String, PrefixNode> entry : node.children.entrySet()) {
            PrefixNode child = entry.getValue();
            String fieldName = child.fieldName;

            if (!child.terminalExpressions.isEmpty() &&
                child.segment != null &&
                child.segment.type() == DataPathSegment.DataPathSegmentType.FIELD) {
                // Terminal field — read value
                Object value = currentRecord.get(fieldName);
                for (String expr : child.terminalExpressions) {
                    results.put(expr, value);
                }
            }

            if (!child.children.isEmpty() &&
                child.segment != null &&
                child.segment.type() == DataPathSegment.DataPathSegmentType.RELATIONSHIP) {
                // Relationship — traverse once and recurse
                FieldDefinition fieldDef = currentCollection.getField(fieldName);
                if (fieldDef == null || !fieldDef.type().isRelationship()) {
                    // Put null for all descendant expressions
                    nullifyDescendants(child, results);
                    continue;
                }

                Object fkValue = currentRecord.get(fieldName);
                if (fkValue == null) {
                    nullifyDescendants(child, results);
                    continue;
                }

                ReferenceConfig refConfig = fieldDef.referenceConfig();
                if (refConfig == null || refConfig.targetCollection() == null) {
                    nullifyDescendants(child, results);
                    continue;
                }

                CollectionDefinition targetCollection =
                    collectionProvider.getByName(refConfig.targetCollection());
                if (targetCollection == null) {
                    nullifyDescendants(child, results);
                    continue;
                }

                Optional<Map<String, Object>> targetRecord =
                    queryEngine.getById(targetCollection, fkValue.toString());
                if (targetRecord.isEmpty()) {
                    nullifyDescendants(child, results);
                    continue;
                }

                // Recurse into children with the fetched record
                resolveTree(child, targetRecord.get(), targetCollection, results);
            }
        }
    }

    /**
     * Sets null for all terminal expressions in the subtree.
     */
    private void nullifyDescendants(PrefixNode node, Map<String, Object> results) {
        for (String expr : node.terminalExpressions) {
            results.put(expr, null);
        }
        for (PrefixNode child : node.children.values()) {
            nullifyDescendants(child, results);
        }
    }

    /**
     * Internal prefix tree node for shared traversal optimization.
     */
    private static class PrefixNode {
        final String fieldName;
        final DataPathSegment segment;
        final Map<String, PrefixNode> children = new LinkedHashMap<>();
        final List<String> terminalExpressions = new ArrayList<>();

        PrefixNode(String fieldName, DataPathSegment segment) {
            this.fieldName = fieldName;
            this.segment = segment;
        }
    }
}
