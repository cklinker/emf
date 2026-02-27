package com.emf.runtime.module.schema.hooks;

import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.BeforeSaveResult;
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
        Object typeObj = record.get("type");
        if (typeObj != null && !typeObj.toString().isBlank()) {
            String type = typeObj.toString();
            if (!VALID_FIELD_TYPES.contains(type)) {
                return BeforeSaveResult.error("type",
                        "Invalid field type: '" + type + "'");
            }
        }

        // Set defaults
        Map<String, Object> updates = new HashMap<>();

        if (!record.containsKey("active")) {
            updates.put("active", true);
        }

        if (updates.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        return BeforeSaveResult.withFieldUpdates(updates);
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
