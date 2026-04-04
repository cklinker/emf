package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowRuleService")
class WorkflowRuleServiceTest {

    @Mock private WorkflowRuleRepository ruleRepository;
    @Mock private WorkflowExecutionLogRepository logRepository;
    @Mock private CollectionService collectionService;

    private WorkflowRuleService service;

    private static final String TENANT_ID = "tenant-1";
    private static final String RULE_ID = "rule-1";
    private static final String COLLECTION_ID = "col-1";

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT_ID, "test-tenant");
        service = new WorkflowRuleService(ruleRepository, logRepository, collectionService);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Rule CRUD operations")
    class RuleCrudTests {

        @Test
        @DisplayName("should list rules by tenant")
        void listRulesByTenant() {
            WorkflowRule rule = new WorkflowRule();
            rule.setName("Test Rule");
            when(ruleRepository.findByTenantIdOrderByNameAsc(TENANT_ID))
                    .thenReturn(List.of(rule));

            List<WorkflowRule> result = service.listRules(TENANT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Test Rule");
        }

        @Test
        @DisplayName("should get rule by id")
        void getRuleById() {
            WorkflowRule rule = new WorkflowRule();
            rule.setName("Test Rule");
            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

            WorkflowRule result = service.getRule(RULE_ID);

            assertThat(result.getName()).isEqualTo("Test Rule");
        }

        @Test
        @DisplayName("should throw when rule not found")
        void throwWhenRuleNotFound() {
            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRule(RULE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should create rule with defaults")
        void createRuleWithDefaults() {
            Collection collection = new Collection();
            when(collectionService.getCollection(COLLECTION_ID)).thenReturn(collection);
            when(ruleRepository.save(any(WorkflowRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setCollectionId(COLLECTION_ID);
            request.setName("New Rule");
            request.setTriggerType("ON_CREATE");

            WorkflowRule result = service.createRule(TENANT_ID, request);

            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getName()).isEqualTo("New Rule");
            assertThat(result.getTriggerType()).isEqualTo("ON_CREATE");
            assertThat(result.isActive()).isTrue();
            assertThat(result.isReEvaluateOnUpdate()).isFalse();
            assertThat(result.getExecutionOrder()).isEqualTo(0);
            assertThat(result.getCollection()).isEqualTo(collection);
        }

        @Test
        @DisplayName("should create rule with actions")
        void createRuleWithActions() {
            Collection collection = new Collection();
            when(collectionService.getCollection(COLLECTION_ID)).thenReturn(collection);
            when(ruleRepository.save(any(WorkflowRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateWorkflowRuleRequest.ActionRequest actionReq = new CreateWorkflowRuleRequest.ActionRequest();
            actionReq.setActionType("FIELD_UPDATE");
            actionReq.setConfig("{\"field\":\"status\",\"value\":\"active\"}");

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setCollectionId(COLLECTION_ID);
            request.setName("Rule With Actions");
            request.setTriggerType("ON_CREATE");
            request.setActions(List.of(actionReq));

            WorkflowRule result = service.createRule(TENANT_ID, request);

            assertThat(result.getActions()).hasSize(1);
            WorkflowAction action = result.getActions().get(0);
            assertThat(action.getActionType()).isEqualTo("FIELD_UPDATE");
            assertThat(action.getConfig()).contains("status");
            assertThat(action.isActive()).isTrue();
            assertThat(action.getExecutionOrder()).isEqualTo(0);
            assertThat(action.getWorkflowRule()).isEqualTo(result);
        }

        @Test
        @DisplayName("should update rule fields selectively")
        void updateRuleSelectively() {
            WorkflowRule existing = new WorkflowRule();
            existing.setName("Old Name");
            existing.setDescription("Old Desc");
            existing.setTriggerType("ON_CREATE");
            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(WorkflowRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setName("New Name");

            WorkflowRule result = service.updateRule(RULE_ID, request);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("Old Desc");
            assertThat(result.getTriggerType()).isEqualTo("ON_CREATE");
        }

        @Test
        @DisplayName("should replace actions on update")
        void replaceActionsOnUpdate() {
            WorkflowRule existing = new WorkflowRule();
            existing.setName("Rule");
            WorkflowAction oldAction = new WorkflowAction();
            oldAction.setActionType("OLD_TYPE");
            existing.getActions().add(oldAction);

            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(existing));
            when(ruleRepository.save(any(WorkflowRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateWorkflowRuleRequest.ActionRequest newActionReq = new CreateWorkflowRuleRequest.ActionRequest();
            newActionReq.setActionType("EMAIL_ALERT");
            newActionReq.setConfig("{\"to\":\"admin@example.com\"}");

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setActions(List.of(newActionReq));

            WorkflowRule result = service.updateRule(RULE_ID, request);

            assertThat(result.getActions()).hasSize(1);
            assertThat(result.getActions().get(0).getActionType()).isEqualTo("EMAIL_ALERT");
        }

        @Test
        @DisplayName("should update collection when collectionId provided")
        void updateCollection() {
            WorkflowRule existing = new WorkflowRule();
            existing.setName("Rule");
            Collection newCollection = new Collection();
            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(existing));
            when(collectionService.getCollection(COLLECTION_ID)).thenReturn(newCollection);
            when(ruleRepository.save(any(WorkflowRule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateWorkflowRuleRequest request = new CreateWorkflowRuleRequest();
            request.setCollectionId(COLLECTION_ID);

            WorkflowRule result = service.updateRule(RULE_ID, request);

            assertThat(result.getCollection()).isEqualTo(newCollection);
        }

        @Test
        @DisplayName("should delete rule")
        void deleteRule() {
            WorkflowRule rule = new WorkflowRule();
            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

            service.deleteRule(RULE_ID);

            verify(ruleRepository).delete(rule);
        }

        @Test
        @DisplayName("should throw when deleting non-existent rule")
        void throwWhenDeletingNonExistent() {
            when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteRule(RULE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Execution Log operations")
    class ExecutionLogTests {

        @Test
        @DisplayName("should list execution logs by tenant")
        void listLogsByTenant() {
            WorkflowExecutionLog log = new WorkflowExecutionLog();
            when(logRepository.findByTenantIdOrderByExecutedAtDesc(TENANT_ID))
                    .thenReturn(List.of(log));

            List<WorkflowExecutionLog> result = service.listExecutionLogs(TENANT_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should list execution logs by rule")
        void listLogsByRule() {
            WorkflowExecutionLog log = new WorkflowExecutionLog();
            when(logRepository.findByWorkflowRuleIdOrderByExecutedAtDesc(RULE_ID))
                    .thenReturn(List.of(log));

            List<WorkflowExecutionLog> result = service.listExecutionLogsByRule(RULE_ID);

            assertThat(result).hasSize(1);
            verify(logRepository).findByWorkflowRuleIdOrderByExecutedAtDesc(RULE_ID);
        }
    }
}
