package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PublishEventActionHandler")
class PublishEventActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        var handler = new PublishEventActionHandler(objectMapper, null);
        assertEquals("PUBLISH_EVENT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should publish event with publisher")
    void shouldPublishEvent() {
        AtomicReference<String> capturedTopic = new AtomicReference<>();
        AtomicReference<String> capturedKey = new AtomicReference<>();
        PublishEventActionHandler.EventPublisher publisher = (topic, key, data) -> {
            capturedTopic.set(topic);
            capturedKey.set(key);
        };

        var handler = new PublishEventActionHandler(objectMapper, publisher);
        String config = """
            {"topic": "order.events", "eventType": "order.created"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("order.events", result.outputData().get("topic"));
        assertEquals("order.created", result.outputData().get("eventType"));
        assertEquals("order.events", capturedTopic.get());
        assertEquals("t1:c1", capturedKey.get());
    }

    @Test
    @DisplayName("Should succeed without publisher (log only)")
    void shouldSucceedWithoutPublisher() {
        var handler = new PublishEventActionHandler(objectMapper, null);
        String config = """
            {"topic": "test.events"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("test.events", result.outputData().get("topic"));
    }

    @Test
    @DisplayName("Should default event type")
    void shouldDefaultEventType() {
        var handler = new PublishEventActionHandler(objectMapper, null);
        String config = """
            {"topic": "test.events"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("workflow.custom.event", result.outputData().get("eventType"));
    }

    @Test
    @DisplayName("Should fail when topic is missing")
    void shouldFailWhenTopicMissing() {
        var handler = new PublishEventActionHandler(objectMapper, null);
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("topic"));
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("status", "Active")).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
