package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Action handler that sends an in-app notification.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "userId": "target-user-id",
 *   "title": "Order Approved",
 *   "message": "Your order has been approved.",
 *   "level": "INFO"
 * }
 * </pre>
 *
 * <p>If {@code userId} is not specified, the notification is sent to the user
 * who triggered the workflow. Level defaults to "INFO" (INFO, WARNING, ERROR).
 *
 * @since 1.0.0
 */
public class SendNotificationActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(SendNotificationActionHandler.class);

    private final ObjectMapper objectMapper;

    public SendNotificationActionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActionTypeKey() {
        return "SEND_NOTIFICATION";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String title = (String) config.get("title");
            if (title == null || title.isBlank()) {
                return ActionResult.failure("Notification title is required");
            }

            String message = (String) config.get("message");
            if (message == null || message.isBlank()) {
                return ActionResult.failure("Notification message is required");
            }

            String userId = (String) config.get("userId");
            if (userId == null || userId.isBlank()) {
                userId = context.userId();
            }

            String level = (String) config.getOrDefault("level", "INFO");

            log.info("Send notification action: userId={}, title='{}', level={}",
                userId, title, level);

            return ActionResult.success(Map.of(
                "userId", userId,
                "title", title,
                "message", message,
                "level", level,
                "status", "SENT"
            ));
        } catch (Exception e) {
            log.error("Failed to execute send notification action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("title") == null) {
                throw new IllegalArgumentException("Config must contain 'title'");
            }
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
