package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SendNotificationActionHandler")
class SendNotificationActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SendNotificationActionHandler handler = new SendNotificationActionHandler(objectMapper);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("SEND_NOTIFICATION", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should send notification with all fields")
    void shouldSendNotification() {
        String config = """
            {"userId": "user-2", "title": "Order Approved", "message": "Your order is ready.", "level": "INFO"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("user-2", result.outputData().get("userId"));
        assertEquals("Order Approved", result.outputData().get("title"));
        assertEquals("SENT", result.outputData().get("status"));
    }

    @Test
    @DisplayName("Should default userId to context user")
    void shouldDefaultUserId() {
        String config = """
            {"title": "Alert", "message": "Something happened"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("user-1", result.outputData().get("userId"));
    }

    @Test
    @DisplayName("Should default level to INFO")
    void shouldDefaultLevel() {
        String config = """
            {"title": "Alert", "message": "Info message"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("INFO", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should fail when title is missing")
    void shouldFailWhenTitleMissing() {
        String config = """
            {"message": "No title"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("title"));
    }

    @Test
    @DisplayName("Should fail when message is missing")
    void shouldFailWhenMessageMissing() {
        String config = """
            {"title": "No message"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("message"));
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of()).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
