package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SendNotificationActionHandlerTest {

    private SendNotificationActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new SendNotificationActionHandler(objectMapper);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("SEND_NOTIFICATION", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should send notification with all fields")
    void shouldSendNotificationWithAllFields() {
        ActionContext ctx = createContext("""
            {
                "userId": "target-user",
                "title": "Order Approved",
                "message": "Your order has been approved",
                "level": "INFO"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("target-user", result.outputData().get("userId"));
        assertEquals("Order Approved", result.outputData().get("title"));
        assertEquals("Your order has been approved", result.outputData().get("message"));
        assertEquals("INFO", result.outputData().get("level"));
        assertEquals("SENT", result.outputData().get("status"));
    }

    @Test
    @DisplayName("Should default userId to context user")
    void shouldDefaultUserId() {
        ActionContext ctx = createContext("""
            {
                "title": "Alert",
                "message": "Something happened"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("user-1", result.outputData().get("userId"));
    }

    @Test
    @DisplayName("Should default level to INFO")
    void shouldDefaultLevel() {
        ActionContext ctx = createContext("""
            {
                "title": "Alert",
                "message": "Something happened"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("INFO", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should fail when title missing")
    void shouldFailWhenTitleMissing() {
        ActionContext ctx = createContext("{\"message\": \"test\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("title is required"));
    }

    @Test
    @DisplayName("Should fail when message missing")
    void shouldFailWhenMessageMissing() {
        ActionContext ctx = createContext("{\"title\": \"test\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("message is required"));
    }

    @Test
    @DisplayName("Validate should reject missing title")
    void validateShouldRejectMissingTitle() {
        assertThrows(IllegalArgumentException.class, () ->
            handler.validate("{\"message\": \"test\"}"));
    }

    @Test
    @DisplayName("Validate should reject missing message")
    void validateShouldRejectMissingMessage() {
        assertThrows(IllegalArgumentException.class, () ->
            handler.validate("{\"title\": \"test\"}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate(
            "{\"title\": \"Alert\", \"message\": \"Something happened\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1"))
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
