package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves and applies data masking for a user against a collection — the one
 * implementation every egress path shares. The JSON:API read advice masks
 * through this service, and non-advice serializers (data export, reports) call
 * {@link #maskRows} at their own boundary.
 *
 * <p>Cost model: collections without masking-configured fields short-circuit
 * before any authorization call, so the feature is free where unused. When
 * maskable fields exist, the {@code unmask} decision is one batched, cached
 * Cerbos check per (tenant, profile, collection).
 *
 * <p>Fail posture mirrors FLS: if Cerbos is unreachable the batched check
 * returns no allowances and every masking-configured field masks.
 */
@Service
public class RecordMaskingService {

    /** Cerbos action gating plaintext visibility of masking-configured fields. */
    public static final String UNMASK_ACTION = "unmask";

    private final CerbosAuthorizationService authzService;
    private final FieldMaskingService fieldMaskingService;

    public RecordMaskingService(CerbosAuthorizationService authzService,
                                FieldMaskingService fieldMaskingService) {
        this.authzService = authzService;
        this.fieldMaskingService = fieldMaskingService;
    }

    /**
     * Returns {@code fieldName → MaskingConfig} for every masking-configured,
     * maskable field of the collection. Empty map means nothing on this
     * collection can ever mask — callers should short-circuit on it.
     */
    public Map<String, FieldMaskingService.MaskingConfig> maskableConfigs(CollectionDefinition definition) {
        if (definition == null || definition.fields() == null || definition.fields().isEmpty()) {
            return Map.of();
        }
        Map<String, FieldMaskingService.MaskingConfig> configs = new LinkedHashMap<>();
        for (FieldDefinition field : definition.fields()) {
            fieldMaskingService.configFor(field)
                    .ifPresent(cfg -> configs.put(field.name(), cfg));
        }
        return configs;
    }

    /**
     * Of {@code maskableFields}, returns the subset the user must see masked —
     * i.e. fields whose {@code unmask} action Cerbos denies for this principal.
     */
    public Set<String> maskedFieldsFor(String email, String profileId, String tenantId,
                                       String collectionId, Set<String> maskableFields) {
        if (maskableFields.isEmpty()) {
            return Set.of();
        }
        List<String> allowed = authzService.batchCheckFieldAccess(
                email, profileId, tenantId, collectionId,
                new ArrayList<>(maskableFields), UNMASK_ACTION);
        Set<String> masked = new HashSet<>(maskableFields);
        allowed.forEach(masked::remove);
        return masked;
    }

    /**
     * Masks flat attribute rows in place for the given user. Non-advice egress
     * paths (export, reports) call this on their result rows before
     * serialization. Null values stay null; non-string values of a maskable
     * field are stringified before masking (defensive — maskable types
     * serialize as strings).
     *
     * @return the field names that were masked for this user (empty when the
     *         collection has no masking config or the user may unmask all)
     */
    public Set<String> maskRows(CollectionDefinition definition, List<Map<String, Object>> rows,
                                String email, String profileId, String tenantId) {
        Map<String, FieldMaskingService.MaskingConfig> configs = maskableConfigs(definition);
        if (configs.isEmpty() || rows == null || rows.isEmpty()) {
            return Set.of();
        }
        Set<String> masked = maskedFieldsFor(
                email, profileId, tenantId, definition.name(), configs.keySet());
        if (masked.isEmpty()) {
            return Set.of();
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            for (String fieldName : masked) {
                Object value = row.get(fieldName);
                if (value != null) {
                    row.put(fieldName, fieldMaskingService.mask(
                            String.valueOf(value), configs.get(fieldName)));
                }
            }
        }
        return masked;
    }
}
