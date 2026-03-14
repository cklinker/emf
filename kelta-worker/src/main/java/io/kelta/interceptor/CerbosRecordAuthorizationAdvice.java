package io.kelta.worker.interceptor;

import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters records from API responses based on Cerbos record-level authorization.
 *
 * <p>For each record in a JSON:API response, checks Cerbos with all record
 * attributes. Records denied by Cerbos are removed from the response.
 *
 * <p>Only applies to {@code /api/} paths when permissions are enabled.
 */
@ControllerAdvice
public class CerbosRecordAuthorizationAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(CerbosRecordAuthorizationAdvice.class);

    private final CerbosAuthorizationService authzService;
    private final CerbosPermissionResolver permissionResolver;
    private final boolean permissionsEnabled;

    public CerbosRecordAuthorizationAdvice(
            CerbosAuthorizationService authzService,
            CerbosPermissionResolver permissionResolver,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled) {
        this.authzService = authzService;
        this.permissionResolver = permissionResolver;
        this.permissionsEnabled = permissionsEnabled;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return permissionsEnabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String path = httpRequest.getRequestURI();

        // Only apply to collection API paths (user record data, not metadata)
        if (!path.startsWith("/api/") || path.startsWith("/api/admin/") || path.startsWith("/api/me/")
                || isMetadataPath(path)) {
            return body;
        }

        if (!permissionResolver.hasIdentity(httpRequest)) {
            return body;
        }

        if (!(body instanceof Map)) {
            return body;
        }

        Map<String, Object> responseBody = (Map<String, Object>) body;
        Object data = responseBody.get("data");
        if (data == null) {
            return body;
        }

        String email = permissionResolver.getEmail(httpRequest);
        String profileId = permissionResolver.getProfileId(httpRequest);
        String tenantId = permissionResolver.getTenantId(httpRequest);
        String collectionId = extractCollectionId(path);
        String action = mapMethodToAction(httpRequest.getMethod());

        if (data instanceof List<?> records) {
            // Collect all records for a single batched Cerbos call
            List<Map<String, Object>> typedRecords = new ArrayList<>();
            for (Object record : records) {
                if (record instanceof Map<?, ?> recordMap) {
                    typedRecords.add((Map<String, Object>) recordMap);
                }
            }

            Set<String> allowedIds = authzService.batchCheckRecordAccess(
                    email, profileId, tenantId, collectionId, typedRecords, action);

            List<Map<String, Object>> filtered = typedRecords.stream()
                    .filter(r -> {
                        String id = (String) r.get("id");
                        return id == null || allowedIds.contains(id);
                    })
                    .toList();

            int removed = typedRecords.size() - filtered.size();
            if (removed > 0) {
                log.debug("Filtered {} records from response for user={} collection={}",
                        removed, email, collectionId);
            }
            responseBody.put("data", filtered);
        } else if (data instanceof Map<?, ?> singleRecord) {
            Map<String, Object> typedRecord = (Map<String, Object>) singleRecord;
            if (!isRecordAllowed(email, profileId, tenantId, collectionId, typedRecord, action)) {
                log.debug("Denied single record for user={} collection={}", email, collectionId);
                responseBody.put("data", null);
            }
        }

        return responseBody;
    }

    @SuppressWarnings("unchecked")
    private boolean isRecordAllowed(String email, String profileId, String tenantId,
                                     String collectionId, Map<String, Object> record, String action) {
        String recordId = (String) record.get("id");
        if (recordId == null) {
            return true;
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        Object attrObj = record.get("attributes");
        if (attrObj instanceof Map<?, ?> attrMap) {
            attributes.putAll((Map<String, Object>) attrMap);
        }

        return authzService.checkRecordAccess(email, profileId, tenantId,
                collectionId, recordId, attributes, action);
    }

    /**
     * Checks if the path is a platform metadata endpoint that should not have
     * record-level authorization applied.
     */
    private boolean isMetadataPath(String path) {
        return path.startsWith("/api/collections")
                || path.startsWith("/api/profiles")
                || path.startsWith("/api/security-audit-logs")
                || path.startsWith("/api/plugins")
                || path.startsWith("/api/oidc")
                || path.startsWith("/api/tenants")
                || path.startsWith("/api/metrics")
                || path.startsWith("/api/flows");
    }

    private String extractCollectionId(String path) {
        // Path format: /api/{collectionName} or /api/{collectionName}/{id}
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "";
    }

    private String mapMethodToAction(String method) {
        if (method == null) return "read";
        return switch (method) {
            case "POST" -> "create";
            case "PUT", "PATCH" -> "edit";
            case "DELETE" -> "delete";
            default -> "read";
        };
    }
}
