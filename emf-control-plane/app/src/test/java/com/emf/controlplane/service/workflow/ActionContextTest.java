package com.emf.controlplane.service.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActionContextTest {

    @Test
    @DisplayName("Builder should create context with all fields")
    void builderCreatesContext() {
        Map<String, Object> data = Map.of("name", "Test");
        Map<String, Object> previousData = Map.of("name", "Old");
        Map<String, Object> resolvedData = Map.of("email", "test@example.com");

        ActionContext context = ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("customers")
            .recordId("rec-1")
            .data(data)
            .previousData(previousData)
            .changedFields(List.of("name"))
            .userId("user-1")
            .actionConfigJson("{\"field\":\"status\"}")
            .workflowRuleId("rule-1")
            .executionLogId("log-1")
            .resolvedData(resolvedData)
            .build();

        assertEquals("tenant-1", context.tenantId());
        assertEquals("col-1", context.collectionId());
        assertEquals("customers", context.collectionName());
        assertEquals("rec-1", context.recordId());
        assertEquals("Test", context.data().get("name"));
        assertEquals("Old", context.previousData().get("name"));
        assertEquals(List.of("name"), context.changedFields());
        assertEquals("user-1", context.userId());
        assertEquals("{\"field\":\"status\"}", context.actionConfigJson());
        assertEquals("rule-1", context.workflowRuleId());
        assertEquals("log-1", context.executionLogId());
        assertEquals("test@example.com", context.resolvedData().get("email"));
    }

    @Test
    @DisplayName("Record constructor should work directly")
    void recordConstructor() {
        ActionContext context = new ActionContext(
            "t1", "c1", "orders", "r1",
            Map.of(), null, List.of(),
            "u1", "{}", "wr1", "el1", Map.of()
        );

        assertEquals("t1", context.tenantId());
        assertEquals("orders", context.collectionName());
    }
}
