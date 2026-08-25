package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.SortField;
import io.kelta.runtime.registry.CollectionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic read-only "what's the most recent value" over any collection:
 * {@code GET /api/{collectionName}/latest?fields=<f1,f2>&filter[...]}.
 * A sibling of {@link CollectionAggregateController} for the case that
 * doesn't fit an aggregate — some facts (a status, a condition, a price)
 * are only meaningful as of their most recent report, not averaged across
 * every report ever filed. Same genericity: collection, fields and filters
 * are all caller-supplied, nothing here names a tenant's schema.
 *
 * <p>Same privacy policy as {@link CollectionAggregateController}, for the
 * same reason: a collection can grant {@code canRead} without
 * {@code canViewAll} so a caller sees only their own rows through the
 * generic route, but this endpoint reveals only the specific fields the
 * caller explicitly asked for from one row — a materially smaller
 * disclosure than a raw list, and the actual thing {@code canViewAll}
 * gates. The response shape ({@code {"record": {...}}}, no top-level
 * {@code data} list) means {@code CerbosRecordAuthorizationAdvice} never
 * touches it, for the same reason as the aggregate endpoint.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api")
public class LatestRecordController {

    private final CollectionRegistry registry;
    private final QueryEngine queryEngine;

    public LatestRecordController(CollectionRegistry registry, QueryEngine queryEngine) {
        this.registry = registry;
        this.queryEngine = queryEngine;
    }

    @GetMapping("/{collectionName}/latest")
    public ResponseEntity<Map<String, Object>> latest(
            @PathVariable String collectionName,
            @RequestParam(required = false) String fields,
            @RequestParam(required = false) MultiValueMap<String, String> params) {

        if (fields == null || fields.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        CollectionDefinition definition = registry.get(collectionName);
        if (definition == null) {
            return ResponseEntity.notFound().build();
        }

        List<String> requested = Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(f -> !f.isEmpty())
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<String> selected = new ArrayList<>(requested);
        if (!selected.contains("createdAt")) {
            selected.add("createdAt");
        }

        QueryRequest request = new QueryRequest(
                new Pagination(1, 1),
                List.of(SortField.desc("createdAt")),
                selected,
                FilterCondition.fromParams(params));
        QueryResult result = queryEngine.executeQuery(definition, request);

        Map<String, Object> body = new LinkedHashMap<>();
        if (result.data().isEmpty()) {
            body.put("record", null);
        } else {
            Map<String, Object> row = result.data().get(0);
            Map<String, Object> record = new LinkedHashMap<>();
            for (String field : requested) {
                record.put(field, row.get(field));
            }
            record.put("recordedAt", row.get("createdAt"));
            body.put("record", record);
        }
        return ResponseEntity.ok(body);
    }
}
