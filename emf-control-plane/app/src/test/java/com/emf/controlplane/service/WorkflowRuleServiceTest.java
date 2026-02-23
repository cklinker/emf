package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.dto.ExecuteWorkflowRequest;
import com.emf.controlplane.dto.WorkflowActionLogDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowActionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.workflow.WorkflowEngine;
import com.emf.runtime.event.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkflowRuleServiceTest {

    private WorkflowRuleRepository ruleRepository;
    private WorkflowExecutionLogRepository executionLogRepository;
    private WorkflowActionLogRepository actionLogRepository;
    private CollectionService collectionService;
    private ConfigEventPublisher configEventPublisher;
    private WorkflowEngine workflowEngine;
    private WorkflowRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        executionLogRepository = mock(WorkflowExecutionLogRepository.class);
        actionLogRepository = mock(WorkflowActionLogRepository.class);
        collectionService = mock(CollectionService.class);
        configEventPublisher = mock(ConfigEventPublisher.class);
        workflowEngine = mock(WorkflowEngine.class);
        service = new WorkflowRuleService(ruleRepository, executionLogRepository,
            actionLogRepository, collectionService, configEventPublisher, workflowEngine);
    }

    @Nested
    @DisplayName("Action Log Queries")
    class ActionLogQueryTests {

        @Test
        @DisplayName("Should list action logs by execution ID")
        void shouldListActionLogsByExecution() {
            WorkflowActionLog log1 = createActionLog("log-1", "exec-1", "FIELD_UPDATE", "SUCCESS");
            WorkflowActionLog log2 = createActionLog("log-2", "exec-1", "EMAIL_ALERT", "FAILURE");

            when(actionLogRepository.findByExecutionLogIdOrderByExecutedAtAsc("exec-1"))
                .thenReturn(List.of(log1, log2));

            List<WorkflowActionLogDto> result = service.listActionLogsByExecution("exec-1");

            assertEquals(2, result.size());
            assertEquals("FIELD_UPDATE", result.get(0).getActionType());
            assertEquals("SUCCESS", result.get(0).getStatus());
            assertEquals("EMAIL_ALERT", result.get(1).getActionType());
            assertEquals("FAILURE", result.get(1).getStatus());
        }

        @Test
        @DisplayName("Should return empty list when no action logs exist")
        void shouldReturnEmptyListWhenNoLogs() {
            when(actionLogRepository.findByExecutionLogIdOrderByExecutedAtAsc("exec-none"))
                .thenReturn(List.of());

            List<WorkflowActionLogDto> result = service.listActionLogsByExecution("exec-none");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should map all fields from entity to DTO")
        void shouldMapAllFields() {
            WorkflowActionLog log = createActionLog("log-1", "exec-1", "FIELD_UPDATE", "SUCCESS");
            log.setErrorMessage("test error");
            log.setInputSnapshot("{\"actionConfig\":\"{}\",\"recordId\":\"rec-1\"}");
            log.setOutputSnapshot("{\"updatedFields\":{\"status\":\"Done\"}}");
            log.setDurationMs(42);

            when(actionLogRepository.findByExecutionLogIdOrderByExecutedAtAsc("exec-1"))
                .thenReturn(List.of(log));

            List<WorkflowActionLogDto> result = service.listActionLogsByExecution("exec-1");

            assertEquals(1, result.size());
            WorkflowActionLogDto dto = result.get(0);
            assertEquals("log-1", dto.getId());
            assertEquals("exec-1", dto.getExecutionLogId());
            assertEquals("FIELD_UPDATE", dto.getActionType());
            assertEquals("SUCCESS", dto.getStatus());
            assertEquals("test error", dto.getErrorMessage());
            assertNotNull(dto.getInputSnapshot());
            assertNotNull(dto.getOutputSnapshot());
            assertEquals(42, dto.getDurationMs());
            assertNotNull(dto.getExecutedAt());
        }
    }

    @Nested
    @DisplayName("Trigger Fields Handling")
    class TriggerFieldsTests {

        @Test
        @DisplayName("Should store trigger fields as JSON when creating rule")
        void shouldStoreTriggerFieldsOnCreate() {
            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");
            when(collectionService.getCollection("col-1")).thenReturn(collection);
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setName("Status Trigger");
            request.setCollectionId("col-1");
            request.setTriggerType("ON_UPDATE");
            request.setTriggerFields(List.of("status", "priority"));

            var dto = service.createRule("tenant-1", request);

            assertNotNull(dto.getTriggerFields());
            assertEquals(2, dto.getTriggerFields().size());
            assertTrue(dto.getTriggerFields().contains("status"));
            assertTrue(dto.getTriggerFields().contains("priority"));
        }

        @Test
        @DisplayName("Should handle null trigger fields on create")
        void shouldHandleNullTriggerFieldsOnCreate() {
            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");
            when(collectionService.getCollection("col-1")).thenReturn(collection);
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setName("No Trigger Fields");
            request.setCollectionId("col-1");
            request.setTriggerType("ON_UPDATE");

            var dto = service.createRule("tenant-1", request);

            assertNull(dto.getTriggerFields());
        }

        @Test
        @DisplayName("Should update trigger fields")
        void shouldUpdateTriggerFields() {
            Collection collection = new Collection();
            collection.setId("col-1");

            WorkflowRule existingRule = new WorkflowRule();
            existingRule.setTenantId("tenant-1");
            existingRule.setCollection(collection);
            existingRule.setName("Test Rule");
            existingRule.setTriggerType("ON_UPDATE");

            when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(existingRule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setTriggerFields(List.of("status"));

            var dto = service.updateRule("rule-1", request);

            assertNotNull(dto.getTriggerFields());
            assertEquals(1, dto.getTriggerFields().size());
            assertEquals("status", dto.getTriggerFields().get(0));
        }
    }

    @Nested
    @DisplayName("Event Publishing")
    class EventPublishingTests {

        @Test
        @DisplayName("Should publish CREATED event when rule is created")
        void shouldPublishCreatedEvent() {
            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");
            when(collectionService.getCollection("col-1")).thenReturn(collection);
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setName("Test Rule");
            request.setCollectionId("col-1");
            request.setTriggerType("ON_CREATE");

            service.createRule("tenant-1", request);

            verify(configEventPublisher).publishWorkflowRuleChanged(any(WorkflowRule.class), eq(ChangeType.CREATED));
        }

        @Test
        @DisplayName("Should publish UPDATED event when rule is updated")
        void shouldPublishUpdatedEvent() {
            Collection collection = new Collection();
            collection.setId("col-1");

            WorkflowRule existingRule = new WorkflowRule();
            existingRule.setTenantId("tenant-1");
            existingRule.setCollection(collection);
            existingRule.setName("Original Rule");
            existingRule.setTriggerType("ON_CREATE");

            when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(existingRule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setName("Updated Rule");

            service.updateRule("rule-1", request);

            verify(configEventPublisher).publishWorkflowRuleChanged(any(WorkflowRule.class), eq(ChangeType.UPDATED));
        }

        @Test
        @DisplayName("Should publish DELETED event when rule is deleted")
        void shouldPublishDeletedEvent() {
            Collection collection = new Collection();
            collection.setId("col-1");

            WorkflowRule existingRule = new WorkflowRule();
            existingRule.setTenantId("tenant-1");
            existingRule.setCollection(collection);
            existingRule.setName("Delete Me");
            existingRule.setTriggerType("ON_CREATE");

            when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(existingRule));

            service.deleteRule("rule-1");

            verify(configEventPublisher).publishWorkflowRuleChanged(any(WorkflowRule.class), eq(ChangeType.DELETED));
            verify(ruleRepository).delete(existingRule);
        }

        @Test
        @DisplayName("Should work when configEventPublisher is null (Kafka disabled)")
        void shouldWorkWithNullPublisher() {
            WorkflowRuleService serviceNoKafka = new WorkflowRuleService(
                ruleRepository, executionLogRepository, actionLogRepository,
                collectionService, null, null);

            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");
            when(collectionService.getCollection("col-1")).thenReturn(collection);
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setName("Test Rule");
            request.setCollectionId("col-1");
            request.setTriggerType("ON_CREATE");

            // Should not throw
            assertDoesNotThrow(() -> serviceNoKafka.createRule("tenant-1", request));
        }
    }

    @Nested
    @DisplayName("Manual Execution (B3)")
    class ManualExecutionTests {

        @Test
        @DisplayName("Should execute for each record ID")
        void shouldExecuteForEachRecordId() {
            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");

            WorkflowRule rule = new WorkflowRule();
            rule.setTenantId("tenant-1");
            rule.setCollection(collection);
            rule.setName("Manual Rule");
            rule.setTriggerType("MANUAL");

            when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(rule));
            when(workflowEngine.executeManualRule(any(), any(), any())).thenReturn("log-1", "log-2");

            ExecuteWorkflowRequest request = new ExecuteWorkflowRequest(List.of("rec-1", "rec-2"));
            List<String> result = service.executeManual("rule-1", request, "user-1");

            assertEquals(2, result.size());
            verify(workflowEngine).executeManualRule(rule, "rec-1", "user-1");
            verify(workflowEngine).executeManualRule(rule, "rec-2", "user-1");
        }

        @Test
        @DisplayName("Should execute without record IDs")
        void shouldExecuteWithoutRecordIds() {
            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");

            WorkflowRule rule = new WorkflowRule();
            rule.setTenantId("tenant-1");
            rule.setCollection(collection);
            rule.setName("Manual Rule");
            rule.setTriggerType("MANUAL");

            when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(rule));
            when(workflowEngine.executeManualRule(any(), any(), any())).thenReturn("log-1");

            ExecuteWorkflowRequest request = new ExecuteWorkflowRequest();
            List<String> result = service.executeManual("rule-1", request, "user-1");

            assertEquals(1, result.size());
            verify(workflowEngine).executeManualRule(rule, null, "user-1");
        }

        @Test
        @DisplayName("Should filter out null execution log IDs")
        void shouldFilterNullLogIds() {
            Collection collection = new Collection();
            collection.setId("col-1");
            collection.setName("orders");

            WorkflowRule rule = new WorkflowRule();
            rule.setTenantId("tenant-1");
            rule.setCollection(collection);
            rule.setName("Manual Rule");
            rule.setTriggerType("MANUAL");

            when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(rule));
            // First returns a log ID, second returns null (no active actions)
            when(workflowEngine.executeManualRule(any(), eq("rec-1"), any())).thenReturn("log-1");
            when(workflowEngine.executeManualRule(any(), eq("rec-2"), any())).thenReturn(null);

            ExecuteWorkflowRequest request = new ExecuteWorkflowRequest(List.of("rec-1", "rec-2"));
            List<String> result = service.executeManual("rule-1", request, "user-1");

            assertEquals(1, result.size());
            assertEquals("log-1", result.get(0));
        }

        @Test
        @DisplayName("Should throw when rule not found")
        void shouldThrowWhenRuleNotFound() {
            when(ruleRepository.findById("nonexistent")).thenReturn(Optional.empty());

            ExecuteWorkflowRequest request = new ExecuteWorkflowRequest(List.of("rec-1"));

            assertThrows(Exception.class, () ->
                service.executeManual("nonexistent", request, "user-1"));
        }
    }

    private WorkflowActionLog createActionLog(String id, String executionLogId,
                                               String actionType, String status) {
        WorkflowActionLog log = new WorkflowActionLog();
        // Use reflection to set ID since BaseEntity normally auto-generates it
        try {
            var idField = log.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(log, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.setExecutionLogId(executionLogId);
        log.setActionType(actionType);
        log.setStatus(status);
        log.setExecutedAt(Instant.now());
        return log;
    }
}
