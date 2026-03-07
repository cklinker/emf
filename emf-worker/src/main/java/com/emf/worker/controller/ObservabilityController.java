package com.emf.worker.controller;

import com.emf.jsonapi.JsonApiResponseBuilder;
import com.emf.worker.service.OpenSearchQueryService;
import com.emf.worker.service.OpenSearchQueryService.SearchResult;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for observability data — request logs, application logs, and audit events.
 * All data is queried from OpenSearch.
 */
@RestController
@RequestMapping("/api/admin/observability")
public class ObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityController.class);

    private final OpenSearchQueryService queryService;

    public ObservabilityController(OpenSearchQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Search request logs (trace spans).
     */
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
            if (method != null) filters.put("tag.http.method", method);
            if (status != null) filters.put("tag.http.status_code", status);
            if (path != null) filters.put("tag.http.route", path);
            if (traceId != null) filters.put("traceID", traceId);
            if (userId != null) filters.put("tag.emf.user.id", userId);

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

    /**
     * Get a single request log detail by trace ID.
     */
    @GetMapping("/request-logs/{traceId}")
    public ResponseEntity<Map<String, Object>> getRequestLog(
            @PathVariable String traceId) {
        try {
            Map<String, String> filters = Map.of("traceID", traceId);
            SearchResult result = queryService.searchTraceSpans(null,
                    Instant.now().minusSeconds(86400 * 30), Instant.now(),
                    filters, 0, 100);

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

    /**
     * Search application logs.
     */
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

            // Add time range filter
            if (start != null) filters.put("@timestamp_gte", start);
            if (end != null) filters.put("@timestamp_lte", end);

            SearchResult result = queryService.search("emf-logs-*", filters, page, size,
                    "@timestamp", SortOrder.DESC);

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

    /**
     * Search audit events (setup, security, login).
     */
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
            Map<String, String> filters = new HashMap<>();
            if (tenantId != null) filters.put("tenant_id", tenantId);
            if (auditType != null) filters.put("audit_type", auditType);
            if (action != null) filters.put("action", action);
            if (userId != null) filters.put("user_id", userId);

            SearchResult result = queryService.search("emf-audit-*", filters, page, size,
                    "@timestamp", SortOrder.DESC);

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
}
