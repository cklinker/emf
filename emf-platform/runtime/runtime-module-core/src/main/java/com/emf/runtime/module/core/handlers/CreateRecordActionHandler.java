package com.emf.runtime.module.core.handlers;

import com.emf.runtime.model.CollectionDefinition;
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

/**
 * Action handler that creates a new record in a target collection.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "targetCollectionName": "orders",
 *   "fieldMappings": [
 *     {"targetField": "name", "value": "New Record"},
 *     {"targetField": "source_id", "sourceField": "id"}
 *   ]
 * }
 * </pre>
 *
 * <p>Supports both {@code targetCollectionName} (preferred) and legacy
 * {@code targetCollectionId} for backward compatibility.
 *
 * @since 1.0.0
 */
public class CreateRecordActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateRecordActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionRegistry collectionRegistry;

    public CreateRecordActionHandler(ObjectMapper objectMapper, CollectionRegistry collectionRegistry) {
        this.objectMapper = objectMapper;
        this.collectionRegistry = collectionRegistry;
    }

    @Override
    public String getActionTypeKey() {
        return "CREATE_RECORD";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            // Resolve target collection (prefer name, fallback to ID)
            String targetCollectionName = resolveCollectionName(config);
            if (targetCollectionName == null) {
                return ActionResult.failure("Target collection name or ID is required");
            }

            CollectionDefinition targetCollection = collectionRegistry.get(targetCollectionName);
            if (targetCollection == null) {
                return ActionResult.failure("Target collection not found: " + targetCollectionName);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fieldMappings = (List<Map<String, Object>>) config.get("fieldMappings");

            Map<String, Object> recordData = new HashMap<>();

            if (fieldMappings != null) {
                for (Map<String, Object> mapping : fieldMappings) {
                    String targetField = (String) mapping.get("targetField");
                    if (targetField == null || targetField.isBlank()) {
                        continue;
                    }

                    String sourceField = (String) mapping.get("sourceField");
                    if (sourceField != null && !sourceField.isBlank()) {
                        Object value = context.data() != null ? context.data().get(sourceField) : null;
                        recordData.put(targetField, value);
                    } else {
                        recordData.put(targetField, mapping.get("value"));
                    }
                }
            }

            log.info("Create record action: targetCollection={}, fields={}",
                targetCollectionName, recordData.keySet());

            return ActionResult.success(Map.of(
                "targetCollectionName", targetCollectionName,
                "recordData", recordData
            ));
        } catch (Exception e) {
            log.error("Failed to execute create record action: {}", e.getMessage(), e);
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
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }

    private String resolveCollectionName(Map<String, Object> config) {
        // Prefer targetCollectionName
        String name = (String) config.get("targetCollectionName");
        if (name != null && !name.isBlank()) {
            return name;
        }

        // Legacy: targetCollectionId — not supported in module context, treat as name
        String id = (String) config.get("targetCollectionId");
        if (id != null && !id.isBlank()) {
            return id;
        }

        return null;
    }
}
