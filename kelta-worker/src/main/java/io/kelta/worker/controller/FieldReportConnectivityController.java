package io.kelta.worker.controller;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only connectivity aggregate for spotopened's facility pages: "what
 * carrier and signal do member field reports say this facility gets" —
 * without exposing a single raw {@code field-reports} row.
 *
 * <p>{@code field-reports} grants Portal User {@code canViewAll: false}
 * (see {@code ops/collections/create-field-reports.sh} in spotopened-web):
 * a member can only read their <em>own</em> reports through the generic
 * dynamic route, on purpose — {@code ratePaid}, {@code notes},
 * {@code gateStatus} and {@code roadCondition} are exactly the kind of
 * per-person detail that collection was built to keep row-scoped. Widening
 * {@code canViewAll} to make an aggregate possible would have widened
 * exposure of all of that right along with it.
 *
 * <p>This controller sidesteps the tradeoff instead of resolving it the
 * generic route's way: it queries {@code field-reports} directly through
 * {@link QueryEngine} — which applies no per-caller row filtering itself,
 * that enforcement lives in {@code CerbosRecordAuthorizationAdvice} around
 * the generic route, not in the query engine — with an explicit field
 * selection of only {@code carrier} and {@code signalBars}, computes the
 * aggregate in Java, and returns nothing but that aggregate. No caller,
 * including this controller's own code, ever holds a full row.
 *
 * <p>Nested under {@code /api/field-reports}, not a standalone path, for
 * the same gateway-routing reason documented on
 * {@link FacilityPhotoUploadController}: a path with no registered
 * collection prefix 404s before reaching the worker. Riding
 * {@code field-reports}'s own route means a GET here is Cerbos-gated as a
 * {@code read} action on that collection — Portal User already has
 * {@code canRead: true}; spotopened's Guest profile needed a new grant
 * ({@code ops/collections/grant-field-reports-connectivity-read.sh}) since
 * the original script only covered Portal User. That grant is safe to add
 * precisely because it only unlocks this endpoint's action check, not row
 * visibility on the generic route (still gated by the untouched
 * {@code canViewAll: false}).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/field-reports")
public class FieldReportConnectivityController {

    private static final String COLLECTION = "field-reports";

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;

    public FieldReportConnectivityController(QueryEngine queryEngine, CollectionRegistry collectionRegistry) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
    }

    @GetMapping("/connectivity/{facilityId}")
    public ResponseEntity<Map<String, Object>> connectivity(@PathVariable String facilityId) {
        CollectionDefinition definition = collectionRegistry.get(COLLECTION);
        if (definition == null) {
            return ResponseEntity.notFound().build();
        }

        // MAX_PAGE_SIZE (1000), not the HTTP-facing 200 cap -- this is an
        // internal query the controller constructs itself, not a value
        // parsed from a caller's request, and a crowd-sourced facility
        // check-in collection is nowhere near 1000 rows for one facility.
        QueryRequest request = new QueryRequest(
                new Pagination(1, Pagination.MAX_PAGE_SIZE),
                List.of(),
                List.of("carrier", "signalBars"),
                List.of(FilterCondition.eq("facilityId", facilityId)));

        QueryResult result = queryEngine.executeQuery(definition, request);

        Map<String, long[]> byCarrier = new LinkedHashMap<>(); // normalized key -> [sumBars, count]
        Map<String, String> displayName = new LinkedHashMap<>(); // normalized key -> first-seen casing
        for (Map<String, Object> row : result.data()) {
            String carrier = asTrimmedString(row.get("carrier"));
            Long bars = asLong(row.get("signalBars"));
            if (carrier == null || bars == null) {
                continue;
            }
            // Free-text field, not a picklist -- "Verizon" and "verizon" are
            // the same carrier to a reader even though they are different
            // strings. Grouping key is case-folded; the label shown is
            // whichever casing was reported first, not an invented one.
            String key = carrier.toLowerCase();
            displayName.putIfAbsent(key, carrier);
            long[] agg = byCarrier.computeIfAbsent(key, k -> new long[2]);
            agg[0] += bars;
            agg[1] += 1;
        }

        List<Map<String, Object>> carriers = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : byCarrier.entrySet()) {
            long[] agg = entry.getValue();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("carrier", displayName.get(entry.getKey()));
            c.put("reportCount", agg[1]);
            c.put("avgSignalBars", Math.round((agg[0] * 10.0) / agg[1]) / 10.0);
            carriers.add(c);
        }
        carriers.sort((a, b) -> ((Long) b.get("reportCount")).compareTo((Long) a.get("reportCount")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", facilityId);
        body.put("totalReports", result.data().size());
        body.put("carriers", carriers);
        return ResponseEntity.ok(body);
    }

    private static String asTrimmedString(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
