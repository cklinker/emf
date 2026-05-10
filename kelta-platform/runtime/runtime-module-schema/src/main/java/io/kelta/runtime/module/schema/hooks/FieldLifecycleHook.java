package io.kelta.runtime.module.schema.hooks;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle hook for the "fields" system collection.
 *
 * <p>Enforces business rules for field CRUD operations:
 * <ul>
 *   <li>Before create: Validate field name format, check reserved names, validate field type</li>
 *   <li>After create/delete: Log for audit trail</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class FieldLifecycleHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(FieldLifecycleHook.class);

    static final Set<String> VALID_FIELD_TYPES = Set.of(
            // Lowercase (UI / FieldService types)
            "string", "number", "boolean", "date", "datetime",
            "reference", "array", "object", "json",
            "picklist", "multi_picklist", "currency", "percent",
            "auto_number", "phone", "email", "url",
            "rich_text", "encrypted", "external_id",
            "geolocation", "lookup", "master_detail",
            "formula", "rollup_summary",
            // Uppercase (runtime / canonical types)
            "STRING", "INTEGER", "LONG", "DOUBLE", "BOOLEAN",
            "DATE", "DATETIME", "JSON",
            "PICKLIST", "MULTI_PICKLIST", "CURRENCY", "PERCENT",
            "AUTO_NUMBER", "PHONE", "EMAIL", "URL",
            "RICH_TEXT", "ENCRYPTED", "EXTERNAL_ID",
            "GEOLOCATION", "LOOKUP", "MASTER_DETAIL",
            "FORMULA", "ROLLUP_SUMMARY", "REFERENCE", "ARRAY"
    );

    /**
     * UI-style synonyms that don't map to a FieldType enum constant by simple
     * uppercase. Most lowercase types match their canonical enum directly
     * (e.g., {@code "string"} → {@code "STRING"}); the entries here cover the
     * cases where the UI uses a different word than the runtime enum.
     */
    private static final Map<String, String> UI_TYPE_TO_CANONICAL = Map.of(
            "number", "DOUBLE",
            "object", "JSON",
            "array", "JSON",
            "reference", "MASTER_DETAIL"
    );

    static final Set<String> RESERVED_FIELD_NAMES = Set.of(
            "id", "createdAt", "updatedAt", "createdBy", "updatedBy",
            "tenantId", "type", "attributes", "relationships"
    );

    @Override
    public String getCollectionName() {
        return "fields";
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        // Validate field name
        Object nameObj = record.get("name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            return BeforeSaveResult.error("name", "Field name is required");
        }

        String name = nameObj.toString().trim();

        // Check reserved names
        if (RESERVED_FIELD_NAMES.contains(name)) {
            return BeforeSaveResult.error("name",
                    "Field name '" + name + "' is reserved and cannot be used");
        }

        // Validate name format: camelCase or snake_case, starts with letter
        if (!name.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            return BeforeSaveResult.error("name",
                    "Field name must start with a letter and contain only letters, numbers, and underscores");
        }

        // Validate field type
        Map<String, Object> updates = new HashMap<>();
        Object typeObj = record.get("type");
        if (typeObj != null && !typeObj.toString().isBlank()) {
            String type = typeObj.toString();
            if (!VALID_FIELD_TYPES.contains(type)) {
                return BeforeSaveResult.error("type",
                        "Invalid field type: '" + type + "'");
            }
            String canonical = canonicalizeType(type);
            if (!canonical.equals(type)) {
                updates.put("type", canonical);
            }
        }

        if (!record.containsKey("active")) {
            updates.put("active", true);
        }

        if (updates.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        return BeforeSaveResult.withFieldUpdates(updates);
    }

    @Override
    public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                         Map<String, Object> previous, String tenantId) {
        // Field type is immutable, but if a request includes it, normalize so
        // the stored value stays canonical even when the same value is round-
        // tripped through the UI.
        Object typeObj = record.get("type");
        if (typeObj == null || typeObj.toString().isBlank()) {
            return BeforeSaveResult.ok();
        }
        String type = typeObj.toString();
        if (!VALID_FIELD_TYPES.contains(type)) {
            return BeforeSaveResult.error("type", "Invalid field type: '" + type + "'");
        }
        String canonical = canonicalizeType(type);
        if (canonical.equals(type)) {
            return BeforeSaveResult.ok();
        }
        return BeforeSaveResult.withFieldUpdates(Map.of("type", canonical));
    }

    /**
     * Returns the canonical (uppercase, runtime-enum) form of a field type
     * accepted by {@link #VALID_FIELD_TYPES}. Already-canonical inputs pass
     * through unchanged.
     */
    static String canonicalizeType(String type) {
        String mapped = UI_TYPE_TO_CANONICAL.get(type);
        if (mapped != null) {
            return mapped;
        }
        return type.toUpperCase();
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        log.info("Schema lifecycle: field '{}' created for tenant '{}'",
                record.get("name"), tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        log.info("Schema lifecycle: field '{}' deleted for tenant '{}'", id, tenantId);
    }
}
