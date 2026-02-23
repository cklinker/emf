package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.WorkflowPendingAction;
import com.emf.controlplane.repository.WorkflowPendingActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PendingActionExecutorTest {

    private PendingActionExecutor executor;
    private WorkflowPendingActionRepository pendingActionRepository;

    @BeforeEach
    void setUp() {
        pendingActionRepository = mock(WorkflowPendingActionRepository.class);
        executor = new PendingActionExecutor(pendingActionRepository);
    }

    @Test
    @DisplayName("Should do nothing when no pending actions are due")
    void shouldDoNothingWhenNoPendingActions() {
        when(pendingActionRepository.findByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            eq("PENDING"), any(Instant.class)))
            .thenReturn(List.of());

        executor.pollAndExecute();

        verify(pendingActionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should execute due pending actions")
    void shouldExecuteDuePendingActions() {
        WorkflowPendingAction pending = createPendingAction(
            "pa-1", "tenant-1", "rule-1", "rec-1",
            Instant.now().minus(5, ChronoUnit.MINUTES));

        when(pendingActionRepository.findByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            eq("PENDING"), any(Instant.class)))
            .thenReturn(List.of(pending));

        executor.pollAndExecute();

        ArgumentCaptor<WorkflowPendingAction> captor = ArgumentCaptor.forClass(WorkflowPendingAction.class);
        verify(pendingActionRepository).save(captor.capture());
        assertEquals("EXECUTED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should execute multiple pending actions")
    void shouldExecuteMultiplePendingActions() {
        WorkflowPendingAction pa1 = createPendingAction(
            "pa-1", "tenant-1", "rule-1", "rec-1",
            Instant.now().minus(10, ChronoUnit.MINUTES));
        WorkflowPendingAction pa2 = createPendingAction(
            "pa-2", "tenant-1", "rule-2", "rec-2",
            Instant.now().minus(5, ChronoUnit.MINUTES));

        when(pendingActionRepository.findByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            eq("PENDING"), any(Instant.class)))
            .thenReturn(List.of(pa1, pa2));

        executor.pollAndExecute();

        verify(pendingActionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Should handle execution error and mark as FAILED")
    void shouldHandleExecutionError() {
        WorkflowPendingAction pending = createPendingAction(
            "pa-1", "tenant-1", "rule-1", "rec-1",
            Instant.now().minus(5, ChronoUnit.MINUTES));

        // First call to save throws, second save (in the error handler) succeeds
        when(pendingActionRepository.findByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            eq("PENDING"), any(Instant.class)))
            .thenReturn(List.of(pending));

        doThrow(new RuntimeException("DB error"))
            .doReturn(pending)
            .when(pendingActionRepository).save(any());

        executor.pollAndExecute();

        // Should have attempted to save twice: once for EXECUTED, once for FAILED
        verify(pendingActionRepository, times(2)).save(any());
    }

    private WorkflowPendingAction createPendingAction(String id, String tenantId,
                                                       String ruleId, String recordId,
                                                       Instant scheduledAt) {
        WorkflowPendingAction pa = new WorkflowPendingAction();
        pa.setTenantId(tenantId);
        pa.setWorkflowRuleId(ruleId);
        pa.setRecordId(recordId);
        pa.setScheduledAt(scheduledAt);
        pa.setStatus("PENDING");
        pa.setExecutionLogId("exec-log-1");

        try {
            var f = pa.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(pa, id);
        } catch (Exception e) {
            // ignore
        }

        return pa;
    }
}
