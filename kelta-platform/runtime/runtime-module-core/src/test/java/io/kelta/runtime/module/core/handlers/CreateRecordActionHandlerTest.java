package io.kelta.runtime.module.core.handlers;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CreateRecordActionHandler")
class CreateRecordActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollectionRegistry registry = mock(CollectionRegistry.class);
    private final QueryEngine queryEngine = mock(QueryEngine.class);
    private final CreateRecordActionHandler handler =
        new CreateRecordActionHandler(objectMapper, registry, queryEngine);

    {
        when(queryEngine.create(any(), any())).thenAnswer(inv -> {
            Map<String, Object> created = new HashMap<>((Map<String, Object>) inv.getArgument(1));
            created.put("id", "test-id-001");
            return created;
        });
    }

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("CREATE_RECORD", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should create record with static values")
    void shouldCreateRecordWithStaticValues() {
        when(registry.get("orders")).thenReturn(mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders", "fieldMappings": [
                {"targetField": "name", "value": "New Order"},
                {"targetField": "status", "value": "Open"}
            ]}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("orders", result.outputData().get("targetCollectionName"));
    }

    @Test
    @DisplayName("Should create record with source field mapping")
    void shouldCreateRecordWithSourceField() {
        when(registry.get("tasks")).thenReturn(mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "tasks", "fieldMappings": [
                {"targetField": "source_id", "sourceField": "id"}
            ]}
            """;
        ActionContext ctx = ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("id", "order-123"))
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

        ActionResult result = handler.execute(ctx);
        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) result.outputData().get("recordData");
        assertEquals("order-123", recordData.get("source_id"));
    }

    @Test
    @DisplayName("Should fail when target collection not found")
    void shouldFailWhenCollectionNotFound() {
        when(registry.get("nonexistent")).thenReturn(null);

        String config = """
            {"targetCollectionName": "nonexistent"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should fail when no target collection specified")
    void shouldFailWhenNoTarget() {
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should support legacy targetCollectionId config")
    void shouldSupportLegacyId() {
        when(registry.get("orders")).thenReturn(mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionId": "orders"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
    }

    @Test
    @DisplayName("Should stamp createdBy/updatedBy from a UUID execution actor")
    void shouldStampAuditUsersFromActor() {
        when(registry.get("orders")).thenReturn(mock(CollectionDefinition.class));
        String actor = "3f2b6c1e-9d4a-4f0b-8e2d-1a2b3c4d5e6f";

        String config = """
            {"targetCollectionName": "orders", "fieldMappings": [
                {"targetField": "name", "value": "New Order"}
            ]}
            """;
        ActionResult result = handler.execute(makeContext(config, actor));
        assertTrue(result.successful());

        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) result.outputData().get("recordData");
        assertEquals(actor, recordData.get("createdBy"));
        assertEquals(actor, recordData.get("updatedBy"));
    }

    @Test
    @DisplayName("Should not stamp audit users from a non-UUID actor (legacy 'webhook')")
    void shouldNotStampNonUuidActor() {
        when(registry.get("orders")).thenReturn(mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders", "fieldMappings": [
                {"targetField": "name", "value": "New Order"}
            ]}
            """;
        ActionResult result = handler.execute(makeContext(config, "webhook"));
        assertTrue(result.successful());

        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) result.outputData().get("recordData");
        assertFalse(recordData.containsKey("createdBy"));
        assertFalse(recordData.containsKey("updatedBy"));
    }

    @Test
    @DisplayName("Should not stamp audit users when the execution has no actor")
    void shouldNotStampWithoutActor() {
        when(registry.get("orders")).thenReturn(mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders", "fieldMappings": [
                {"targetField": "name", "value": "New Order"}
            ]}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());

        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) result.outputData().get("recordData");
        assertFalse(recordData.containsKey("createdBy"));
    }

    private ActionContext makeContext(String config) {
        return makeContext(config, null);
    }

    private ActionContext makeContext(String config, String userId) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("name", "Test"))
            .userId(userId)
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
