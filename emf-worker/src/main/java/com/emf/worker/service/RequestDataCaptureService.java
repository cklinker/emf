package com.emf.worker.service;

import io.opentelemetry.api.trace.Span;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Captures HTTP request/response data (headers, bodies) and writes directly
 * to OpenSearch. This bypasses the OTEL span attribute mechanism which silently
 * drops setAttribute() calls when trace context is propagated from upstream services.
 */
@Service
public class RequestDataCaptureService {

    private static final Logger log = LoggerFactory.getLogger(RequestDataCaptureService.class);
    private static final DateTimeFormatter INDEX_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    private final RestHighLevelClient client;

    public RequestDataCaptureService(RestHighLevelClient client) {
        this.client = client;
    }

    /**
     * Asynchronously indexes request/response data to OpenSearch.
     */
    @Async
    public void captureRequestData(String traceId, String spanId,
                                   Map<String, String> requestHeaders,
                                   Map<String, String> responseHeaders,
                                   String requestBody, String responseBody,
                                   String method, String path, int statusCode,
                                   String tenantId, String userId, String userEmail,
                                   String correlationId) {
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("@timestamp", Instant.now().toString());
            doc.put("traceId", traceId);
            doc.put("spanId", spanId);
            doc.put("method", method);
            doc.put("path", path);
            doc.put("statusCode", statusCode);
            doc.put("tenantId", tenantId);
            doc.put("userId", userId);
            doc.put("userEmail", userEmail);
            doc.put("correlationId", correlationId);
            doc.put("requestHeaders", requestHeaders);
            doc.put("responseHeaders", responseHeaders);
            doc.put("requestBody", requestBody);
            doc.put("responseBody", responseBody);

            String index = "emf-request-data-" + INDEX_DATE_FORMAT.format(Instant.now());
            IndexRequest request = new IndexRequest(index)
                    .id(traceId + "-" + spanId)
                    .source(doc, XContentType.JSON);
            client.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.warn("Failed to capture request data for trace {}: {}", traceId, e.getMessage());
        }
    }

    /**
     * Extracts request headers as a map, filtering out sensitive headers.
     */
    public static Map<String, String> extractRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String lower = name.toLowerCase();
                // Skip sensitive headers
                if (lower.equals("authorization") || lower.equals("cookie") ||
                        lower.equals("x-api-key") || lower.equals("set-cookie")) {
                    headers.put(name, "[REDACTED]");
                } else {
                    headers.put(name, request.getHeader(name));
                }
            }
        }
        return headers;
    }

    /**
     * Extracts response headers as a map.
     */
    public static Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            String lower = name.toLowerCase();
            if (lower.equals("set-cookie")) {
                headers.put(name, "[REDACTED]");
            } else {
                headers.put(name, response.getHeader(name));
            }
        }
        return headers;
    }
}
