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

        // Primary data: one batched Cerbos check per collection, then strip denied
        // fields from both attributes and to-one relationship linkage.
        if (data instanceof List<?> records) {
            stripCollection(email, profileId, tenantId, collectionId, toTypedRecords(records));
        } else if (data instanceof Map<?, ?> singleRecord) {
            stripCollection(email, profileId, tenantId, collectionId,
                    List.of((Map<String, Object>) singleRecord));
        }

        // Included resources — JSON:API flattens every included resource (at any
        // relationship depth) into this one array, so grouping by type and checking
        // once per type covers all of them.
        Object included = responseBody.get("included");
        if (included instanceof List<?> includedList) {
            Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
            for (Object item : includedList) {
                if (item instanceof Map<?, ?> includedRecord) {
                    Map<String, Object> typed = (Map<String, Object>) includedRecord;
                    String includedType = (String) typed.get("type");
                    if (includedType != null) {
                        byType.computeIfAbsent(includedType, k -> new ArrayList<>()).add(typed);
                    }
                }
            }
            for (Map.Entry<String, List<Map<String, Object>>> entry : byType.entrySet()) {
                stripCollection(email, profileId, tenantId, entry.getKey(), entry.getValue());
            }
        }

        return responseBody;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toTypedRecords(List<?> records) {
        List<Map<String, Object>> typed = new ArrayList<>();
        for (Object record : records) {
            if (record instanceof Map<?, ?> recordMap) {
                typed.add((Map<String, Object>) recordMap);
            }
        }
        return typed;
    }

    /**
     * Checks read access for every field that appears across {@code records} (one batched
     * Cerbos call, since field permissions depend only on profile + collection, not record
     * data) and removes denied fields from each record's {@code attributes} and to-one
     * {@code relationships} linkage.
     */
    private void stripCollection(String email, String profileId, String tenantId,
                                  String collectionId, List<Map<String, Object>> records) {
        Set<String> fieldIds = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            collectCheckableFieldIds(record, fieldIds);
        }
        if (fieldIds.isEmpty()) {
            return;
        }

        List<String> allowed = authzService.batchCheckFieldAccess(
                email, profileId, tenantId, collectionId, new ArrayList<>(fieldIds), "read");
        Set<String> allowedSet = new HashSet<>(allowed);

        if (log.isDebugEnabled() && allowedSet.size() < fieldIds.size()) {
            log.debug("Field security: {}/{} fields allowed for {} (user={})",
                    allowedSet.size(), fieldIds.size(), collectionId, email);
        }

        for (Map<String, Object> record : records) {
            stripDenied(allowedSet, record);
        }
    }

    /**
     * Collects the non-system field ids that are subject to a field-access check: every key in
     * {@code attributes}, plus every <em>to-one</em> relationship key (lookup / master-detail
     * fields are serialized into {@code relationships}, not {@code attributes}). Has-many inverse
     * relationships (added by {@code ?include=}) are not collection fields and are left alone.
     */
    private void collectCheckableFieldIds(Map<String, Object> record, Set<String> out) {
        Object attrObj = record.get("attributes");
        if (attrObj instanceof Map<?, ?> attrMap) {
            for (Object key : attrMap.keySet()) {
                if (key instanceof String fieldId && !isSystemField(fieldId)) {
                    out.add(fieldId);
                }
            }
        }
        Object relObj = record.get("relationships");
        if (relObj instanceof Map<?, ?> relMap) {
            for (Map.Entry<?, ?> entry : relMap.entrySet()) {
                if (entry.getKey() instanceof String fieldId && !isSystemField(fieldId)
                        && isToOneRelationship(entry.getValue())) {
                    out.add(fieldId);
                }
            }
        }
    }

    /**
     * Removes every denied (non-system) field from a record's {@code attributes} and its to-one
     * {@code relationships} linkage. A hidden lookup / master-detail field would otherwise leak
     * the related record's id through {@code relationships}, since the router serializes
     * relationship fields there rather than in {@code attributes}.
     */
    @SuppressWarnings("unchecked")
    private void stripDenied(Set<String> allowedFields, Map<String, Object> record) {
        Object attrObj = record.get("attributes");
        if (attrObj instanceof Map<?, ?> attrMap) {
            ((Map<String, Object>) attrMap).keySet().removeIf(
                    key -> key instanceof String f && !isSystemField(f) && !allowedFields.contains(f));
        }

        Object relObj = record.get("relationships");
        if (relObj instanceof Map<?, ?> relMapRaw) {
            Map<String, Object> relationships = (Map<String, Object>) relMapRaw;
            relationships.entrySet().removeIf(entry ->
                    entry.getKey() instanceof String f
                            && !isSystemField(f)
                            && isToOneRelationship(entry.getValue())
                            && !allowedFields.contains(f));
            if (relationships.isEmpty()) {
                record.remove("relationships");
            }
        }
    }

    /**
     * A to-one relationship (lookup / master-detail field) is serialized as
     * {@code {"data": {"type": ..., "id": ...}}}, or {@code {"data": null}} for an empty FK.
     * Has-many inverse relationships serialize {@code data} as a list and must not be treated
     * as collection fields.
     */
    private boolean isToOneRelationship(Object relValue) {
        if (!(relValue instanceof Map<?, ?> relMap) || !relMap.containsKey("data")) {
            return false;
        }
        Object relData = relMap.get("data");
        return relData == null || relData instanceof Map;
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
