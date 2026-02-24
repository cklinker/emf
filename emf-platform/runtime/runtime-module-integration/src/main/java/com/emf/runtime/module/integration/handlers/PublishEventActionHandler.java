package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Action handler that publishes a custom event (e.g., to Kafka).
 *
 * <p>Config format:
 * <pre>
 * {
 *   "topic": "custom.event.topic",
 *   "eventType": "order.approved",
 *   "dataPayload": {"key1": "value1"}
 * }
 * </pre>
 *
 * <p>Publishes a message containing event data, record information, and a timestamp.
 * The message key is {@code tenantId:collectionId} for consistent partitioning.
 *
 * <p>This handler uses an {@code EventPublisher} function obtained from ModuleContext
 * extensions. If no publisher is available, the event data is logged and returned
 * in the output without actual publishing.
 *
 * @since 1.0.0
 */
public class PublishEventActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(PublishEventActionHandler.class);

    /**
     * Functional interface for event publishing. Implementations can use Kafka, SNS, etc.
     */
    @FunctionalInterface
    public interface EventPublisher {
        void publish(String topic, String key, Map<String, Object> eventData);
    }

    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;

    public PublishEventActionHandler(ObjectMapper objectMapper, EventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getActionTypeKey() {
        return "PUBLISH_EVENT";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String topic = (String) config.get("topic");
            if (topic == null || topic.isBlank()) {
                return ActionResult.failure("Event 'topic' is required");
            }

            String eventType = (String) config.getOrDefault("eventType", "workflow.custom.event");

            // Build event payload
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("eventType", eventType);
            eventData.put("tenantId", context.tenantId());
            eventData.put("collectionId", context.collectionId());
            eventData.put("collectionName", context.collectionName());
            eventData.put("recordId", context.recordId());
            eventData.put("workflowRuleId", context.workflowRuleId());
            eventData.put("timestamp", Instant.now().toString());

            @SuppressWarnings("unchecked")
            Map<String, Object> dataPayload = (Map<String, Object>) config.get("dataPayload");
            if (dataPayload != null) {
                eventData.put("data", dataPayload);
            }

            eventData.put("recordData", context.data());

            String messageKey = context.tenantId() + ":" + context.collectionId();

            if (eventPublisher != null) {
                eventPublisher.publish(topic, messageKey, eventData);
                log.info("Custom event published: topic={}, eventType={}, workflowRule={}",
                    topic, eventType, context.workflowRuleId());
            } else {
                log.warn("No EventPublisher configured — event logged but not published: topic={}, eventType={}",
                    topic, eventType);
            }

            return ActionResult.success(Map.of(
                "topic", topic,
                "eventType", eventType,
                "messageKey", messageKey
            ));
        } catch (Exception e) {
            log.error("Failed to execute publish event action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("topic") == null) {
                throw new IllegalArgumentException("Config must contain 'topic'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
