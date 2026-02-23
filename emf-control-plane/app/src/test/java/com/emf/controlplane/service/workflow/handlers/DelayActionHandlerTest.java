package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.WorkflowPendingAction;
import com.emf.controlplane.repository.WorkflowPendingActionRepository;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DelayActionHandlerTest {

    private DelayActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowPendingActionRepository pendingActionRepository;

    @BeforeEach
    void setUp() {
        pendingActionRepository = mock(WorkflowPendingActionRepository.class);
        when(pendingActionRepository.save(any())).thenAnswer(inv -> {
            WorkflowPendingAction pa = inv.getArgument(0);
            if (pa.getId() == null) {
                // Simulate UUID generation
                try {
                    var f = pa.getClass().getSuperclass().getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(pa, java.util.UUID.randomUUID().toString());
                } catch (Exception e) {
                    // ignore
                }
            }
            return pa;
        });
        handler = new DelayActionHandler(objectMapper, pendingActionRepository);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("DELAY", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should create pending action with delay minutes")
    void shouldCreatePendingActionWithDelayMinutes() {
        ActionContext ctx = createContext("{\"delayMinutes\": 30}");

        Instant before = Instant.now().plus(29, ChronoUnit.MINUTES);
        ActionResult result = handler.execute(ctx);
        Instant after = Instant.now().plus(31, ChronoUnit.MINUTES);

        assertTrue(result.successful());
        assertNotNull(result.outputData().get("pendingActionId"));
        assertEquals("PENDING", result.outputData().get("status"));

        ArgumentCaptor<WorkflowPendingAction> captor = ArgumentCaptor.forClass(WorkflowPendingAction.class);
        verify(pendingActionRepository).save(captor.capture());
        WorkflowPendingAction saved = captor.getValue();

        assertEquals("tenant-1", saved.getTenantId());
        assertEquals("rule-1", saved.getWorkflowRuleId());
        assertEquals("rec-1", saved.getRecordId());
        assertEquals("PENDING", saved.getStatus());
        assertTrue(saved.getScheduledAt().isAfter(before));
        assertTrue(saved.getScheduledAt().isBefore(after));
    }

    @Test
    @DisplayName("Should create pending action with delayUntilTime")
    void shouldCreatePendingActionWithDelayUntilTime() {
        String futureTime = Instant.now().plus(2, ChronoUnit.HOURS).toString();
        ActionContext ctx = createContext("{\"delayUntilTime\": \"" + futureTime + "\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        verify(pendingActionRepository).save(any());
    }

    @Test
    @DisplayName("Should create pending action with delayUntilField")
    void shouldCreatePendingActionWithDelayUntilField() {
        String futureTime = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        ActionContext ctx = ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("dueDate", futureTime))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson("{\"delayUntilField\": \"dueDate\"}")
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        verify(pendingActionRepository).save(any());
    }

    @Test
    @DisplayName("Should fail when delayUntilField value is null")
    void shouldFailWhenDelayUntilFieldNull() {
        ActionContext ctx = ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("status", "Active"))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson("{\"delayUntilField\": \"dueDate\"}")
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Could not compute delay time"));
    }

    @Test
    @DisplayName("Should fail when no delay config provided")
    void shouldFailWhenNoDelayConfig() {
        ActionContext ctx = createContext("{}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should snapshot record data")
    void shouldSnapshotRecordData() {
        ActionContext ctx = ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("status", "Pending", "total", 100))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson("{\"delayMinutes\": 5}")
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();

        handler.execute(ctx);

        ArgumentCaptor<WorkflowPendingAction> captor = ArgumentCaptor.forClass(WorkflowPendingAction.class);
        verify(pendingActionRepository).save(captor.capture());
        assertNotNull(captor.getValue().getRecordSnapshot());
        assertTrue(captor.getValue().getRecordSnapshot().contains("Pending"));
    }

    @Test
    @DisplayName("Validate should reject config without delay options")
    void validateShouldRejectNoDelayOptions() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept delayMinutes")
    void validateShouldAcceptDelayMinutes() {
        assertDoesNotThrow(() -> handler.validate("{\"delayMinutes\": 30}"));
    }

    @Test
    @DisplayName("Validate should accept delayUntilField")
    void validateShouldAcceptDelayUntilField() {
        assertDoesNotThrow(() -> handler.validate("{\"delayUntilField\": \"dueDate\"}"));
    }

    @Test
    @DisplayName("Validate should accept delayUntilTime")
    void validateShouldAcceptDelayUntilTime() {
        assertDoesNotThrow(() -> handler.validate("{\"delayUntilTime\": \"2025-12-31T23:59:59Z\"}"));
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
