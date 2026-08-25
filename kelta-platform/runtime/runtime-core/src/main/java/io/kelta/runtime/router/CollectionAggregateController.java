package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic read-only group-and-count/average aggregate over any collection:
 * {@code GET /api/{collectionName}/aggregate?groupBy=<field>&avg=<numericField>&filter[...]}.
 *
 * <p>A tenant/collection-agnostic sibling to {@link DynamicCollectionRouter} —
 * every field name (which collection, which field to group by, which field
 * to average, which filters to apply) is a caller-supplied parameter, not
 * anything hardcoded here. This exists as a platform capability because
 * {@link QueryEngine#aggregate} has no {@code GROUP BY}: it computes one
 * aggregate over a whole filtered set, not one per group. Grouping is done
 * here, in memory, over a bounded row fetch — real SQL {@code GROUP BY}
 * support belongs in {@code QueryEngine} itself if a caller ever needs more
 * than {@link Pagination#MAX_PAGE_SIZE} rows aggregated; this is the
 * pragmatic version for "how many, and what's the average, per distinct
 * value of this field."
 *
 * <p><b>Deliberately bypasses row-level {@code canViewAll}.</b> A collection
 * can grant {@code canRead} without {@code canViewAll} specifically so a
 * caller can read only their own rows through {@link DynamicCollectionRouter}
 * (row-level filtering happens in {@code CerbosRecordAuthorizationAdvice},
 * downstream of this class, keyed off a JSON:API {@code data} array this
 * endpoint's response never contains — see below). This endpoint only
 * requires the collection-level {@code read} action (the same gate
 * {@code RouteAuthorizationFilter} already applies to any GET under
 * {@code /api/{collectionName}/**}) and then reveals only a count and an
 * average per group — no row, no id, no other field. That is a materially
 * smaller disclosure than a raw list, which is the actual reason
 * {@code canViewAll} exists to gate: this endpoint is a different, safer
 * capability, not a bypass of the same one.
 *
 * <p>Response shape is deliberately not the JSON:API {@code {"data": [...]}}
 * envelope every other collection endpoint returns — {@code
 * {"totalCount": N, "groups": [{"key", "count", "avg"}, ...]}} instead — so
 * {@code CerbosRecordAuthorizationAdvice} (which only inspects a top-level
 * {@code data} list) leaves this response alone entirely; it is not a list
 * of records to filter; it never was one.
 *
 * <p>Grouping is exact-value equality on the field's string representation
 * — no case-folding, no normalization. A caller whose field is genuinely
 * free text (and wants e.g. "Verizon"/"verizon" folded together) does that
 * itself; a generic platform endpoint has no basis for guessing which
 * fields want that and which don't.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api")
public class CollectionAggregateController {

    private final CollectionRegistry registry;
    private final QueryEngine queryEngine;

    public CollectionAggregateController(CollectionRegistry registry, QueryEngine queryEngine) {
        this.registry = registry;
        this.queryEngine = queryEngine;
    }

    @GetMapping("/{collectionName}/aggregate")
    public ResponseEntity<Map<String, Object>> aggregate(
            @PathVariable String collectionName,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String avg,
            @RequestParam(required = false) MultiValueMap<String, String> params) {

        if (groupBy == null || groupBy.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        CollectionDefinition definition = registry.get(collectionName);
        if (definition == null) {
            return ResponseEntity.notFound().build();
        }

        List<String> fields = avg != null && !avg.isBlank()
                ? List.of(groupBy, avg)
                : List.of(groupBy);
        List<FilterCondition> filters = FilterCondition.fromParams(params);

        QueryRequest request = new QueryRequest(
                new Pagination(1, Pagination.MAX_PAGE_SIZE),
                List.of(),
                fields,
                filters);
        QueryResult result = queryEngine.executeQuery(definition, request);

        Map<String, long[]> counts = new LinkedHashMap<>(); // key -> [count]
        Map<String, double[]> sums = new LinkedHashMap<>(); // key -> [sum, sampleCount]
        for (Map<String, Object> row : result.data()) {
            String key = stringValue(row.get(groupBy));
            if (key == null) {
                continue;
            }
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            if (avg != null) {
                Double numeric = numericValue(row.get(avg));
                if (numeric != null) {
                    double[] agg = sums.computeIfAbsent(key, k -> new double[2]);
                    agg[0] += numeric;
                    agg[1] += 1;
                }
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("key", entry.getKey());
            group.put("count", entry.getValue()[0]);
            double[] agg = sums.get(entry.getKey());
            group.put("avg", agg != null && agg[1] > 0 ? roundToOneDecimal(agg[0] / agg[1]) : null);
            groups.add(group);
        }
        groups.sort((a, b) -> ((Long) b.get("count")).compareTo((Long) a.get("count")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalCount", result.data().size());
        body.put("groups", groups);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Double numericValue(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
