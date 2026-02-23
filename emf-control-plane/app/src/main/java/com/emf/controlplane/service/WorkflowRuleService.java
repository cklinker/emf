package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.dto.WorkflowActionLogDto;
import com.emf.controlplane.dto.WorkflowRuleDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.runtime.event.ChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkflowRuleService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuleService.class);

    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowExecutionLogRepository logRepository;
    private final WorkflowActionLogRepository actionLogRepository;
    private final CollectionService collectionService;
    private final ConfigEventPublisher configEventPublisher;

    public WorkflowRuleService(WorkflowRuleRepository ruleRepository,
                               WorkflowExecutionLogRepository logRepository,
                               WorkflowActionLogRepository actionLogRepository,
                               CollectionService collectionService,
                               @Nullable ConfigEventPublisher configEventPublisher) {
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
        this.actionLogRepository = actionLogRepository;
        this.collectionService = collectionService;
        this.configEventPublisher = configEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<WorkflowRuleDto> listRuleDtos(String tenantId) {
        return ruleRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(WorkflowRuleDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowRuleDto getRuleDto(String id) {
        return WorkflowRuleDto.fromEntity(ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowRule", id)));
    }

    @Transactional(readOnly = true)
    public WorkflowRule getRule(String id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowRule", id));
    }

    @Transactional
    @SetupAudited(section = "WorkflowRules", entityType = "WorkflowRule")
    public WorkflowRuleDto createRule(String tenantId, CreateWorkflowRuleRequest request) {
        log.info("Creating workflow rule '{}' for tenant: {}", request.getName(), tenantId);

        Collection collection = collectionService.getCollection(request.getCollectionId());

        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId(tenantId);
        rule.setCollection(collection);
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setActive(request.getActive() != null ? request.getActive() : true);
        rule.setTriggerType(request.getTriggerType());
        rule.setFilterFormula(request.getFilterFormula());
        rule.setReEvaluateOnUpdate(request.getReEvaluateOnUpdate() != null ? request.getReEvaluateOnUpdate() : false);
        rule.setExecutionOrder(request.getExecutionOrder() != null ? request.getExecutionOrder() : 0);
        rule.setErrorHandling(request.getErrorHandling() != null ? request.getErrorHandling() : "STOP_ON_ERROR");
        rule.setTriggerFields(WorkflowRuleDto.serializeTriggerFields(request.getTriggerFields()));
        rule.setCronExpression(request.getCronExpression());
        rule.setTimezone(request.getTimezone());

        if (request.getActions() != null) {
            for (CreateWorkflowRuleRequest.ActionRequest actionReq : request.getActions()) {
                WorkflowAction action = new WorkflowAction();
                action.setWorkflowRule(rule);
                action.setActionType(actionReq.getActionType());
                action.setExecutionOrder(actionReq.getExecutionOrder() != null ? actionReq.getExecutionOrder() : 0);
                action.setConfig(actionReq.getConfig());
                action.setActive(actionReq.getActive() != null ? actionReq.getActive() : true);
                rule.getActions().add(action);
            }
        }

        WorkflowRuleDto dto = WorkflowRuleDto.fromEntity(ruleRepository.save(rule));
        publishWorkflowChanged(rule, ChangeType.CREATED);
        return dto;
    }

    @Transactional
    @SetupAudited(section = "WorkflowRules", entityType = "WorkflowRule")
    public WorkflowRuleDto updateRule(String id, CreateWorkflowRuleRequest request) {
        log.info("Updating workflow rule: {}", id);
        WorkflowRule rule = getRule(id);

        if (request.getName() != null) rule.setName(request.getName());
        if (request.getDescription() != null) rule.setDescription(request.getDescription());
        if (request.getActive() != null) rule.setActive(request.getActive());
        if (request.getTriggerType() != null) rule.setTriggerType(request.getTriggerType());
        if (request.getFilterFormula() != null) rule.setFilterFormula(request.getFilterFormula());
        if (request.getReEvaluateOnUpdate() != null) rule.setReEvaluateOnUpdate(request.getReEvaluateOnUpdate());
        if (request.getExecutionOrder() != null) rule.setExecutionOrder(request.getExecutionOrder());
        if (request.getErrorHandling() != null) rule.setErrorHandling(request.getErrorHandling());
        if (request.getTriggerFields() != null) rule.setTriggerFields(WorkflowRuleDto.serializeTriggerFields(request.getTriggerFields()));
        if (request.getCronExpression() != null) rule.setCronExpression(request.getCronExpression());
        if (request.getTimezone() != null) rule.setTimezone(request.getTimezone());

        if (request.getCollectionId() != null) {
            Collection collection = collectionService.getCollection(request.getCollectionId());
            rule.setCollection(collection);
        }

        if (request.getActions() != null) {
            rule.getActions().clear();
            for (CreateWorkflowRuleRequest.ActionRequest actionReq : request.getActions()) {
                WorkflowAction action = new WorkflowAction();
                action.setWorkflowRule(rule);
                action.setActionType(actionReq.getActionType());
                action.setExecutionOrder(actionReq.getExecutionOrder() != null ? actionReq.getExecutionOrder() : 0);
                action.setConfig(actionReq.getConfig());
                action.setActive(actionReq.getActive() != null ? actionReq.getActive() : true);
                rule.getActions().add(action);
            }
        }

        WorkflowRuleDto dto = WorkflowRuleDto.fromEntity(ruleRepository.save(rule));
        publishWorkflowChanged(rule, ChangeType.UPDATED);
        return dto;
    }

    @Transactional
    @SetupAudited(section = "WorkflowRules", entityType = "WorkflowRule")
    public void deleteRule(String id) {
        log.info("Deleting workflow rule: {}", id);
        WorkflowRule rule = getRule(id);
        // Publish event before delete so payload can access lazy-loaded fields
        publishWorkflowChanged(rule, ChangeType.DELETED);
        ruleRepository.delete(rule);
    }

    // --- Execution Logs ---

    @Transactional(readOnly = true)
    public List<WorkflowExecutionLog> listExecutionLogs(String tenantId) {
        return logRepository.findByTenantIdOrderByExecutedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecutionLog> listExecutionLogsByRule(String ruleId) {
        return logRepository.findByWorkflowRuleIdOrderByExecutedAtDesc(ruleId);
    }

    // --- Action Logs ---

    @Transactional(readOnly = true)
    public List<WorkflowActionLogDto> listActionLogsByExecution(String executionLogId) {
        return actionLogRepository.findByExecutionLogIdOrderByExecutedAtAsc(executionLogId)
                .stream().map(WorkflowActionLogDto::fromEntity).toList();
    }

    // --- Event Publishing ---

    private void publishWorkflowChanged(WorkflowRule rule, ChangeType changeType) {
        if (configEventPublisher != null) {
            configEventPublisher.publishWorkflowRuleChanged(rule, changeType);
        }
    }
}
