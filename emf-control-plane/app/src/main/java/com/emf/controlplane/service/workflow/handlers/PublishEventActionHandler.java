package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Action handler that publishes a custom Kafka event.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "topic": "custom.event.topic",
 *   "eventType": "order.approved",
 *   "dataPayload": {
 *     "key1": "value1",
 *     "key2": "value2"
 *   }
 * }
 * </pre>
 * <p>
 * Publishes a message to the specified Kafka topic containing the event data,
 * record information, and a timestamp. The message key is composed of
 * {@code tenantId:collectionId} for consistent partitioning.
 * <p>
 * This handler is only active when Kafka is enabled.
 */
@Component
@ConditionalOnProperty(name = "emf.control-plane.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class PublishEventActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(PublishEventActionHandler.class);

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PublishEventActionHandler(ObjectMapper objectMapper,
                                     KafkaTemplate<String, Object> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
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
                return ActionResult.failure("Kafka 'topic' is required");
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

            // Add custom data payload
            @SuppressWarnings("unchecked")
            Map<String, Object> dataPayload = (Map<String, Object>) config.get("dataPayload");
            if (dataPayload != null) {
                eventData.put("data", dataPayload);
            }

            // Include record data
            eventData.put("recordData", context.data());

            // Publish to Kafka with tenant:collection key for partitioning
            String messageKey = context.tenantId() + ":" + context.collectionId();
            kafkaTemplate.send(topic, messageKey, eventData);

            log.info("Custom event published: topic={}, eventType={}, workflowRule={}",
                topic, eventType, context.workflowRuleId());

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
