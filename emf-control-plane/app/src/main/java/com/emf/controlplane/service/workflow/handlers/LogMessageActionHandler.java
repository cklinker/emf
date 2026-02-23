package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Action handler that writes a message to the execution log.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "message": "Order status changed to Approved",
 *   "level": "INFO"
 * }
 * </pre>
 * <p>
 * The message is captured in the action log's output snapshot.
 * Level defaults to "INFO" and can be INFO, WARNING, ERROR, or DEBUG.
 */
@Component
public class LogMessageActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(LogMessageActionHandler.class);
    private static final Set<String> VALID_LEVELS = Set.of("DEBUG", "INFO", "WARNING", "ERROR");

    private final ObjectMapper objectMapper;

    public LogMessageActionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActionTypeKey() {
        return "LOG_MESSAGE";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String message = (String) config.get("message");
            if (message == null || message.isBlank()) {
                return ActionResult.failure("Log message is required");
            }

            String level = (String) config.getOrDefault("level", "INFO");
            if (!VALID_LEVELS.contains(level.toUpperCase())) {
                level = "INFO";
            }

            // Log the message at the appropriate level
            switch (level.toUpperCase()) {
                case "DEBUG" -> log.debug("Workflow log [{}]: {}", context.workflowRuleId(), message);
                case "WARNING" -> log.warn("Workflow log [{}]: {}", context.workflowRuleId(), message);
                case "ERROR" -> log.error("Workflow log [{}]: {}", context.workflowRuleId(), message);
                default -> log.info("Workflow log [{}]: {}", context.workflowRuleId(), message);
            }

            return ActionResult.success(Map.of(
                "message", message,
                "level", level.toUpperCase(),
                "workflowRuleId", context.workflowRuleId(),
                "recordId", context.recordId() != null ? context.recordId() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to execute log message action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("message") == null) {
                throw new IllegalArgumentException("Config must contain 'message'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
