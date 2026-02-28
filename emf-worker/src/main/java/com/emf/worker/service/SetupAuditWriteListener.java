package com.emf.worker.service;

import com.emf.runtime.router.CollectionWriteListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Listens for write operations on setup-related collections and records
 * them in the setup audit trail.
 *
 * <p>Only writes to configuration collections (schema, profiles, permissions,
 * users, OIDC, flows, etc.) are audited. User data collections (custom
 * collections managed by end users) are ignored.
 *
 * @since 1.0.0
 */
@Component
public class SetupAuditWriteListener implements CollectionWriteListener {

    private static final Logger log = LoggerFactory.getLogger(SetupAuditWriteListener.class);

    /**
     * Maps collection names to their setup section for audit purposes.
     */
    private static final Map<String, String> COLLECTION_SECTION_MAP = Map.ofEntries(
            // Schema
            Map.entry("collections", "Schema"),
            Map.entry("fields", "Schema"),

            // Profiles
            Map.entry("profiles", "Profiles"),
            Map.entry("profile-system-permissions", "Profiles"),
            Map.entry("profile-object-permissions", "Profiles"),
            Map.entry("profile-field-permissions", "Profiles"),

            // Permission Sets
            Map.entry("permission-sets", "Permission Sets"),
            Map.entry("permset-system-permissions", "Permission Sets"),
            Map.entry("permset-object-permissions", "Permission Sets"),
            Map.entry("permset-field-permissions", "Permission Sets"),

            // Users
            Map.entry("users", "Users"),
            Map.entry("user-permission-sets", "Users"),
            Map.entry("group-permission-sets", "Users"),
            Map.entry("groups", "Users"),

            // OIDC
            Map.entry("oidc-providers", "OIDC"),

            // Validation
            Map.entry("validation-rules", "Validation"),
            Map.entry("record-types", "Validation"),

            // UI
            Map.entry("page-layouts", "UI"),
            Map.entry("list-views", "UI"),

            // Flows
            Map.entry("flows", "Flows"),

            // Governor Limits
            Map.entry("governor-limits", "Governor Limits")
    );

    /**
     * Collections whose names should be derived from the entity type rather
     * than the collection name itself (for more readable audit entries).
     */
    private static final Set<String> JUNCTION_COLLECTIONS = Set.of(
            "profile-system-permissions", "profile-object-permissions", "profile-field-permissions",
            "permset-system-permissions", "permset-object-permissions", "permset-field-permissions",
            "user-permission-sets", "group-permission-sets"
    );

    private final SetupAuditService auditService;
    private final ObjectMapper objectMapper;

    public SetupAuditWriteListener(SetupAuditService auditService) {
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void afterCreate(String collectionName, String tenantId, String userId,
                            String recordId, Map<String, Object> data) {
        String section = COLLECTION_SECTION_MAP.get(collectionName);
        if (section == null) {
            return; // Not a setup collection — skip
        }

        String entityType = resolveEntityType(collectionName);
        String entityName = extractEntityName(data);

        auditService.log(tenantId, userId, "CREATE", section, entityType,
                recordId, entityName, null, toJson(data));
    }

    @Override
    public void afterUpdate(String collectionName, String tenantId, String userId,
                            String recordId, Map<String, Object> data) {
        String section = COLLECTION_SECTION_MAP.get(collectionName);
        if (section == null) {
            return; // Not a setup collection — skip
        }

        String entityType = resolveEntityType(collectionName);

        // We only have the new data — old data would require a pre-fetch
        auditService.log(tenantId, userId, "UPDATE", section, entityType,
                recordId, null, null, toJson(data));
    }

    @Override
    public void afterDelete(String collectionName, String tenantId, String userId,
                            String recordId) {
        String section = COLLECTION_SECTION_MAP.get(collectionName);
        if (section == null) {
            return; // Not a setup collection — skip
        }

        String entityType = resolveEntityType(collectionName);

        auditService.log(tenantId, userId, "DELETE", section, entityType,
                recordId, null, null, null);
    }

    /**
     * Resolves a human-readable entity type from the collection name.
     * For junction tables, returns a simplified name.
     */
    static String resolveEntityType(String collectionName) {
        if (JUNCTION_COLLECTIONS.contains(collectionName)) {
            // e.g., "profile-system-permissions" → "system-permission"
            return collectionName.substring(collectionName.indexOf('-') + 1);
        }
        // Remove trailing 's' for singular form (simple heuristic)
        if (collectionName.endsWith("s") && !collectionName.endsWith("ss")) {
            return collectionName.substring(0, collectionName.length() - 1);
        }
        return collectionName;
    }

    /**
     * Extracts a display name from record data for audit entries.
     */
    private static String extractEntityName(Map<String, Object> data) {
        // Try common name fields in priority order
        for (String key : new String[]{"name", "label", "apiName", "entityName", "email"}) {
            Object value = data.get(key);
            if (value != null) {
                String str = value.toString();
                if (!str.isBlank()) {
                    return str.length() > 200 ? str.substring(0, 200) : str;
                }
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit data to JSON: {}", e.getMessage());
            return null;
        }
    }
}
