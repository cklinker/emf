package com.emf.controlplane.controller;

import com.emf.controlplane.dto.WorkflowAnalyticsDto;
import com.emf.controlplane.dto.WorkflowRuleVersionDto;
import com.emf.controlplane.service.WorkflowRuleService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowRuleControllerAnalyticsTest {

    private WorkflowRuleService workflowRuleService;
    private WorkflowRuleController controller;

    @BeforeEach
    void setUp() {
        workflowRuleService = mock(WorkflowRuleService.class);
        controller = new WorkflowRuleController(workflowRuleService);
        TenantContextHolder.set("tenant-1", "tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should return analytics without date filter")
    void shouldReturnAnalytics() {
        WorkflowAnalyticsDto expected = new WorkflowAnalyticsDto();
        expected.setTotalExecutions(100);
        expected.setSuccessCount(80);
        expected.setFailureCount(20);
        expected.setSuccessRate(80.0);

        when(workflowRuleService.getAnalytics("tenant-1", null, null)).thenReturn(expected);

        WorkflowAnalyticsDto result = controller.getAnalytics(null, null);

        assertEquals(100, result.getTotalExecutions());
        assertEquals(80.0, result.getSuccessRate());
    }

    @Test
    @DisplayName("Should return analytics with date filter")
    void shouldReturnAnalyticsWithDateFilter() {
        String start = "2024-01-01T00:00:00Z";
        String end = "2024-12-31T23:59:59Z";

        WorkflowAnalyticsDto expected = new WorkflowAnalyticsDto();
        expected.setTotalExecutions(50);

        when(workflowRuleService.getAnalytics(
            eq("tenant-1"), eq(Instant.parse(start)), eq(Instant.parse(end))))
            .thenReturn(expected);

        WorkflowAnalyticsDto result = controller.getAnalytics(start, end);

        assertEquals(50, result.getTotalExecutions());
    }

    @Test
    @DisplayName("Should return rule-specific analytics")
    void shouldReturnRuleAnalytics() {
        WorkflowAnalyticsDto expected = new WorkflowAnalyticsDto();
        expected.setTotalExecutions(25);
        expected.setSuccessCount(20);

        when(workflowRuleService.getRuleAnalytics("rule-1", null, null)).thenReturn(expected);

        WorkflowAnalyticsDto result = controller.getRuleAnalytics("rule-1", null, null);

        assertEquals(25, result.getTotalExecutions());
    }

    @Test
    @DisplayName("Should list versions for a rule")
    void shouldListVersions() {
        WorkflowRuleVersionDto v1 = new WorkflowRuleVersionDto();
        v1.setVersionNumber(1);
        WorkflowRuleVersionDto v2 = new WorkflowRuleVersionDto();
        v2.setVersionNumber(2);

        when(workflowRuleService.listVersions("rule-1")).thenReturn(List.of(v2, v1));

        List<WorkflowRuleVersionDto> result = controller.listVersions("rule-1");

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersionNumber());
    }

    @Test
    @DisplayName("Should get specific version")
    void shouldGetSpecificVersion() {
        WorkflowRuleVersionDto version = new WorkflowRuleVersionDto();
        version.setVersionNumber(3);
        version.setSnapshot("{\"name\":\"Test\"}");

        when(workflowRuleService.getVersion("rule-1", 3)).thenReturn(version);

        WorkflowRuleVersionDto result = controller.getVersion("rule-1", 3);

        assertEquals(3, result.getVersionNumber());
        assertEquals("{\"name\":\"Test\"}", result.getSnapshot());
    }
}
