package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Action handler that creates a task record for follow-up.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "subject": "Follow up on order",
 *   "description": "Review the order and contact customer",
 *   "assignTo": "user-id-or-email",
 *   "dueDate": "2024-12-31",
 *   "priority": "High",
 *   "status": "Open"
 * }
 * </pre>
 * <p>
 * Creates a task data payload that includes the triggering record reference.
 * The task data is returned in the {@link ActionResult#outputData()} for
 * downstream processing.
 */
@Component
public class CreateTaskActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskActionHandler.class);

    private final ObjectMapper objectMapper;

    public CreateTaskActionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActionTypeKey() {
        return "CREATE_TASK";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String subject = (String) config.get("subject");
            if (subject == null || subject.isBlank()) {
                return ActionResult.failure("Task 'subject' is required");
            }

            // Build task data
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("subject", subject);

            String description = (String) config.get("description");
            if (description != null) {
                taskData.put("description", description);
            }

            String assignTo = (String) config.get("assignTo");
            if (assignTo != null) {
                taskData.put("assignTo", assignTo);
            }

            String dueDate = (String) config.get("dueDate");
            if (dueDate != null) {
                taskData.put("dueDate", dueDate);
            }

            String priority = (String) config.get("priority");
            if (priority != null) {
                taskData.put("priority", priority);
            }

            String status = (String) config.getOrDefault("status", "Open");
            taskData.put("status", status);

            // Link back to triggering record
            taskData.put("relatedRecordId", context.recordId());
            taskData.put("relatedCollectionId", context.collectionId());
            taskData.put("relatedCollectionName", context.collectionName());
            taskData.put("createdBy", context.userId());

            log.info("Create task action: subject={}, assignTo={}, workflowRule={}",
                subject, assignTo, context.workflowRuleId());

            return ActionResult.success(Map.of(
                "taskData", taskData,
                "subject", subject,
                "status", status
            ));
        } catch (Exception e) {
            log.error("Failed to execute create task action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("subject") == null) {
                throw new IllegalArgumentException("Config must contain 'subject'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
