package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublishEventActionHandlerTest {

    private PublishEventActionHandler handler;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        handler = new PublishEventActionHandler(objectMapper, kafkaTemplate);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("PUBLISH_EVENT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should publish event to specified topic")
    void shouldPublishEvent() {
        ActionContext ctx = createContext("""
            {
                "topic": "custom.orders.approved",
                "eventType": "order.approved",
                "dataPayload": {"reason": "auto-approved"}
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("custom.orders.approved", result.outputData().get("topic"));
        assertEquals("order.approved", result.outputData().get("eventType"));
        assertEquals("tenant-1:col-1", result.outputData().get("messageKey"));

        verify(kafkaTemplate).send(eq("custom.orders.approved"), eq("tenant-1:col-1"), any());
    }

    @Test
    @DisplayName("Should use default event type when not specified")
    void shouldUseDefaultEventType() {
        ActionContext ctx = createContext("""
            {"topic": "custom.events"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("workflow.custom.event", result.outputData().get("eventType"));
    }

    @Test
    @DisplayName("Should fail when topic is missing")
    void shouldFailWhenTopicMissing() {
        ActionContext ctx = createContext("""
            {"eventType": "test.event"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should fail when topic is blank")
    void shouldFailWhenTopicBlank() {
        ActionContext ctx = createContext("""
            {"topic": ""}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should handle Kafka send failure gracefully")
    void shouldHandleKafkaFailure() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Kafka unavailable"));

        ActionContext ctx = createContext("""
            {"topic": "custom.events"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Validate should reject missing topic")
    void validateShouldReject() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAccept() {
        assertDoesNotThrow(() -> handler.validate("{\"topic\": \"my.events\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "total", 150.0))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson(configJson)
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();
    }
}
