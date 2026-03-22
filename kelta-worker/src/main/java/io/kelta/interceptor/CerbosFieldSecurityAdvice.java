package io.kelta.worker.interceptor;

import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
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
 * Strips fields from API responses based on Cerbos field-level security.
 *
 * <p>For each field in each record, checks Cerbos for "read" access.
 * Fields denied by Cerbos are removed from the response attributes.
 *
 * <p>Runs after {@link CerbosRecordAuthorizationAdvice} (higher order = later).
 */
@ControllerAdvice
@Order(10) // After CerbosRecordAuthorizationAdvice (default order 0)
public class CerbosFieldSecurityAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(CerbosFieldSecurityAdvice.class);

    private final CerbosAuthorizationService authzService;
    private final CerbosPermissionResolver permissionResolver;
    private final boolean permissionsEnabled;

    public CerbosFieldSecurityAdvice(
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

        if (data instanceof List<?> records) {
            // Collect all unique field IDs across all records, then check once.
            // Field permissions depend only on profile + collection, not record data,
            // so one Cerbos call covers the entire response.
            Set<String> allFieldIds = new LinkedHashSet<>();
            List<Map<String, Object>> typedRecords = new ArrayList<>();
            for (Object record : records) {
                if (record instanceof Map<?, ?> recordMap) {
                    Map<String, Object> typed = (Map<String, Object>) recordMap;
                    typedRecords.add(typed);
                    Object attrObj = typed.get("attributes");
                    if (attrObj instanceof Map<?, ?> attrMap) {
                        for (Object key : attrMap.keySet()) {
                            if (key instanceof String fieldId && !isSystemField(fieldId)) {
                                allFieldIds.add(fieldId);
                            }
                        }
                    }
                }
            }

            if (!allFieldIds.isEmpty()) {
                List<String> allowedFields = authzService.batchCheckFieldAccess(
                        email, profileId, tenantId, collectionId,
                        new ArrayList<>(allFieldIds), "read");
                Set<String> allowedSet = new HashSet<>(allowedFields);

                for (Map<String, Object> typedRecord : typedRecords) {
                    stripFieldsUsing(allowedSet, typedRecord);
                }
            }
        } else if (data instanceof Map<?, ?> singleRecord) {
            stripHiddenFields(email, profileId, tenantId, collectionId,
                    (Map<String, Object>) singleRecord);
        }

        // Also process included resources — group by type for one check per type
        Object included = responseBody.get("included");
        if (included instanceof List<?> includedList) {
            Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
            Map<String, Set<String>> fieldsByType = new LinkedHashMap<>();

            for (Object item : includedList) {
                if (item instanceof Map<?, ?> includedRecord) {
                    Map<String, Object> typed = (Map<String, Object>) includedRecord;
                    String includedType = (String) typed.get("type");
                    if (includedType != null) {
                        byType.computeIfAbsent(includedType, k -> new ArrayList<>()).add(typed);
                        Object attrObj = typed.get("attributes");
                        if (attrObj instanceof Map<?, ?> attrMap) {
                            Set<String> fields = fieldsByType.computeIfAbsent(includedType, k -> new LinkedHashSet<>());
                            for (Object key : attrMap.keySet()) {
                                if (key instanceof String fieldId && !isSystemField(fieldId)) {
                                    fields.add(fieldId);
                                }
                            }
                        }
                    }
                }
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : byType.entrySet()) {
                String type = entry.getKey();
                Set<String> fields = fieldsByType.getOrDefault(type, Set.of());
                if (!fields.isEmpty()) {
                    List<String> allowed = authzService.batchCheckFieldAccess(
                            email, profileId, tenantId, type,
                            new ArrayList<>(fields), "read");
                    Set<String> allowedSet = new HashSet<>(allowed);
                    for (Map<String, Object> rec : entry.getValue()) {
                        stripFieldsUsing(allowedSet, rec);
                    }
                }
            }
        }

        return responseBody;
    }

    @SuppressWarnings("unchecked")
    private void stripHiddenFields(String email, String profileId, String tenantId,
                                    String collectionId, Map<String, Object> record) {
        Object attrObj = record.get("attributes");
        if (!(attrObj instanceof Map<?, ?> attrMap)) {
            return;
        }

        Map<String, Object> attributes = (Map<String, Object>) attrMap;

        // Collect non-system fields to check
        List<String> fieldsToCheck = attributes.keySet().stream()
                .filter(fieldId -> !isSystemField(fieldId))
                .toList();

        if (fieldsToCheck.isEmpty()) {
            return;
        }

        // Single batched Cerbos call for all fields
        List<String> allowedFields = authzService.batchCheckFieldAccess(
                email, profileId, tenantId, collectionId, fieldsToCheck, "read");

        Set<String> allowedSet = new HashSet<>(allowedFields);
        List<String> fieldsToRemove = fieldsToCheck.stream()
                .filter(f -> !allowedSet.contains(f))
                .toList();

        if (!fieldsToRemove.isEmpty()) {
            log.debug("Stripping {} hidden fields from {} for user={}",
                    fieldsToRemove.size(), collectionId, email);
            for (String field : fieldsToRemove) {
                attributes.remove(field);
            }
        }
    }

    /**
     * Strips fields from a record using a pre-computed allowed set.
     * Used for list responses where field access is checked once for all records.
     */
    @SuppressWarnings("unchecked")
    private void stripFieldsUsing(Set<String> allowedFields, Map<String, Object> record) {
        Object attrObj = record.get("attributes");
        if (!(attrObj instanceof Map<?, ?> attrMap)) {
            return;
        }

        Map<String, Object> attributes = (Map<String, Object>) attrMap;
        List<String> fieldsToRemove = attributes.keySet().stream()
                .filter(f -> !isSystemField(f) && !allowedFields.contains(f))
                .toList();

        if (!fieldsToRemove.isEmpty()) {
            for (String field : fieldsToRemove) {
                attributes.remove(field);
            }
        }
    }

    private boolean isSystemField(String fieldId) {
        return "createdAt".equals(fieldId) || "updatedAt".equals(fieldId)
                || "createdBy".equals(fieldId) || "updatedBy".equals(fieldId);
    }

    /**
     * Checks if the path is a platform metadata endpoint (collections, profiles, etc.)
     * that should not have field-level security applied. These endpoints return
     * collection definitions and system configuration, not user records.
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
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "";
    }
}
