package io.kelta.worker.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures HTTP request/response data (headers, bodies) and writes to PostgreSQL.
 */
@Service
public class RequestDataCaptureService {

    private static final Logger log = LoggerFactory.getLogger(RequestDataCaptureService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public RequestDataCaptureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Async
    public void captureRequestData(String traceId, String spanId,
                                   Map<String, String> requestHeaders,
                                   Map<String, String> responseHeaders,
                                   String requestBody, String responseBody,
                                   String method, String path, int statusCode,
                                   String tenantId, String userId, String userEmail,
                                   String correlationId) {
        try {
            String id = UUID.randomUUID().toString();
            String reqHeadersJson = requestHeaders != null ? OBJECT_MAPPER.writeValueAsString(requestHeaders) : null;
            String respHeadersJson = responseHeaders != null ? OBJECT_MAPPER.writeValueAsString(responseHeaders) : null;

            jdbcTemplate.update(
                    "INSERT INTO request_data (id, tenant_id, trace_id, span_id, method, path, status_code, " +
                            "user_id, user_email, correlation_id, request_headers, response_headers, " +
                            "request_body, response_body) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
                    id, tenantId, traceId, spanId, method, path, statusCode,
                    userId, userEmail, correlationId,
                    reqHeadersJson, respHeadersJson,
                    requestBody, responseBody);
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
