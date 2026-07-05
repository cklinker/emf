package io.kelta.worker.interceptor;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.CollectionLifecycleManager;
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
 * Read-side field-level security + data masking for {@code /api/field-history}.
 *
 * <p>The generic {@link CerbosFieldSecurityAdvice} keys off each row's own JSON:API {@code type}
 * ({@code field-history}), so it protects the field-history collection's own fields — it does
 * <em>not</em> know that a row's {@code oldValue}/{@code newValue} carry the historical value of
 * the <em>referenced</em> collection's field, which may itself be HIDDEN or MASKED for the caller.
 * Without this guard, enabling {@code trackHistory} on a masked/restricted field would leak its
 * cleartext through the history feed.
 *
 * <p>For each history row this advice resolves the referenced collection + field and:
 * <ul>
 *   <li>drops the row entirely when the caller lacks {@code read} on that field (HIDDEN parity);</li>
 *   <li>redacts {@code oldValue}/{@code newValue} through {@link FieldMaskingService} when the field
 *       is MASKED for the caller, flagging {@code meta.maskedFields}.</li>
 * </ul>
 *
 * <p>Fail-closed: rows whose referenced collection can't be resolved are dropped. Row removal makes
 * a page's returned count a lower bound on the raw match count — acceptable for an audit feed.
 * Runs after {@link CerbosFieldSecurityAdvice} (higher order = later).
 */
@ControllerAdvice
@Order(20)
public class FieldHistorySecurityAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(FieldHistorySecurityAdvice.class);
    private static final String PATH_PREFIX = "/api/field-history";
    private static final String READ_ACTION = "read";

    private final CerbosAuthorizationService authzService;
    private final CerbosPermissionResolver permissionResolver;
    private final CollectionRegistry collectionRegistry;
    private final CollectionLifecycleManager lifecycleManager;
    private final RecordMaskingService recordMaskingService;
    private final FieldMaskingService fieldMaskingService;
    private final boolean permissionsEnabled;

    public FieldHistorySecurityAdvice(
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
        } else {
            // Single-record response: redact in place; a fully-denied field nulls its values.
            guard.apply(data);
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

        /** Returns false when the row must be dropped; redacts masked values in place otherwise. */
        @SuppressWarnings("unchecked")
        boolean apply(Object row) {
            if (!(row instanceof Map)) {
                return true;
            }
            Object attrObj = ((Map<String, Object>) row).get("attributes");
            if (!(attrObj instanceof Map)) {
                return true;
            }
            Map<String, Object> attrs = (Map<String, Object>) attrObj;
            String collectionId = asString(attrs.get("collectionId"));
            String fieldName = asString(attrs.get("fieldName"));
            if (collectionId == null || fieldName == null) {
                return false; // fail-closed: can't identify the referenced field
            }
            Decision decision = byCollectionId.computeIfAbsent(collectionId, this::resolve);
            if (decision == null || !decision.readable.contains(fieldName)) {
                return false; // referenced collection unresolved, or field not readable → drop
            }
            if (decision.masked.contains(fieldName)) {
                redact(attrs, fieldName, decision.configs.get(fieldName));
                markMasked((Map<String, Object>) row, fieldName);
            }
            return true;
        }

        private Decision resolve(String collectionId) {
            String collectionName = lifecycleManager.getCollectionNameById(collectionId);
            if (collectionName == null) {
                return null;
            }
            CollectionDefinition definition = collectionRegistry.get(collectionName);
            // Fields that exist on the collection today are the only ones we can decide on.
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

        private void redact(Map<String, Object> attrs, String fieldName,
                            FieldMaskingService.MaskingConfig config) {
            attrs.put("oldValue", maskValue(attrs.get("oldValue"), config));
            attrs.put("newValue", maskValue(attrs.get("newValue"), config));
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
