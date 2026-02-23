package com.emf.controlplane.service.workflow;

import com.emf.controlplane.service.WorkflowRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowLogRetentionServiceTest {

    @Test
    @DisplayName("Should cleanup old logs with configured retention days")
    void shouldCleanupOldLogs() {
        WorkflowRuleService ruleService = mock(WorkflowRuleService.class);
        when(ruleService.deleteLogsOlderThan(any(Instant.class))).thenReturn(5);

        WorkflowLogRetentionService retentionService = new WorkflowLogRetentionService(ruleService, 90);
        retentionService.cleanupOldLogs();

        verify(ruleService).deleteLogsOlderThan(any(Instant.class));
    }

    @Test
    @DisplayName("Should skip cleanup when retention days is 0")
    void shouldSkipWhenRetentionIsZero() {
        WorkflowRuleService ruleService = mock(WorkflowRuleService.class);

        WorkflowLogRetentionService retentionService = new WorkflowLogRetentionService(ruleService, 0);
        retentionService.cleanupOldLogs();

        verify(ruleService, never()).deleteLogsOlderThan(any());
    }

    @Test
    @DisplayName("Should handle cleanup errors gracefully")
    void shouldHandleCleanupErrors() {
        WorkflowRuleService ruleService = mock(WorkflowRuleService.class);
        when(ruleService.deleteLogsOlderThan(any())).thenThrow(new RuntimeException("DB error"));

        WorkflowLogRetentionService retentionService = new WorkflowLogRetentionService(ruleService, 90);

        assertDoesNotThrow(() -> retentionService.cleanupOldLogs());
    }

    @Test
    @DisplayName("Should return configured retention days")
    void shouldReturnRetentionDays() {
        WorkflowRuleService ruleService = mock(WorkflowRuleService.class);
        WorkflowLogRetentionService retentionService = new WorkflowLogRetentionService(ruleService, 30);

        assertEquals(30, retentionService.getRetentionDays());
    }
}
