package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.dto.WorkflowActionLogDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowActionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
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
    private WorkflowRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        executionLogRepository = mock(WorkflowExecutionLogRepository.class);
        actionLogRepository = mock(WorkflowActionLogRepository.class);
        collectionService = mock(CollectionService.class);
        configEventPublisher = mock(ConfigEventPublisher.class);
        service = new WorkflowRuleService(ruleRepository, executionLogRepository,
            actionLogRepository, collectionService, configEventPublisher);
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
                collectionService, null);

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
