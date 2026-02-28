package com.emf.runtime.module.core.handlers;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.query.QueryEngine;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler that updates a record in any collection (cross-collection).
 *
 * <p>Config format:
 * <pre>
 * {
 *   "targetCollectionName": "orders",
 *   "recordIdField": "order_id",
 *   "updates": [
 *     {"field": "status", "value": "Approved"},
 *     {"field": "reviewer", "sourceField": "userId"}
 *   ]
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class UpdateRecordActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateRecordActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionRegistry collectionRegistry;
    private final QueryEngine queryEngine;

    public UpdateRecordActionHandler(ObjectMapper objectMapper,
                                     CollectionRegistry collectionRegistry,
                                     QueryEngine queryEngine) {
        this.objectMapper = objectMapper;
        this.collectionRegistry = collectionRegistry;
        this.queryEngine = queryEngine;
    }

    @Override
    public String getActionTypeKey() {
        return "UPDATE_RECORD";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String targetCollectionName = resolveCollectionName(config);
            if (targetCollectionName == null) {
                return ActionResult.failure("Target collection name or ID is required");
            }

            CollectionDefinition targetCollection = collectionRegistry.get(targetCollectionName);
            if (targetCollection == null) {
                return ActionResult.failure("Target collection not found: " + targetCollectionName);
            }

            String targetRecordId = resolveRecordId(config, context);
            if (targetRecordId == null || targetRecordId.isBlank()) {
                return ActionResult.failure("Could not resolve target record ID");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> updates = (List<Map<String, Object>>) config.get("updates");
            if (updates == null || updates.isEmpty()) {
                return ActionResult.failure("No updates defined");
            }

            Map<String, Object> updateData = new HashMap<>();
            for (Map<String, Object> update : updates) {
                String field = (String) update.get("field");
                if (field == null || field.isBlank()) {
                    continue;
                }

                String sourceField = (String) update.get("sourceField");
                if (sourceField != null && !sourceField.isBlank()) {
                    // Support dot-notation for nested access (e.g., "aggregations.total_spent")
                    Object value = resolveNestedValue(context.data(), sourceField);
                    updateData.put(field, value);
                } else {
                    updateData.put(field, update.get("value"));
                }
            }

            log.info("Update record action: collection={}, recordId={}, fields={}",
                targetCollectionName, targetRecordId, updateData.keySet());

            // Persist the update to the database
            Optional<Map<String, Object>> updatedRecord =
                queryEngine.update(targetCollection, targetRecordId, updateData);

            if (updatedRecord.isEmpty()) {
                return ActionResult.failure("Record not found: " + targetRecordId
                    + " in collection " + targetCollectionName);
            }

            return ActionResult.success(Map.of(
                "targetCollectionName", targetCollectionName,
                "targetRecordId", targetRecordId,
                "updatedFields", updateData,
                "record", updatedRecord.get()
            ));
        } catch (Exception e) {
            log.error("Failed to execute update record action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("targetCollectionName") == null && config.get("targetCollectionId") == null) {
                throw new IllegalArgumentException("Config must contain 'targetCollectionName' or 'targetCollectionId'");
            }
            if (config.get("updates") == null) {
                throw new IllegalArgumentException("Config must contain 'updates' array");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }

    private String resolveCollectionName(Map<String, Object> config) {
        String name = (String) config.get("targetCollectionName");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String id = (String) config.get("targetCollectionId");
        if (id != null && !id.isBlank()) {
            return id;
        }
        return null;
    }

    /**
     * Resolves a value from a nested data map using dot-notation.
     * For example, "aggregations.total_spent" resolves to data.get("aggregations").get("total_spent").
     */
    @SuppressWarnings("unchecked")
    private Object resolveNestedValue(Map<String, Object> data, String path) {
        if (data == null || path == null) return null;

        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private String resolveRecordId(Map<String, Object> config, ActionContext context) {
        // "recordId" — literal value (may already be template-resolved by StateDataResolver)
        String recordId = (String) config.get("recordId");
        if (recordId != null && !recordId.isBlank()) {
            return recordId;
        }

        // "recordIdField" — field name to look up in context data.
        // After template resolution, this might already be the actual value (e.g., a UUID)
        // rather than a field name. Fall back to using it as a literal if lookup fails.
        String recordIdField = (String) config.get("recordIdField");
        if (recordIdField != null && !recordIdField.isBlank()) {
            Object value = context.data() != null ? context.data().get(recordIdField) : null;
            if (value != null) {
                return value.toString();
            }
            // Field lookup failed — use the raw value as a literal record ID.
            // This handles cases where template resolution already produced the final value.
            return recordIdField;
        }

        return context.recordId();
    }
}
