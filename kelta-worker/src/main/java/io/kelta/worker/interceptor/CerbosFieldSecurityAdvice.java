package io.kelta.worker.interceptor;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.FieldMaskingService;
import io.kelta.worker.service.RecordMaskingService;
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
 * Strips and masks fields on API responses based on Cerbos field-level security.
 *
 * <p>For each field in each record, checks Cerbos for "read" access.
 * Fields denied by Cerbos are removed from the response attributes. Fields that
 * survive the read check but carry a masking policy ({@code fieldTypeConfig.masking})
 * are then redacted for users whose profile denies the {@code unmask} action —
 * strip first, mask second, so a HIDDEN field is never masked into existence.
 * Masked field names are surfaced per record in {@code meta.maskedFields}.
 *
 * <p>Runs after {@link CerbosRecordAuthorizationAdvice} (higher order = later).
 */
@ControllerAdvice
@Order(10) // After CerbosRecordAuthorizationAdvice (default order 0)
public class CerbosFieldSecurityAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(CerbosFieldSecurityAdvice.class);

    private final CerbosAuthorizationService authzService;
    private final CerbosPermissionResolver permissionResolver;
    private final CollectionRegistry collectionRegistry;
    private final RecordMaskingService recordMaskingService;
    private final FieldMaskingService fieldMaskingService;
    private final boolean permissionsEnabled;

    public CerbosFieldSecurityAdvice(
            CerbosAuthorizationService authzService,
            CerbosPermissionResolver permissionResolver,
            CollectionRegistry collectionRegistry,
            RecordMaskingService recordMaskingService,
            FieldMaskingService fieldMaskingService,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled) {
        this.authzService = authzService;
        this.permissionResolver = permissionResolver;
        this.collectionRegistry = collectionRegistry;
        this.recordMaskingService = recordMaskingService;
        this.fieldMaskingService = fieldMaskingService;
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
        String fallbackCollectionId = extractCollectionId(path);

        // Primary data: group by each record's own JSON:API `type` — NOT the path
        // segment. On sub-resource routes (`/api/{parent}/{id}/{child}`) the records
        // are the child type, so keying strip/mask off the path would check the
        // parent's field permissions and leak the child's HIDDEN/MASKED fields. The
        // `type` is the collection API name that both the Cerbos CEL and the
        // CollectionRegistry key on. Falls back to the path id only when a record
        // carries no `type` (non-resource payloads).
        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
        if (data instanceof List<?> records) {
            for (Object record : records) {
                addByType(byType, record, fallbackCollectionId);
            }
        } else if (data instanceof Map<?, ?> singleRecord) {
            addByType(byType, singleRecord, fallbackCollectionId);
        }

        // Included resources — JSON:API flattens every included resource (at any
        // relationship depth) into this one array; grouping by type folds them into
        // the same per-type checks as the primary data.
        Object included = responseBody.get("included");
        if (included instanceof List<?> includedList) {
            for (Object item : includedList) {
                addByType(byType, item, null);
            }
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : byType.entrySet()) {
            stripCollection(email, profileId, tenantId, entry.getKey(), entry.getValue());
        }

        return responseBody;
    }

    /**
     * Adds a JSON:API resource object to the by-type group keyed on its {@code type};
     * when {@code type} is absent, uses {@code fallbackType} (the path-derived
     * collection id) if provided, else skips it (a typeless included resource has no
     * collection to check against).
     */
    @SuppressWarnings("unchecked")
    private void addByType(Map<String, List<Map<String, Object>>> byType, Object record,
                            String fallbackType) {
        if (!(record instanceof Map<?, ?> recordMap)) {
            return;
        }
        Map<String, Object> typed = (Map<String, Object>) recordMap;
        Object typeObj = typed.get("type");
        String type = typeObj instanceof String s ? s : fallbackType;
        if (type != null) {
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(typed);
        }
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

        maskCollection(email, profileId, tenantId, collectionId, records, allowedSet);
    }

    /**
     * Redacts masking-configured fields the user may not unmask. Runs strictly
     * after {@link #stripDenied} so a HIDDEN field is never masked into
     * existence. Collections with no masking config short-circuit before any
     * authorization call — the extra Cerbos check only happens where an admin
     * configured masking.
     */
    private void maskCollection(String email, String profileId, String tenantId,
                                 String collectionId, List<Map<String, Object>> records,
                                 Set<String> readAllowed) {
        CollectionDefinition definition = collectionRegistry.get(collectionId);
        if (definition == null) {
            return; // unknown type (e.g. non-collection included resource) — nothing to mask
        }

        Map<String, FieldMaskingService.MaskingConfig> configs =
                recordMaskingService.maskableConfigs(definition);
        if (configs.isEmpty()) {
            return;
        }

        // Only fields that survived the read strip can need masking.
        Set<String> maskable = new LinkedHashSet<>(configs.keySet());
        maskable.retainAll(readAllowed);
        if (maskable.isEmpty()) {
            return;
        }

        Set<String> masked = recordMaskingService.maskedFieldsFor(
                email, profileId, tenantId, collectionId, maskable);
        if (masked.isEmpty()) {
            return;
        }

        for (Map<String, Object> record : records) {
            maskRecord(record, masked, configs);
        }
    }

    /**
     * Replaces each masked field's attribute value with its redacted form and
     * records the affected field names in the record's {@code meta.maskedFields}
     * so clients can render lock state without sniffing placeholder strings.
     * Null values stay null (null-ness is not treated as sensitive).
     */
    @SuppressWarnings("unchecked")
    private void maskRecord(Map<String, Object> record, Set<String> masked,
                             Map<String, FieldMaskingService.MaskingConfig> configs) {
        Object attrObj = record.get("attributes");
        if (!(attrObj instanceof Map<?, ?> attrMapRaw)) {
            return;
        }
        Map<String, Object> attributes = (Map<String, Object>) attrMapRaw;

        List<String> maskedPresent = new ArrayList<>();
        for (String fieldId : masked) {
            if (!attributes.containsKey(fieldId)) {
                continue;
            }
            Object value = attributes.get(fieldId);
            if (value != null) {
                attributes.put(fieldId, fieldMaskingService.mask(
                        String.valueOf(value), configs.get(fieldId)));
            }
            maskedPresent.add(fieldId);
        }
        if (maskedPresent.isEmpty()) {
            return;
        }
        Collections.sort(maskedPresent);

        Object metaObj = record.get("meta");
        Map<String, Object> meta;
        if (metaObj instanceof Map<?, ?> metaMap) {
            meta = (Map<String, Object>) metaMap;
        } else {
            meta = new LinkedHashMap<>();
            record.put("meta", meta);
        }
        meta.put("maskedFields", maskedPresent);
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
        return ApiPaths.isMetadataPath(path);
    }

    private String extractCollectionId(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "";
    }
}
