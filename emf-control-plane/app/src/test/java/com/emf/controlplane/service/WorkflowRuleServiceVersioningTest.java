package com.emf.controlplane.service;

import com.emf.controlplane.dto.WorkflowAnalyticsDto;
import com.emf.controlplane.dto.WorkflowRuleVersionDto;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.entity.WorkflowRuleVersion;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.repository.WorkflowRuleVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowRuleServiceVersioningTest {

    private WorkflowRuleRepository ruleRepository;
    private WorkflowExecutionLogRepository logRepository;
    private WorkflowActionLogRepository actionLogRepository;
    private WorkflowRuleVersionRepository versionRepository;
    private CollectionService collectionService;
    private ObjectMapper objectMapper;
    private WorkflowRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        logRepository = mock(WorkflowExecutionLogRepository.class);
        actionLogRepository = mock(WorkflowActionLogRepository.class);
        versionRepository = mock(WorkflowRuleVersionRepository.class);
        collectionService = mock(CollectionService.class);
        objectMapper = new ObjectMapper();

        service = new WorkflowRuleService(
            ruleRepository, logRepository, actionLogRepository, versionRepository,
            collectionService, null, null, objectMapper
        );
    }

    // --- Versioning Tests ---

    @Test
    @DisplayName("Should create version with correct snapshot")
    void shouldCreateVersion() {
        WorkflowRule rule = createRule("rule-1", "Test Rule");
        WorkflowAction action = new WorkflowAction();
        action.setActionType("FIELD_UPDATE");
        action.setConfig("{\"field\":\"status\",\"value\":\"Active\"}");
        action.setActive(true);
        action.setRetryCount(2);
        action.setRetryDelaySeconds(30);
        action.setRetryBackoff("EXPONENTIAL");
        rule.getActions().add(action);

        when(versionRepository.findMaxVersionNumber("rule-1")).thenReturn(0);
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createVersion(rule, "Initial version");

        verify(versionRepository).save(argThat(version -> {
            WorkflowRuleVersion v = (WorkflowRuleVersion) version;
            return v.getWorkflowRuleId().equals("rule-1")
                && v.getVersionNumber() == 1
                && v.getChangeSummary().equals("Initial version")
                && v.getSnapshot().contains("FIELD_UPDATE");
        }));
    }

    @Test
    @DisplayName("Should auto-increment version number")
    void shouldAutoIncrementVersion() {
        WorkflowRule rule = createRule("rule-1", "Test Rule");
        when(versionRepository.findMaxVersionNumber("rule-1")).thenReturn(3);
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createVersion(rule, null);

        verify(versionRepository).save(argThat(version ->
            ((WorkflowRuleVersion) version).getVersionNumber() == 4
        ));
    }

    @Test
    @DisplayName("Should list versions in descending order")
    void shouldListVersions() {
        WorkflowRuleVersion v2 = new WorkflowRuleVersion();
        v2.setId("v2-id");
        v2.setWorkflowRuleId("rule-1");
        v2.setVersionNumber(2);
        v2.setSnapshot("{}");

        WorkflowRuleVersion v1 = new WorkflowRuleVersion();
        v1.setId("v1-id");
        v1.setWorkflowRuleId("rule-1");
        v1.setVersionNumber(1);
        v1.setSnapshot("{}");

        when(versionRepository.findByWorkflowRuleIdOrderByVersionNumberDesc("rule-1"))
            .thenReturn(List.of(v2, v1));

        List<WorkflowRuleVersionDto> versions = service.listVersions("rule-1");

        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersionNumber());
        assertEquals(1, versions.get(1).getVersionNumber());
    }

    @Test
    @DisplayName("Should get specific version")
    void shouldGetSpecificVersion() {
        WorkflowRuleVersion v1 = new WorkflowRuleVersion();
        v1.setId("v1-id");
        v1.setWorkflowRuleId("rule-1");
        v1.setVersionNumber(1);
        v1.setSnapshot("{\"name\":\"Test\"}");
        v1.setChangeSummary("Initial");

        when(versionRepository.findByWorkflowRuleIdAndVersionNumber("rule-1", 1))
            .thenReturn(Optional.of(v1));

        WorkflowRuleVersionDto dto = service.getVersion("rule-1", 1);

        assertEquals(1, dto.getVersionNumber());
        assertEquals("{\"name\":\"Test\"}", dto.getSnapshot());
        assertEquals("Initial", dto.getChangeSummary());
    }

    @Test
    @DisplayName("Should serialize rule snapshot with retry fields")
    void shouldSerializeSnapshotWithRetryFields() {
        WorkflowRule rule = createRule("rule-1", "My Rule");
        rule.setExecutionMode("PARALLEL");

        WorkflowAction action = new WorkflowAction();
        action.setActionType("EMAIL_ALERT");
        action.setConfig("{\"to\":\"test@example.com\"}");
        action.setActive(true);
        action.setRetryCount(3);
        action.setRetryDelaySeconds(120);
        action.setRetryBackoff("EXPONENTIAL");
        rule.getActions().add(action);

        String snapshot = service.serializeRuleSnapshot(rule);

        assertTrue(snapshot.contains("\"name\":\"My Rule\""));
        assertTrue(snapshot.contains("\"executionMode\":\"PARALLEL\""));
        assertTrue(snapshot.contains("\"retryCount\":3"));
        assertTrue(snapshot.contains("\"retryDelaySeconds\":120"));
        assertTrue(snapshot.contains("\"retryBackoff\":\"EXPONENTIAL\""));
    }

    // --- Analytics Tests ---

    @Test
    @DisplayName("Should calculate analytics with all fields")
    void shouldCalculateAnalytics() {
        List<WorkflowExecutionLog> logs = new ArrayList<>();
        logs.add(createLog("SUCCESS", "ON_CREATE", 100));
        logs.add(createLog("SUCCESS", "ON_UPDATE", 200));
        logs.add(createLog("FAILURE", "ON_CREATE", 50));
        logs.add(createLog("PARTIAL_FAILURE", "ON_UPDATE", 300));

        when(logRepository.findByTenantIdOrderByExecutedAtDesc("tenant-1")).thenReturn(logs);

        WorkflowAnalyticsDto analytics = service.getAnalytics("tenant-1", null, null);

        assertEquals(4, analytics.getTotalExecutions());
        assertEquals(2, analytics.getSuccessCount());
        assertEquals(1, analytics.getFailureCount());
        assertEquals(1, analytics.getPartialFailureCount());
        assertEquals(50.0, analytics.getSuccessRate());
        assertNotNull(analytics.getAvgDurationMs());
        assertEquals(162.5, analytics.getAvgDurationMs(), 0.1);
    }

    @Test
    @DisplayName("Should handle empty analytics")
    void shouldHandleEmptyAnalytics() {
        when(logRepository.findByTenantIdOrderByExecutedAtDesc("tenant-1")).thenReturn(List.of());

        WorkflowAnalyticsDto analytics = service.getAnalytics("tenant-1", null, null);

        assertEquals(0, analytics.getTotalExecutions());
        assertEquals(0, analytics.getSuccessCount());
        assertEquals(0.0, analytics.getSuccessRate());
        assertNull(analytics.getAvgDurationMs());
    }

    @Test
    @DisplayName("Should filter analytics by date range")
    void shouldFilterByDateRange() {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = Instant.now();

        List<WorkflowExecutionLog> logs = List.of(createLog("SUCCESS", "ON_CREATE", 50));

        when(logRepository.findByTenantIdAndExecutedAtBetweenOrderByExecutedAtDesc(
            "tenant-1", start, end)).thenReturn(logs);

        WorkflowAnalyticsDto analytics = service.getAnalytics("tenant-1", start, end);

        assertEquals(1, analytics.getTotalExecutions());
        assertEquals(100.0, analytics.getSuccessRate());
    }

    @Test
    @DisplayName("Should calculate rule-specific analytics")
    void shouldCalculateRuleAnalytics() {
        List<WorkflowExecutionLog> logs = List.of(
            createLog("SUCCESS", "ON_CREATE", 100),
            createLog("FAILURE", "ON_CREATE", 200)
        );

        when(logRepository.findByWorkflowRuleIdOrderByExecutedAtDesc("rule-1")).thenReturn(logs);

        WorkflowAnalyticsDto analytics = service.getRuleAnalytics("rule-1", null, null);

        assertEquals(2, analytics.getTotalExecutions());
        assertEquals(1, analytics.getSuccessCount());
        assertEquals(1, analytics.getFailureCount());
        assertEquals(50.0, analytics.getSuccessRate());
    }

    @Test
    @DisplayName("Should group by trigger type in analytics")
    void shouldGroupByTriggerType() {
        List<WorkflowExecutionLog> logs = new ArrayList<>();
        logs.add(createLog("SUCCESS", "ON_CREATE", 100));
        logs.add(createLog("SUCCESS", "ON_CREATE", 100));
        logs.add(createLog("SUCCESS", "ON_UPDATE", 100));
        logs.add(createLog("SUCCESS", "MANUAL", 100));

        when(logRepository.findByTenantIdOrderByExecutedAtDesc("tenant-1")).thenReturn(logs);

        WorkflowAnalyticsDto analytics = service.getAnalytics("tenant-1", null, null);

        assertEquals(2L, analytics.getExecutionsByTriggerType().get("ON_CREATE"));
        assertEquals(1L, analytics.getExecutionsByTriggerType().get("ON_UPDATE"));
        assertEquals(1L, analytics.getExecutionsByTriggerType().get("MANUAL"));
    }

    // --- Log Retention Tests ---

    @Test
    @DisplayName("Should delete old logs")
    void shouldDeleteOldLogs() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        WorkflowExecutionLog oldLog = createLog("SUCCESS", "ON_CREATE", 100);
        oldLog.setId("log-1");

        when(logRepository.findByExecutedAtBefore(cutoff)).thenReturn(List.of(oldLog));
        when(actionLogRepository.findByExecutionLogIdOrderByExecutedAtAsc("log-1")).thenReturn(List.of());

        int deleted = service.deleteLogsOlderThan(cutoff);

        assertEquals(1, deleted);
        verify(logRepository).deleteAll(List.of(oldLog));
    }

    @Test
    @DisplayName("Should return 0 when no old logs")
    void shouldReturnZeroWhenNoOldLogs() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        when(logRepository.findByExecutedAtBefore(cutoff)).thenReturn(List.of());

        int deleted = service.deleteLogsOlderThan(cutoff);

        assertEquals(0, deleted);
        verify(logRepository, never()).deleteAll(anyList());
    }

    // --- Helpers ---

    private WorkflowRule createRule(String id, String name) {
        WorkflowRule rule = new WorkflowRule();
        rule.setId(id);
        rule.setName(name);
        rule.setTriggerType("ON_CREATE");
        rule.setErrorHandling("STOP_ON_ERROR");
        rule.setExecutionMode("SEQUENTIAL");

        com.emf.controlplane.entity.Collection collection = mock(com.emf.controlplane.entity.Collection.class);
        when(collection.getId()).thenReturn("col-1");
        when(collection.getName()).thenReturn("orders");
        rule.setCollection(collection);

        return rule;
    }

    private WorkflowExecutionLog createLog(String status, String triggerType, int durationMs) {
        WorkflowExecutionLog log = new WorkflowExecutionLog();
        log.setStatus(status);
        log.setTriggerType(triggerType);
        log.setDurationMs(durationMs);
        log.setExecutedAt(Instant.now());
        return log;
    }
}
