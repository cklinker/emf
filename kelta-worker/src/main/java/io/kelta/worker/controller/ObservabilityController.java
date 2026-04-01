package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.AuditQueryService;
import io.kelta.worker.service.ObservabilityQueryService;
import io.kelta.worker.service.ObservabilityQueryService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for observability data — request logs, application logs, and audit events.
 * Trace/log queries go to OpenSearch via RestClient; audit queries go to PostgreSQL.
 */
@RestController
@RequestMapping("/api/admin/observability")
public class ObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityController.class);

    private final ObservabilityQueryService queryService;
    private final AuditQueryService auditQueryService;

    public ObservabilityController(ObservabilityQueryService queryService,
                                   AuditQueryService auditQueryService) {
        this.queryService = queryService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/request-logs")
    public ResponseEntity<Map<String, Object>> searchRequestLogs(
            @RequestHeader(value = "X-Tenant-Slug", required = false) String tenantSlug,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Instant endInstant = end != null ? Instant.parse(end) : Instant.now();
            Instant startInstant = start != null ? Instant.parse(start) : endInstant.minusSeconds(3600);

            Map<String, String> filters = new HashMap<>();
            if (method != null) filters.put("method", method);
            if (status != null) filters.put("status", status);
            if (path != null) filters.put("path", path);
            if (traceId != null) filters.put("traceId", traceId);
            if (userId != null) filters.put("userId", userId);

            SearchResult result = queryService.searchTraceSpans(tenantSlug, startInstant, endInstant,
                    filters, page, size);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("hits", result.hits());
            attributes.put("totalHits", result.totalHits());
            attributes.put("page", page);
            attributes.put("size", size);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("request-logs", "search", attributes));
        } catch (Exception e) {
            log.error("Failed to search request logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to search request logs"));
        }
    }

    @GetMapping("/request-logs/{traceId}")
    public ResponseEntity<Map<String, Object>> getRequestLog(
            @PathVariable String traceId) {
        try {
            Map<String, String> filters = Map.of("traceId", traceId);
            SearchResult result = queryService.searchTraceSpans(null,
                    Instant.now().minusSeconds(86400 * 30), Instant.now(),
                    filters, 0, 100);

            // Query request data from PostgreSQL
            List<Map<String, Object>> requestDataList = queryService.getTraceRequestData(traceId);

            // Merge captured data into spans by matching spanId
            if (!requestDataList.isEmpty()) {
                Map<String, Map<String, Object>> dataBySpanId = new HashMap<>();
                for (Map<String, Object> dataHit : requestDataList) {
                    String spanId = (String) dataHit.get("span_id");
                    if (spanId != null) {
                        dataBySpanId.put(spanId, dataHit);
                    }
                }
                for (Map<String, Object> span : result.hits()) {
                    String spanId = (String) span.get("spanID");
                    Map<String, Object> captured = dataBySpanId.get(spanId);

                    // Fallback: match by parent spanId
                    if (captured == null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> refs = (List<Map<String, Object>>) span.get("references");
                        if (refs != null) {
                            for (Map<String, Object> ref : refs) {
                                String parentSpanId = (String) ref.get("spanID");
                                if (parentSpanId != null) {
                                    captured = dataBySpanId.get(parentSpanId);
                                    if (captured != null) break;
                                }
                            }
                        }
                    }

                    if (captured != null) {
                        mergeCapturedData(span, captured);
                    }
                }
            }

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("spans", result.hits());
            attributes.put("traceId", traceId);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("request-log-detail", traceId, attributes));
        } catch (Exception e) {
            log.error("Failed to get request log detail: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to get request log detail"));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Map<String, String> filters = new HashMap<>();
            if (query != null) filters.put("query", query);
            if (level != null) filters.put("level", level);
            if (service != null) filters.put("service", service);
            if (traceId != null) filters.put("traceId", traceId);
            if (start != null) filters.put("@timestamp_gte", start);
            if (end != null) filters.put("@timestamp_lte", end);

            SearchResult result = queryService.searchLogs(filters, page, size);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("hits", result.hits());
            attributes.put("totalHits", result.totalHits());
            attributes.put("page", page);
            attributes.put("size", size);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("log-entries", "search", attributes));
        } catch (Exception e) {
            log.error("Failed to search logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to search logs"));
        }
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> searchAudit(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestParam(required = false) String auditType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Instant startInstant = start != null ? Instant.parse(start) : null;
            Instant endInstant = end != null ? Instant.parse(end) : null;

            SearchResult result = auditQueryService.searchAudit(
                    tenantId, auditType, action, userId,
                    startInstant, endInstant, page, size);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("hits", result.hits());
            attributes.put("totalHits", result.totalHits());
            attributes.put("page", page);
            attributes.put("size", size);

            return ResponseEntity.ok(JsonApiResponseBuilder.single("audit-entries", "search", attributes));
        } catch (Exception e) {
            log.error("Failed to search audit events: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    JsonApiResponseBuilder.error("500", "Internal Server Error",
                            "Failed to search audit events"));
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeCapturedData(Map<String, Object> span, Map<String, Object> captured) {
        Map<String, Object> tagMap = (Map<String, Object>) span.get("tagMap");
        if (tagMap == null) {
            tagMap = new LinkedHashMap<>();
            span.put("tagMap", tagMap);
        }
        if (captured.get("request_body") != null) {
            tagMap.put("http.request.body", captured.get("request_body"));
        }
        if (captured.get("response_body") != null) {
            tagMap.put("http.response.body", captured.get("response_body"));
        }
        // Request/response headers from JSONB come back as Map or String
        Object reqHeaders = captured.get("request_headers");
        if (reqHeaders instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> entry : headers.entrySet()) {
                tagMap.put("http.request.header." + entry.getKey().toString().toLowerCase(),
                        entry.getValue());
            }
        }
        Object respHeaders = captured.get("response_headers");
        if (respHeaders instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> entry : headers.entrySet()) {
                tagMap.put("http.response.header." + entry.getKey().toString().toLowerCase(),
                        entry.getValue());
            }
        }
    }
}
