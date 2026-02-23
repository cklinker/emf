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

class FieldUpdateActionHandlerTest {

    private FieldUpdateActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new FieldUpdateActionHandler(objectMapper);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("FIELD_UPDATE", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should execute field updates successfully")
    void shouldExecuteFieldUpdates() {
        ActionContext ctx = createContext("""
            {"updates": [
                {"field": "status", "value": "Approved"},
                {"field": "priority", "value": "High"}
            ]}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertNotNull(result.outputData());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals("Approved", updatedFields.get("status"));
        assertEquals("High", updatedFields.get("priority"));
    }

    @Test
    @DisplayName("Should fail when no updates defined")
    void shouldFailWhenNoUpdates() {
        ActionContext ctx = createContext("{\"updates\": []}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should fail when updates key is missing")
    void shouldFailWhenUpdatesMissing() {
        ActionContext ctx = createContext("{}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should skip blank field names")
    void shouldSkipBlankFieldNames() {
        ActionContext ctx = createContext("""
            {"updates": [
                {"field": "", "value": "skip"},
                {"field": "status", "value": "Active"}
            ]}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals(1, updatedFields.size());
        assertEquals("Active", updatedFields.get("status"));
    }

    @Test
    @DisplayName("Should handle invalid JSON config")
    void shouldHandleInvalidJson() {
        ActionContext ctx = createContext("not-json");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Validate should reject missing updates")
    void validateShouldRejectMissing() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should reject empty updates")
    void validateShouldRejectEmpty() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{\"updates\": []}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate(
            "{\"updates\": [{\"field\": \"status\", \"value\": \"Done\"}]}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "status", "Pending"))
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
