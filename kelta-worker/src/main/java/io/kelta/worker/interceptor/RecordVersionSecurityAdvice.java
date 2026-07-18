package io.kelta.worker.interceptor;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.CollectionLifecycleManager;
import io.kelta.worker.service.FieldMaskingService;
import io.kelta.worker.service.RecordMaskingService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-side field-level security + data masking for {@code /api/record-versions}.
 *
 * <p>A version row's {@code snapshot} carries every field value of a record in the
 * <em>referenced</em> collection, so the generic {@link CerbosFieldSecurityAdvice} (which keys
 * off the row's own {@code record-versions} type) cannot protect it. Mirrors
 * {@link FieldHistorySecurityAdvice}: for each row the referenced collection is resolved and
 * <ul>
 *   <li>snapshot keys the caller cannot {@code read} are removed, and those field names are
 *       also removed from {@code changedFields} (the fact a hidden field changed is itself
 *       a leak);</li>
 *   <li>snapshot keys that no longer exist on the collection are removed (fail-closed — no
 *       current field means no FLS decision is possible); identity/audit keys are kept, matching
 *       the generic advice's never-strip contract for system audit fields;</li>
 *   <li>MASKED-readable fields' snapshot values are redacted via {@link FieldMaskingService},
 *       flagging {@code meta.maskedFields}.</li>
 * </ul>
 *
 * <p>Fail-closed: rows whose referenced collection can't be resolved, or whose snapshot isn't
 * an object, are dropped. Runs after {@link CerbosFieldSecurityAdvice} (higher order = later).
 */
@ControllerAdvice
@Order(21)
public class RecordVersionSecurityAdvice implements ResponseBodyAdvice<Object> {

    private static final String PATH_PREFIX = "/api/record-versions";
    private static final String READ_ACTION = "read";

    /** Identity/audit snapshot keys always kept — parity with the generic advice's never-strip set. */
    private static final Set<String> SYSTEM_KEYS = Set.of(
            "id", "recordTypeId", "record_type_id",
            "createdAt", "created_at", "createdBy", "created_by",
            "updatedAt", "updated_at", "updatedBy", "updated_by");

    private final CerbosAuthorizationService authzService;
    private final CerbosPermissionResolver permissionResolver;
    private final CollectionRegistry collectionRegistry;
    private final CollectionLifecycleManager lifecycleManager;
    private final RecordMaskingService recordMaskingService;
    private final FieldMaskingService fieldMaskingService;
    private final boolean permissionsEnabled;

    public RecordVersionSecurityAdvice(
            CerbosAuthorizationService authzService,
            CerbosPermissionResolver permissionResolver,
            CollectionRegistry collectionRegistry,
            CollectionLifecycleManager lifecycleManager,
            RecordMaskingService recordMaskingService,
            FieldMaskingService fieldMaskingService,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled) {
        this.authzService = authzService;
        this.permissionResolver = permissionResolver;
        this.collectionRegistry = collectionRegistry;
        this.lifecycleManager = lifecycleManager;
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
        if (!httpRequest.getRequestURI().startsWith(PATH_PREFIX)) {
            return body;
        }
        if (!permissionResolver.hasIdentity(httpRequest) || !(body instanceof Map)) {
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

        Guard guard = new Guard(email, profileId, tenantId);
        if (data instanceof List<?>) {
            List<Object> rows = (List<Object>) data;
            Iterator<Object> it = rows.iterator();
            while (it.hasNext()) {
                if (!guard.apply(it.next())) {
                    it.remove();
                }
            }
        } else if (!guard.apply(data)) {
            responseBody.put("data", null);
        }
        return responseBody;
    }

    /**
     * Per-request evaluator that caches the referenced-collection FLS/masking decision so a page of
     * many rows over the same collection costs one Cerbos batch, not one per row.
     */
    private final class Guard {
        private final String email;
        private final String profileId;
        private final String tenantId;
        private final Map<String, Decision> byCollectionId = new HashMap<>();

        Guard(String email, String profileId, String tenantId) {
            this.email = email;
            this.profileId = profileId;
            this.tenantId = tenantId;
        }

        /** Returns false when the row must be dropped; strips/redacts the snapshot in place otherwise. */
        @SuppressWarnings("unchecked")
        boolean apply(Object row) {
            if (!(row instanceof Map)) {
                return true;
            }
            Map<String, Object> rowMap = (Map<String, Object>) row;
            Object attrObj = rowMap.get("attributes");
            if (!(attrObj instanceof Map)) {
                return true;
            }
            Map<String, Object> attrs = (Map<String, Object>) attrObj;
            String collectionId = asString(attrs.get("collectionId"));
            if (collectionId == null) {
                return false; // fail-closed: can't identify the referenced collection
            }
            Decision decision = byCollectionId.computeIfAbsent(collectionId, this::resolve);
            if (decision == null) {
                return false; // referenced collection unresolved → drop
            }
            Object snapshotObj = attrs.get("snapshot");
            if (!(snapshotObj instanceof Map)) {
                return false; // fail-closed: snapshot must be an object to be filtered
            }
            Map<String, Object> snapshot = (Map<String, Object>) snapshotObj;

            // Rebuild rather than mutate — the deserialized JSONB map may be immutable.
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String field = entry.getKey();
                if (SYSTEM_KEYS.contains(field)) {
                    filtered.put(field, entry.getValue());
                    continue;
                }
                if (!decision.readable.contains(field)) {
                    continue; // not readable, or no longer a field of the collection
                }
                if (decision.masked.contains(field)) {
                    filtered.put(field, maskValue(entry.getValue(), decision.configs.get(field)));
                    markMasked(rowMap, field);
                } else {
                    filtered.put(field, entry.getValue());
                }
            }
            attrs.put("snapshot", filtered);

            Object changedObj = attrs.get("changedFields");
            if (changedObj instanceof List<?> changed) {
                attrs.put("changedFields", changed.stream()
                        .filter(name -> name instanceof String s && decision.readable.contains(s))
                        .toList());
            }
            return true;
        }

        private Decision resolve(String collectionId) {
            String collectionName = lifecycleManager.getCollectionNameById(collectionId);
            if (collectionName == null) {
                return null;
            }
            CollectionDefinition definition = collectionRegistry.get(collectionName);
            Set<String> candidateFields = definition == null ? Set.of()
                    : new LinkedHashSet<>(definition.getFieldNames());
            Set<String> readable = candidateFields.isEmpty() ? Set.of()
                    : new HashSet<>(authzService.batchCheckFieldAccess(
                            email, profileId, tenantId, collectionName,
                            new ArrayList<>(candidateFields), READ_ACTION));

            Map<String, FieldMaskingService.MaskingConfig> configs = definition == null
                    ? Map.of() : recordMaskingService.maskableConfigs(definition);
            Set<String> maskable = new LinkedHashSet<>(configs.keySet());
            maskable.retainAll(readable);
            Set<String> masked = maskable.isEmpty() ? Set.of()
                    : recordMaskingService.maskedFieldsFor(
                            email, profileId, tenantId, collectionName, maskable);
            return new Decision(readable, masked, configs);
        }

        private Object maskValue(Object value, FieldMaskingService.MaskingConfig config) {
            if (value == null || config == null) {
                return value;
            }
            return fieldMaskingService.mask(String.valueOf(value), config);
        }

        @SuppressWarnings("unchecked")
        private void markMasked(Map<String, Object> row, String fieldName) {
            Object metaObj = row.get("meta");
            Map<String, Object> meta;
            if (metaObj instanceof Map) {
                meta = (Map<String, Object>) metaObj;
            } else {
                meta = new LinkedHashMap<>();
                row.put("meta", meta);
            }
            Object existing = meta.get("maskedFields");
            List<String> maskedFields = existing instanceof List
                    ? (List<String>) existing : new ArrayList<>();
            if (!maskedFields.contains(fieldName)) {
                maskedFields.add(fieldName);
                Collections.sort(maskedFields);
            }
            meta.put("maskedFields", maskedFields);
        }
    }

    /** Cached per-referenced-collection outcome: which fields the caller may read, and which are masked. */
    private record Decision(Set<String> readable, Set<String> masked,
                            Map<String, FieldMaskingService.MaskingConfig> configs) {
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
