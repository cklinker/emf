package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FieldUpdateActionHandler")
class FieldUpdateActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FieldUpdateActionHandler handler = new FieldUpdateActionHandler(objectMapper);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("FIELD_UPDATE", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should update fields from config")
    void shouldUpdateFields() {
        String config = """
            {"updates": [{"field": "status", "value": "Approved"}, {"field": "priority", "value": "High"}]}
            """;
        ActionContext ctx = makeContext(config);
        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals("Approved", updatedFields.get("status"));
        assertEquals("High", updatedFields.get("priority"));
    }

    @Test
    @DisplayName("Should fail when no updates defined")
    void shouldFailWhenNoUpdates() {
        String config = """
            {"updates": []}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should skip blank field names")
    void shouldSkipBlankFieldNames() {
        String config = """
            {"updates": [{"field": "", "value": "test"}, {"field": "status", "value": "Active"}]}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals(1, updatedFields.size());
        assertEquals("Active", updatedFields.get("status"));
    }

    @Test
    @DisplayName("Should validate config requires updates array")
    void shouldValidateUpdatesRequired() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Should validate config rejects empty updates")
    void shouldValidateEmptyUpdates() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{\"updates\": []}"));
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1")
            .collectionId("c1")
            .collectionName("orders")
            .recordId("r1")
            .data(Map.of("name", "Test"))
            .actionConfigJson(config)
            .workflowRuleId("wf1")
            .executionLogId("log1")
            .build();
    }
}
