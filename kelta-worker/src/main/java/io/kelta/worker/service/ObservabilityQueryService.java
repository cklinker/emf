package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service for querying the Grafana observability stack:
 * <ul>
 *   <li>Tempo — distributed traces (TraceQL)</li>
 *   <li>Loki — application logs (LogQL)</li>
 *   <li>Mimir — metrics via Prometheus-compatible API (PromQL)</li>
 * </ul>
 */
@Service
public class ObservabilityQueryService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityQueryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient tempoClient;
    private final RestClient lokiClient;
    private final RestClient mimirClient;

    public ObservabilityQueryService(@Qualifier("tempoRestClient") RestClient tempoClient,
                                     @Qualifier("lokiRestClient") RestClient lokiClient,
                                     @Qualifier("mimirRestClient") RestClient mimirClient) {
        this.tempoClient = tempoClient;
        this.lokiClient = lokiClient;
        this.mimirClient = mimirClient;
    }

    // -----------------------------------------------------------------------
    // Trace search (Tempo)
    // -----------------------------------------------------------------------

    /**
     * Search for trace spans by time range and optional filters using Tempo TraceQL.
     */
    public SearchResult searchTraceSpans(String tenantSlug, Instant start, Instant end,
                                         Map<String, String> filters, int page, int size) {
        String traceId = filters.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            List<Map<String, Object>> spans = getTraceDetail(traceId);
            // Filter to server spans only and apply other filters
            List<Map<String, Object>> filtered = filterServerSpans(spans, filters);
            return new SearchResult(filtered, filtered.size());
        }

        String traceQL = buildTraceQL(tenantSlug, filters);
        int limit = (page + 1) * size;

        try {
            String url = "/api/search?q=" + urlEncode(traceQL)
                    + "&start=" + start.getEpochSecond()
                    + "&end=" + end.getEpochSecond()
                    + "&limit=" + limit
                    + "&spss=1";

            String responseBody = tempoClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
            JsonNode response = MAPPER.readTree(responseBody);

            List<Map<String, Object>> hits = new ArrayList<>();
            JsonNode traces = response.path("traces");
            if (traces.isArray()) {
                for (JsonNode trace : traces) {
                    hits.add(mapTempoSearchResult(trace));
                }
            }

            // Client-side pagination (Tempo doesn't support offset)
            int fromIndex = Math.min(page * size, hits.size());
            int toIndex = Math.min(fromIndex + size, hits.size());
            List<Map<String, Object>> pagedHits = hits.subList(fromIndex, toIndex);

            return new SearchResult(new ArrayList<>(pagedHits), hits.size());
        } catch (Exception e) {
            log.error("Tempo search failed: {}", e.getMessage(), e);
            return new SearchResult(List.of(), 0);
        }
    }

    /**
     * Get all spans for a trace from Tempo.
     */
    public List<Map<String, Object>> getTraceDetail(String traceId) {
        try {
            String responseBody = tempoClient.get()
                    .uri("/api/traces/{traceId}", traceId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            JsonNode response = MAPPER.readTree(responseBody);
            return parseOtlpTrace(response);
        } catch (Exception e) {
            log.error("Tempo trace detail failed for {}: {}", traceId, e.getMessage(), e);
            return List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Log search (Loki)
    // -----------------------------------------------------------------------

    /**
     * Search application logs using Loki LogQL.
     */
    public SearchResult searchLogs(Map<String, String> filters, int page, int size) {
        String logQL = buildLogQL(filters);
        int limit = (page + 1) * size;

        String startFilter = filters.get("@timestamp_gte");
        String endFilter = filters.get("@timestamp_lte");

        Instant end = endFilter != null ? Instant.parse(endFilter) : Instant.now();
        Instant start = startFilter != null ? Instant.parse(startFilter) : end.minus(Duration.ofHours(1));

        try {
            String url = "/loki/api/v1/query_range?query=" + urlEncode(logQL)
                    + "&start=" + toNanos(start)
                    + "&end=" + toNanos(end)
                    + "&limit=" + limit
                    + "&direction=backward";

            String responseBody = lokiClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
            JsonNode response = MAPPER.readTree(responseBody);

            List<Map<String, Object>> hits = parseLokiResponse(response);

            // Client-side pagination
            int fromIndex = Math.min(page * size, hits.size());
            int toIndex = Math.min(fromIndex + size, hits.size());
            List<Map<String, Object>> pagedHits = hits.subList(fromIndex, toIndex);

            return new SearchResult(new ArrayList<>(pagedHits), hits.size());
        } catch (Exception e) {
            log.error("Loki query failed: {}", e.getMessage(), e);
            return new SearchResult(List.of(), 0);
        }
    }

    // -----------------------------------------------------------------------
    // Metrics (Mimir / Prometheus API)
    // -----------------------------------------------------------------------

    /**
     * Get request rate over time as requests per second (range query).
     */
    public List<Map<String, Object>> getRequestRateOverTime(String tenantSlug,
                                                             Instant start, Instant end,
                                                             String step) {
        String promQL = "sum(rate(traces_spanmetrics_calls_total{span_kind=\"SPAN_KIND_SERVER\"}["
                + step + "]))";

        return parseRangeQueryToDataPoints(executeMimirRangeQuery(promQL, start, end, step), false);
    }

    /**
     * Get latency percentile over time (range query).
     * Returns values in seconds.
     */
    public List<Map<String, Object>> getLatencyOverTime(String tenantSlug,
                                                         Instant start, Instant end,
                                                         String step, double quantile) {
        String promQL = "histogram_quantile(" + quantile
                + ", sum(rate(traces_spanmetrics_latency_bucket{span_kind=\"SPAN_KIND_SERVER\"}["
                + step + "])) by (le))";

        return parseRangeQueryToDataPoints(executeMimirRangeQuery(promQL, start, end, step), false);
    }

    /**
     * Get error count over time (range query).
     */
    public List<Map<String, Object>> getErrorCountOverTime(String tenantSlug,
                                                            Instant start, Instant end,
                                                            String step) {
        String promQL = "sum(increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=~\"[4-5]..\"}"
                + "[" + step + "]))";

        return parseRangeQueryToDataPoints(executeMimirRangeQuery(promQL, start, end, step), true);
    }

    /**
     * Get auth failure count over time (range query).
     */
    public List<Map<String, Object>> getAuthFailuresOverTime(String tenantSlug,
                                                              Instant start, Instant end,
                                                              String step) {
        String promQL = "sum(increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=~\"401|403\"}"
                + "[" + step + "]))";

        return parseRangeQueryToDataPoints(executeMimirRangeQuery(promQL, start, end, step), true);
    }

    /**
     * Get rate-limited request count over time (range query).
     */
    public List<Map<String, Object>> getRateLimitOverTime(String tenantSlug,
                                                           Instant start, Instant end,
                                                           String step) {
        String promQL = "sum(increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=\"429\"}"
                + "[" + step + "]))";

        return parseRangeQueryToDataPoints(executeMimirRangeQuery(promQL, start, end, step), true);
    }

    /**
     * Get active (in-flight) request count over time (range query).
     */
    public List<Map<String, Object>> getActiveRequestsOverTime(String tenantSlug,
                                                                Instant start, Instant end,
                                                                String step) {
        String promQL = "sum(rate(traces_spanmetrics_calls_total{span_kind=\"SPAN_KIND_SERVER\"}["
                + step + "])) * "
                + "avg(sum(rate(traces_spanmetrics_latency_sum{span_kind=\"SPAN_KIND_SERVER\"}["
                + step + "])) / sum(rate(traces_spanmetrics_latency_count{span_kind=\"SPAN_KIND_SERVER\"}["
                + step + "])))";

        return parseRangeQueryToDataPoints(executeMimirRangeQuery(promQL, start, end, step), false);
    }

    /**
     * Parse a Mimir range query response into a list of timestamp/value data points.
     */
    private List<Map<String, Object>> parseRangeQueryToDataPoints(JsonNode response, boolean roundValues) {
        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode results = response.path("data").path("result");
        if (results.isArray() && !results.isEmpty()) {
            JsonNode values = results.get(0).path("values");
            for (JsonNode pair : values) {
                Map<String, Object> point = new HashMap<>();
                long epochSec = pair.get(0).asLong();
                point.put("timestamp", epochSec);
                double val = Double.parseDouble(pair.get(1).asText());
                if (Double.isNaN(val) || Double.isInfinite(val)) val = 0.0;
                point.put("value", roundValues ? Math.round(val) : val);
                result.add(point);
            }
        }
        return result;
    }

    /**
     * Get latency percentiles for a time range.
     */
    public Map<String, Double> getLatencyPercentiles(String tenantSlug,
                                                     Instant start, Instant end) {
        String rangeDuration = toPrometheusDuration(start, end);
        String baseSelector = "{span_kind=\"SPAN_KIND_SERVER\"}";

        String p50Query = "histogram_quantile(0.50, sum(rate(traces_spanmetrics_latency_bucket"
                + baseSelector + "[" + rangeDuration + "])) by (le))";
        String p95Query = "histogram_quantile(0.95, sum(rate(traces_spanmetrics_latency_bucket"
                + baseSelector + "[" + rangeDuration + "])) by (le))";
        String p99Query = "histogram_quantile(0.99, sum(rate(traces_spanmetrics_latency_bucket"
                + baseSelector + "[" + rangeDuration + "])) by (le))";
        String avgQuery = "sum(rate(traces_spanmetrics_latency_sum" + baseSelector + "["
                + rangeDuration + "])) / sum(rate(traces_spanmetrics_latency_count"
                + baseSelector + "[" + rangeDuration + "]))";

        Map<String, Double> result = new HashMap<>();
        // Tempo span metrics uses seconds — convert to microseconds to match existing API contract
        result.put("p50", parsePrometheusScalar(executeMimirInstantQuery(p50Query, end)) * 1_000_000);
        result.put("p95", parsePrometheusScalar(executeMimirInstantQuery(p95Query, end)) * 1_000_000);
        result.put("p99", parsePrometheusScalar(executeMimirInstantQuery(p99Query, end)) * 1_000_000);
        result.put("avg", parsePrometheusScalar(executeMimirInstantQuery(avgQuery, end)) * 1_000_000);
        return result;
    }

    /**
     * Get top endpoints by request count.
     */
    public List<Map<String, Object>> getTopEndpoints(String tenantSlug,
                                                      Instant start, Instant end,
                                                      int limit) {
        String rangeDuration = toPrometheusDuration(start, end);
        String baseSelector = "{span_kind=\"SPAN_KIND_SERVER\"}";

        String countQuery = "topk(" + limit + ", sum by (span_name) (increase(traces_spanmetrics_calls_total"
                + baseSelector + "[" + rangeDuration + "])))";

        String p50Query = "histogram_quantile(0.50, sum by (span_name, le) (rate(traces_spanmetrics_latency_bucket"
                + baseSelector + "[" + rangeDuration + "])))";
        String p95Query = "histogram_quantile(0.95, sum by (span_name, le) (rate(traces_spanmetrics_latency_bucket"
                + baseSelector + "[" + rangeDuration + "])))";
        String p99Query = "histogram_quantile(0.99, sum by (span_name, le) (rate(traces_spanmetrics_latency_bucket"
                + baseSelector + "[" + rangeDuration + "])))";
        String avgQuery = "sum by (span_name) (rate(traces_spanmetrics_latency_sum" + baseSelector
                + "[" + rangeDuration + "])) / sum by (span_name) (rate(traces_spanmetrics_latency_count"
                + baseSelector + "[" + rangeDuration + "]))";

        // Execute all queries
        Map<String, Double> counts = parsePrometheusVector(executeMimirInstantQuery(countQuery, end));
        Map<String, Double> p50s = parsePrometheusVector(executeMimirInstantQuery(p50Query, end));
        Map<String, Double> p95s = parsePrometheusVector(executeMimirInstantQuery(p95Query, end));
        Map<String, Double> p99s = parsePrometheusVector(executeMimirInstantQuery(p99Query, end));
        Map<String, Double> avgs = parsePrometheusVector(executeMimirInstantQuery(avgQuery, end));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : counts.entrySet()) {
            String spanName = entry.getKey();
            Map<String, Object> endpoint = new HashMap<>();
            endpoint.put("endpoint", spanName);
            endpoint.put("requestCount", Math.round(entry.getValue()));
            // Convert seconds to microseconds
            endpoint.put("p50", p50s.getOrDefault(spanName, 0.0) * 1_000_000);
            endpoint.put("p95", p95s.getOrDefault(spanName, 0.0) * 1_000_000);
            endpoint.put("p99", p99s.getOrDefault(spanName, 0.0) * 1_000_000);
            endpoint.put("avgDuration", avgs.getOrDefault(spanName, 0.0) * 1_000_000);
            result.add(endpoint);
        }

        // Sort by request count descending
        result.sort((a, b) -> Long.compare((long) b.get("requestCount"), (long) a.get("requestCount")));
        return result;
    }

    /**
     * Get top errors by operation name.
     */
    public List<Map<String, Object>> getTopErrors(String tenantSlug,
                                                   Instant start, Instant end,
                                                   int limit) {
        String rangeDuration = toPrometheusDuration(start, end);
        String promQL = "topk(" + limit + ", sum by (span_name) (increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=~\"[4-5]..\"}"
                + "[" + rangeDuration + "])))";

        Map<String, Double> errors = parsePrometheusVector(executeMimirInstantQuery(promQL, end));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : errors.entrySet()) {
            Map<String, Object> error = new HashMap<>();
            error.put("path", entry.getKey());
            error.put("count", Math.round(entry.getValue()));
            result.add(error);
        }

        result.sort((a, b) -> Long.compare((long) b.get("count"), (long) a.get("count")));
        return result;
    }

    /**
     * Get metrics summary (total requests, error rate, avg latency).
     */
    public Map<String, Object> getMetricsSummary(String tenantSlug,
                                                  Instant start, Instant end) {
        String rangeDuration = toPrometheusDuration(start, end);
        String baseSelector = "{span_kind=\"SPAN_KIND_SERVER\"}";

        String totalQuery = "sum(increase(traces_spanmetrics_calls_total" + baseSelector
                + "[" + rangeDuration + "]))";
        String errorQuery = "sum(increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=~\"[4-5]..\"}"
                + "[" + rangeDuration + "]))";
        String authQuery = "sum(increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=~\"401|403\"}"
                + "[" + rangeDuration + "]))";
        String rateLimitQuery = "sum(increase(traces_spanmetrics_calls_total"
                + "{span_kind=\"SPAN_KIND_SERVER\", http_status_code=\"429\"}"
                + "[" + rangeDuration + "]))";
        String avgLatencyQuery = "sum(rate(traces_spanmetrics_latency_sum" + baseSelector
                + "[" + rangeDuration + "])) / sum(rate(traces_spanmetrics_latency_count"
                + baseSelector + "[" + rangeDuration + "]))";

        double totalRequests = parsePrometheusScalar(executeMimirInstantQuery(totalQuery, end));
        double errorCount = parsePrometheusScalar(executeMimirInstantQuery(errorQuery, end));
        double authFailures = parsePrometheusScalar(executeMimirInstantQuery(authQuery, end));
        double rateLimited = parsePrometheusScalar(executeMimirInstantQuery(rateLimitQuery, end));
        double avgLatency = parsePrometheusScalar(executeMimirInstantQuery(avgLatencyQuery, end));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRequests", Math.round(totalRequests));
        summary.put("errorCount", Math.round(errorCount));
        summary.put("errorRate", totalRequests > 0 ? errorCount / totalRequests * 100.0 : 0.0);
        // Convert seconds to milliseconds for the API contract
        summary.put("avgLatencyMs", Double.isNaN(avgLatency) ? 0.0 : avgLatency * 1000.0);
        summary.put("authFailures", Math.round(authFailures));
        summary.put("rateLimited", Math.round(rateLimited));
        return summary;
    }

    // -----------------------------------------------------------------------
    // TraceQL builder
    // -----------------------------------------------------------------------

    private String buildTraceQL(String tenantSlug, Map<String, String> filters) {
        StringBuilder sb = new StringBuilder("{ kind = server");

        if (tenantSlug != null && !tenantSlug.isEmpty()) {
            sb.append(" && .kelta.tenant.slug = \"").append(escape(tenantSlug)).append("\"");
        }

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) continue;

            switch (key) {
                case "method" -> sb.append(" && .http.request.method = \"")
                        .append(escape(value)).append("\"");
                case "status" -> {
                    if (value.endsWith("xx")) {
                        int base = Integer.parseInt(value.substring(0, 1)) * 100;
                        sb.append(" && .http.response.status_code >= ").append(base)
                                .append(" && .http.response.status_code < ").append(base + 100);
                    } else {
                        sb.append(" && .http.response.status_code = ").append(value);
                    }
                }
                case "path" -> sb.append(" && (.http.url.path =~ \".*")
                        .append(escape(value)).append(".*\" || .http.route =~ \".*")
                        .append(escape(value)).append(".*\")");
                case "userId" -> sb.append(" && .kelta.user.id = \"")
                        .append(escape(value)).append("\"");
                // traceId is handled before buildTraceQL is called
                default -> { /* ignore */ }
            }
        }

        sb.append(" }");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // LogQL builder
    // -----------------------------------------------------------------------

    private String buildLogQL(Map<String, String> filters) {
        String service = filters.get("service");
        StringBuilder sb = new StringBuilder("{namespace=\"emf\"");
        if (service != null && !service.isEmpty()) {
            sb.append(", app=\"").append(escape(service)).append("\"");
        }
        sb.append("}");

        String query = filters.get("query");
        if (query != null && !query.isEmpty()) {
            sb.append(" |= \"").append(escape(query)).append("\"");
        }

        // Use json pipeline for structured field filters
        boolean needsJson = false;
        StringBuilder pipeline = new StringBuilder();

        String level = filters.get("level");
        if (level != null && !level.isEmpty()) {
            needsJson = true;
            pipeline.append(" | level =~ \"(?i)").append(escape(level)).append("\"");
        }

        String traceId = filters.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            needsJson = true;
            pipeline.append(" | traceId = \"").append(escape(traceId)).append("\"");
        }

        if (needsJson) {
            sb.append(" | json").append(pipeline);
        }

        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Tempo response parsers
    // -----------------------------------------------------------------------

    private Map<String, Object> mapTempoSearchResult(JsonNode trace) {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("traceID", trace.path("traceID").asText());
        hit.put("operationName", trace.path("rootTraceName").asText());

        String startNano = trace.path("startTimeUnixNano").asText("0");
        long startMs = Long.parseLong(startNano) / 1_000_000;
        hit.put("startTimeMillis", startMs);
        hit.put("duration", trace.path("durationMs").asLong() * 1000); // ms → μs

        // Extract tagMap from spanSets attributes
        Map<String, Object> tagMap = new LinkedHashMap<>();
        tagMap.put("process.serviceName", trace.path("rootServiceName").asText());

        JsonNode spanSets = trace.path("spanSets");
        if (spanSets.isArray() && !spanSets.isEmpty()) {
            JsonNode spans = spanSets.get(0).path("spans");
            if (spans.isArray() && !spans.isEmpty()) {
                JsonNode span = spans.get(0);
                hit.put("spanID", span.path("spanID").asText());
                flattenOtlpAttributes(span.path("attributes"), tagMap);
            }
        }

        hit.put("tagMap", tagMap);
        return hit;
    }

    private List<Map<String, Object>> parseOtlpTrace(JsonNode response) {
        List<Map<String, Object>> spans = new ArrayList<>();

        JsonNode batches = response.path("batches");
        if (!batches.isArray()) {
            // Tempo may return resourceSpans format
            batches = response.path("resourceSpans");
        }
        if (!batches.isArray()) return spans;

        for (JsonNode batch : batches) {
            Map<String, Object> resourceTagMap = new LinkedHashMap<>();
            JsonNode resourceAttrs = batch.path("resource").path("attributes");
            flattenOtlpAttributes(resourceAttrs, resourceTagMap);

            String serviceName = resourceTagMap.getOrDefault("service.name", "").toString();

            JsonNode scopeSpans = batch.path("scopeSpans");
            if (!scopeSpans.isArray()) {
                scopeSpans = batch.path("instrumentationLibrarySpans");
            }
            if (!scopeSpans.isArray()) continue;

            for (JsonNode scope : scopeSpans) {
                JsonNode spanArray = scope.path("spans");
                if (!spanArray.isArray()) continue;

                for (JsonNode span : spanArray) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("traceID", hexFromBase64OrPlain(span.path("traceId").asText()));
                    hit.put("spanID", hexFromBase64OrPlain(span.path("spanId").asText()));
                    hit.put("operationName", span.path("name").asText());

                    String startNano = span.path("startTimeUnixNano").asText("0");
                    String endNano = span.path("endTimeUnixNano").asText("0");
                    long startNs = Long.parseLong(startNano);
                    long endNs = Long.parseLong(endNano);
                    hit.put("startTimeMillis", startNs / 1_000_000);
                    hit.put("duration", (endNs - startNs) / 1_000); // nanos → μs

                    Map<String, Object> tagMap = new LinkedHashMap<>();
                    tagMap.put("process.serviceName", serviceName);
                    for (Map.Entry<String, Object> re : resourceTagMap.entrySet()) {
                        tagMap.put("process." + re.getKey(), re.getValue());
                    }
                    flattenOtlpAttributes(span.path("attributes"), tagMap);
                    hit.put("tagMap", tagMap);

                    String parentSpanId = span.path("parentSpanId").asText("");
                    if (!parentSpanId.isEmpty()) {
                        String hexParent = hexFromBase64OrPlain(parentSpanId);
                        hit.put("references", List.of(Map.of("refType", "CHILD_OF",
                                "traceID", hit.get("traceID"), "spanID", hexParent)));
                    }

                    spans.add(hit);
                }
            }
        }

        // Sort by start time
        spans.sort(Comparator.comparingLong(s -> (long) s.get("startTimeMillis")));
        return spans;
    }

    private List<Map<String, Object>> filterServerSpans(List<Map<String, Object>> spans,
                                                         Map<String, String> filters) {
        return spans.stream().filter(span -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> tagMap = (Map<String, Object>) span.get("tagMap");
            if (tagMap == null) return false;

            Object kind = tagMap.get("span.kind");
            if (kind == null) kind = tagMap.get("span_kind");
            // Accept if kind is "server" or if it's a top-level span
            if (kind != null && !"server".equalsIgnoreCase(kind.toString())
                    && !"SPAN_KIND_SERVER".equals(kind.toString())) {
                return false;
            }

            String method = filters.get("method");
            if (method != null && !method.isEmpty()) {
                Object m = tagMap.get("http.request.method");
                if (m == null) m = tagMap.get("http.method");
                if (m == null || !method.equalsIgnoreCase(m.toString())) return false;
            }
            return true;
        }).toList();
    }

    // -----------------------------------------------------------------------
    // Loki response parser
    // -----------------------------------------------------------------------

    private List<Map<String, Object>> parseLokiResponse(JsonNode response) {
        List<Map<String, Object>> hits = new ArrayList<>();

        JsonNode results = response.path("data").path("result");
        if (!results.isArray()) return hits;

        for (JsonNode stream : results) {
            JsonNode streamLabels = stream.path("stream");
            String app = streamLabels.path("app").asText("");

            JsonNode values = stream.path("values");
            if (!values.isArray()) continue;

            for (JsonNode entry : values) {
                if (!entry.isArray() || entry.size() < 2) continue;
                String nanoTimestamp = entry.get(0).asText();
                String logLine = entry.get(1).asText();

                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("_id", nanoTimestamp);
                hit.put("@timestamp", Instant.ofEpochSecond(0,
                        Long.parseLong(nanoTimestamp)).toString());
                hit.put("service", app);

                // Try to parse structured JSON log
                try {
                    JsonNode logJson = MAPPER.readTree(logLine);
                    hit.put("message", logJson.path("message").asText(logLine));
                    hit.put("level", logJson.path("level").asText(""));
                    hit.put("logger", logJson.path("logger_name").asText(
                            logJson.path("logger").asText("")));
                    hit.put("thread", logJson.path("thread_name").asText(
                            logJson.path("thread").asText("")));
                    hit.put("traceId", logJson.path("traceId").asText(
                            logJson.path("trace_id").asText("")));
                } catch (Exception e) {
                    // Plain text log
                    hit.put("message", logLine);
                    hit.put("level", "");
                    hit.put("logger", "");
                    hit.put("thread", "");
                    hit.put("traceId", "");
                }

                hits.add(hit);
            }
        }
        return hits;
    }

    // -----------------------------------------------------------------------
    // Mimir (Prometheus API) helpers
    // -----------------------------------------------------------------------

    private JsonNode executeMimirInstantQuery(String promQL, Instant time) {
        try {
            String url = "/prometheus/api/v1/query?query=" + urlEncode(promQL)
                    + "&time=" + time.getEpochSecond();

            String responseBody = mimirClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
            return MAPPER.readTree(responseBody);
        } catch (Exception e) {
            log.error("Mimir instant query failed: {}", e.getMessage(), e);
            return MAPPER.createObjectNode();
        }
    }

    private JsonNode executeMimirRangeQuery(String promQL, Instant start, Instant end,
                                             String step) {
        try {
            String url = "/prometheus/api/v1/query_range?query=" + urlEncode(promQL)
                    + "&start=" + start.getEpochSecond()
                    + "&end=" + end.getEpochSecond()
                    + "&step=" + urlEncode(step);

            String responseBody = mimirClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
            return MAPPER.readTree(responseBody);
        } catch (Exception e) {
            log.error("Mimir range query failed: {}", e.getMessage(), e);
            return MAPPER.createObjectNode();
        }
    }

    /**
     * Extract a single scalar value from a Prometheus instant query response.
     */
    private double parsePrometheusScalar(JsonNode response) {
        JsonNode result = response.path("data").path("result");
        if (result.isArray() && !result.isEmpty()) {
            JsonNode value = result.get(0).path("value");
            if (value.isArray() && value.size() >= 2) {
                String val = value.get(1).asText("0");
                try {
                    double d = Double.parseDouble(val);
                    return Double.isNaN(d) || Double.isInfinite(d) ? 0.0 : d;
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    /**
     * Extract label-value pairs from a Prometheus instant vector response,
     * keyed by the "span_name" label.
     */
    private Map<String, Double> parsePrometheusVector(JsonNode response) {
        Map<String, Double> result = new LinkedHashMap<>();
        JsonNode results = response.path("data").path("result");
        if (!results.isArray()) return result;

        for (JsonNode item : results) {
            String spanName = item.path("metric").path("span_name").asText("");
            JsonNode value = item.path("value");
            if (value.isArray() && value.size() >= 2) {
                try {
                    double d = Double.parseDouble(value.get(1).asText("0"));
                    if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                        result.put(spanName, d);
                    }
                } catch (NumberFormatException e) {
                    // skip
                }
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // OTLP attribute flattening
    // -----------------------------------------------------------------------

    private void flattenOtlpAttributes(JsonNode attributes, Map<String, Object> target) {
        if (attributes == null || !attributes.isArray()) return;

        for (JsonNode attr : attributes) {
            String key = attr.path("key").asText();
            JsonNode valueNode = attr.path("value");

            if (valueNode.has("stringValue")) {
                target.put(key, valueNode.path("stringValue").asText());
            } else if (valueNode.has("intValue")) {
                target.put(key, Long.parseLong(valueNode.path("intValue").asText()));
            } else if (valueNode.has("doubleValue")) {
                target.put(key, valueNode.path("doubleValue").asDouble());
            } else if (valueNode.has("boolValue")) {
                target.put(key, valueNode.path("boolValue").asBoolean());
            } else {
                target.put(key, valueNode.toString());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private String toPrometheusDuration(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds <= 0) seconds = 3600; // default 1h
        return seconds + "s";
    }

    private static String toNanos(Instant instant) {
        return String.valueOf(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Convert a value that may be base64-encoded or plain hex to hex.
     * Tempo returns IDs as hex strings in search but may use base64 in OTLP responses.
     */
    private static String hexFromBase64OrPlain(String value) {
        if (value == null || value.isEmpty()) return "";
        // If it looks like hex already (only hex chars), return as-is
        if (value.matches("[0-9a-fA-F]+")) return value;
        // Try base64 decode
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return value;
        }
    }

    public record SearchResult(List<Map<String, Object>> hits, long totalHits) {}
}
