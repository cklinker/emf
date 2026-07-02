package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds groups of duplicate records within a collection, matched on a caller-chosen set of fields.
 *
 * <p>Detection is <em>read-only</em>. It scans up to {@link #MAX_SCAN} records through the
 * authorized {@link QueryEngine} (which validates field names against the collection definition
 * and resolves the correct per-tenant schema / RLS) and groups them in memory — deliberately
 * avoiding raw SQL on the dynamic user tables. Merging duplicates is a separate, guarded write
 * path (follow-up).
 *
 * @since 1.0.0
 */
@Service
public class DuplicateDetectionService {

    /** Upper bound on records scanned per call; larger collections report {@code truncated=true}. */
    static final int MAX_SCAN = 5000;

    /** Page size for the bounded scan (the query engine caps a single page at 1000). */
    static final int PAGE_SIZE = 1000;

    /** Composite-key separator — a control char unlikely to appear in field values. */
    private static final String KEY_SEP = "\u0001";

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;

    public DuplicateDetectionService(QueryEngine queryEngine, CollectionRegistry collectionRegistry) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
    }

    /** A set of records that share the same values on the match fields. */
    public record DuplicateGroup(Map<String, Object> values, int count, List<String> recordIds) {
    }

    /** Detection outcome: the duplicate groups plus how much was scanned. */
    public record Result(List<DuplicateGroup> groups, int scanned, boolean truncated) {
    }

    /**
     * Finds duplicate groups in a collection.
     *
     * @param collectionName the collection to scan
     * @param matchFields    the fields whose equal values define a duplicate (non-empty)
     * @param maxGroups      max groups to return (clamped 1..500)
     * @return the duplicate groups (largest first) + scan stats
     * @throws IllegalArgumentException if the collection is unknown or matchFields is empty
     */
    public Result findDuplicates(String collectionName, List<String> matchFields, int maxGroups) {
        if (matchFields == null || matchFields.isEmpty()) {
            throw new IllegalArgumentException("matchFields must be non-empty");
        }
        CollectionDefinition def = collectionRegistry.get(collectionName);
        if (def == null) {
            throw new IllegalArgumentException("Collection not found: " + collectionName);
        }
        int clampedGroups = Math.min(Math.max(maxGroups, 1), 500);

        // Select the match fields + id. The QueryEngine validates the field names against the
        // definition (unknown field -> InvalidQueryException), so no identifier can be injected.
        List<String> selectFields = new ArrayList<>(matchFields);
        if (!selectFields.contains("id")) {
            selectFields.add("id");
        }
        // Bounded scan: page through in PAGE_SIZE chunks (the engine caps a page at 1000) up to
        // MAX_SCAN. Stop early on a short/empty page.
        List<Map<String, Object>> rows = new ArrayList<>();
        int page = 1;
        while (rows.size() < MAX_SCAN) {
            QueryRequest request = new QueryRequest(
                    new Pagination(page, PAGE_SIZE), List.of(), selectFields, List.of());
            QueryResult result = queryEngine.executeQuery(def, request);
            List<Map<String, Object>> data = result.data();
            if (data.isEmpty()) {
                break;
            }
            rows.addAll(data);
            if (data.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        // Group by the composite of match-field values. Rows with a null/blank value in ANY match
        // field are skipped (nulls are not treated as equal -- avoids false "duplicates").
        Map<String, List<Map<String, Object>>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = compositeKey(row, matchFields);
            if (key == null) {
                continue;
            }
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<DuplicateGroup> groups = new ArrayList<>();
        for (List<Map<String, Object>> members : byKey.values()) {
            if (members.size() < 2) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String f : matchFields) {
                values.put(f, members.get(0).get(f));
            }
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> m : members) {
                Object id = m.get("id");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
            groups.add(new DuplicateGroup(values, members.size(), ids));
        }

        groups.sort((a, b) -> Integer.compare(b.count(), a.count()));
        if (groups.size() > clampedGroups) {
            groups = new ArrayList<>(groups.subList(0, clampedGroups));
        }

        boolean truncated = rows.size() >= MAX_SCAN;
        return new Result(groups, rows.size(), truncated);
    }

    /** Composite key of the match-field values, or null if any value is null/blank. */
    private static String compositeKey(Map<String, Object> row, List<String> matchFields) {
        StringBuilder sb = new StringBuilder();
        for (String f : matchFields) {
            Object v = row.get(f);
            if (v == null) {
                return null;
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                return null;
            }
            sb.append(s).append(KEY_SEP);
        }
        return sb.toString();
    }
}
