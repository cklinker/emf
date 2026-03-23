package io.kelta.worker.service;

import io.opentelemetry.api.trace.Span;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified audit writer that indexes audit events to OpenSearch.
 * Replaces direct PostgreSQL writes for audit data.
 * All operations are async and non-fatal.
 */
@Service
public class OpenSearchAuditService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchAuditService.class);
    private static final DateTimeFormatter INDEX_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    private final RestHighLevelClient client;

    public OpenSearchAuditService(RestHighLevelClient client) {
        this.client = client;
    }

    /**
     * Logs a setup audit event (collection/field/permission changes).
     */
    @Async
    public void logSetupAudit(String tenantId, String userId, String action, String section,
                              String entityType, String entityId, String entityName,
                              String oldValue, String newValue) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("audit_type", "setup");
        doc.put("tenant_id", tenantId);
        doc.put("user_id", userId);
        doc.put("action", action);
        doc.put("section", section);
        doc.put("entity_type", entityType);
        doc.put("entity_id", entityId);
        doc.put("entity_name", entityName);
        doc.put("old_value", oldValue);
        doc.put("new_value", newValue);
        addCommonFields(doc);
        indexDocument("kelta-audit", doc);
    }

    /**
     * Logs a security audit event (login, permission changes, etc.).
     */
    @Async
    public void logSecurityAudit(String tenantId, String userId, String eventType,
                                 String eventCategory, String ipAddress, String details) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("audit_type", "security");
        doc.put("tenant_id", tenantId);
        doc.put("user_id", userId);
        doc.put("event_type", eventType);
        doc.put("event_category", eventCategory);
        doc.put("ip_address", ipAddress);
        doc.put("details", details);
        addCommonFields(doc);
        indexDocument("kelta-audit", doc);
    }

    /**
     * Logs a login history event.
     */
    @Async
    public void logLoginHistory(String tenantId, String userId, String email,
                                String loginType, String status, String ipAddress) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("audit_type", "login");
        doc.put("tenant_id", tenantId);
        doc.put("user_id", userId);
        doc.put("email", email);
        doc.put("login_type", loginType);
        doc.put("status", status);
        doc.put("ip_address", ipAddress);
        addCommonFields(doc);
        indexDocument("kelta-audit", doc);
    }

    private void addCommonFields(Map<String, Object> doc) {
        Instant now = Instant.now();
        doc.put("@timestamp", now.toString());
        doc.put("id", UUID.randomUUID().toString());

        // Add trace context for correlation
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            doc.put("trace_id", span.getSpanContext().getTraceId());
            doc.put("span_id", span.getSpanContext().getSpanId());
        }

        // Add MDC context
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            doc.put("correlation_id", correlationId);
        }
    }

    private void indexDocument(String indexPrefix, Map<String, Object> doc) {
        try {
            String index = indexPrefix + "-" + INDEX_DATE_FORMAT.format(Instant.now());
            IndexRequest request = new IndexRequest(index)
                    .id((String) doc.get("id"))
                    .source(doc, XContentType.JSON);
            client.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.warn("Failed to index audit document: {}", e.getMessage());
        }
    }
}
