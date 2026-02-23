package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateWorkflowRuleRequest;
import com.emf.controlplane.dto.ExecuteWorkflowRequest;
import com.emf.controlplane.dto.WorkflowActionLogDto;
import com.emf.controlplane.dto.WorkflowAnalyticsDto;
import com.emf.controlplane.dto.WorkflowRuleDto;
import com.emf.controlplane.dto.WorkflowRuleVersionDto;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.entity.WorkflowRuleVersion;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.repository.WorkflowRuleVersionRepository;
import com.emf.controlplane.service.workflow.WorkflowEngine;
import com.emf.runtime.event.ChangeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkflowRuleService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuleService.class);

    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowExecutionLogRepository logRepository;
    private final WorkflowActionLogRepository actionLogRepository;
    private final WorkflowRuleVersionRepository versionRepository;
    private final CollectionService collectionService;
    private final ConfigEventPublisher configEventPublisher;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public WorkflowRuleService(WorkflowRuleRepository ruleRepository,
                               WorkflowExecutionLogRepository logRepository,
                               WorkflowActionLogRepository actionLogRepository,
                               WorkflowRuleVersionRepository versionRepository,
                               CollectionService collectionService,
                               @Nullable ConfigEventPublisher configEventPublisher,
                               @Nullable WorkflowEngine workflowEngine,
                               ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
        this.actionLogRepository = actionLogRepository;
        this.versionRepository = versionRepository;
        this.collectionService = collectionService;
        this.configEventPublisher = configEventPublisher;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
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
        rule.setExecutionMode(request.getExecutionMode() != null ? request.getExecutionMode() : "SEQUENTIAL");

        applyActions(rule, request.getActions());

        WorkflowRule saved = ruleRepository.save(rule);

        // Create initial version (version 1)
        createVersion(saved, "Initial version");

        WorkflowRuleDto dto = WorkflowRuleDto.fromEntity(saved);
        publishWorkflowChanged(saved, ChangeType.CREATED);
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
        if (request.getExecutionMode() != null) rule.setExecutionMode(request.getExecutionMode());

        if (request.getCollectionId() != null) {
            Collection collection = collectionService.getCollection(request.getCollectionId());
            rule.setCollection(collection);
        }

        if (request.getActions() != null) {
            rule.getActions().clear();
            applyActions(rule, request.getActions());
        }

        WorkflowRule saved = ruleRepository.save(rule);

        // Create new version on every update
        createVersion(saved, null);

        WorkflowRuleDto dto = WorkflowRuleDto.fromEntity(saved);
        publishWorkflowChanged(saved, ChangeType.UPDATED);
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

    private void applyActions(WorkflowRule rule, List<CreateWorkflowRuleRequest.ActionRequest> actionRequests) {
        if (actionRequests == null) return;
        for (CreateWorkflowRuleRequest.ActionRequest actionReq : actionRequests) {
            WorkflowAction action = new WorkflowAction();
            action.setWorkflowRule(rule);
            action.setActionType(actionReq.getActionType());
            action.setExecutionOrder(actionReq.getExecutionOrder() != null ? actionReq.getExecutionOrder() : 0);
            action.setConfig(actionReq.getConfig());
            action.setActive(actionReq.getActive() != null ? actionReq.getActive() : true);
            action.setRetryCount(actionReq.getRetryCount() != null ? actionReq.getRetryCount() : 0);
            action.setRetryDelaySeconds(actionReq.getRetryDelaySeconds() != null ? actionReq.getRetryDelaySeconds() : 60);
            action.setRetryBackoff(actionReq.getRetryBackoff() != null ? actionReq.getRetryBackoff() : "FIXED");
            rule.getActions().add(action);
        }
    }

    // --- Versioning ---

    /**
     * Creates a version snapshot of the current rule state.
     */
    void createVersion(WorkflowRule rule, String changeSummary) {
        try {
            int nextVersion = versionRepository.findMaxVersionNumber(rule.getId()) + 1;

            WorkflowRuleVersion version = new WorkflowRuleVersion();
            version.setWorkflowRuleId(rule.getId());
            version.setVersionNumber(nextVersion);
            version.setSnapshot(serializeRuleSnapshot(rule));
            version.setChangeSummary(changeSummary);
            versionRepository.save(version);

            log.debug("Created version {} for workflow rule '{}'", nextVersion, rule.getName());
        } catch (Exception e) {
            log.warn("Failed to create version for workflow rule '{}': {}", rule.getId(), e.getMessage());
        }
    }

    /**
     * Gets the current version number for a workflow rule.
     */
    @Transactional(readOnly = true)
    public int getCurrentVersion(String ruleId) {
        return versionRepository.findMaxVersionNumber(ruleId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRuleVersionDto> listVersions(String ruleId) {
        return versionRepository.findByWorkflowRuleIdOrderByVersionNumberDesc(ruleId).stream()
                .map(WorkflowRuleVersionDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowRuleVersionDto getVersion(String ruleId, int versionNumber) {
        return versionRepository.findByWorkflowRuleIdAndVersionNumber(ruleId, versionNumber)
                .map(WorkflowRuleVersionDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowRuleVersion",
                    ruleId + "/v" + versionNumber));
    }

    /**
     * Serializes a workflow rule + actions to a JSON string for version storage.
     */
    String serializeRuleSnapshot(WorkflowRule rule) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", rule.getName());
            snapshot.put("description", rule.getDescription());
            snapshot.put("active", rule.isActive());
            snapshot.put("triggerType", rule.getTriggerType());
            snapshot.put("filterFormula", rule.getFilterFormula());
            snapshot.put("executionOrder", rule.getExecutionOrder());
            snapshot.put("errorHandling", rule.getErrorHandling());
            snapshot.put("triggerFields", rule.getTriggerFields());
            snapshot.put("cronExpression", rule.getCronExpression());
            snapshot.put("timezone", rule.getTimezone());
            snapshot.put("executionMode", rule.getExecutionMode());

            List<Map<String, Object>> actionList = new ArrayList<>();
            for (WorkflowAction action : rule.getActions()) {
                Map<String, Object> actionMap = new LinkedHashMap<>();
                actionMap.put("actionType", action.getActionType());
                actionMap.put("executionOrder", action.getExecutionOrder());
                actionMap.put("config", action.getConfig());
                actionMap.put("active", action.isActive());
                actionMap.put("retryCount", action.getRetryCount());
                actionMap.put("retryDelaySeconds", action.getRetryDelaySeconds());
                actionMap.put("retryBackoff", action.getRetryBackoff());
                actionList.add(actionMap);
            }
            snapshot.put("actions", actionList);

            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("Failed to serialize rule snapshot: {}", e.getMessage());
            return "{}";
        }
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

    // --- Manual Execution ---

    @Transactional
    public List<String> executeManual(String ruleId, ExecuteWorkflowRequest request, String userId) {
        WorkflowRule rule = getRule(ruleId);

        if (workflowEngine == null) {
            throw new IllegalStateException("Workflow engine not available");
        }

        List<String> executionLogIds = new ArrayList<>();

        if (request.getRecordIds() != null && !request.getRecordIds().isEmpty()) {
            for (String recordId : request.getRecordIds()) {
                String logId = workflowEngine.executeManualRule(rule, recordId, userId);
                if (logId != null) {
                    executionLogIds.add(logId);
                }
            }
        } else {
            String logId = workflowEngine.executeManualRule(rule, null, userId);
            if (logId != null) {
                executionLogIds.add(logId);
            }
        }

        log.info("Manual execution of rule '{}' completed: {} execution logs created",
            rule.getName(), executionLogIds.size());

        return executionLogIds;
    }

    // --- Analytics ---

    /**
     * Gets aggregate workflow analytics for a tenant.
     */
    @Transactional(readOnly = true)
    public WorkflowAnalyticsDto getAnalytics(String tenantId, Instant startDate, Instant endDate) {
        List<WorkflowExecutionLog> logs;
        if (startDate != null && endDate != null) {
            logs = logRepository.findByTenantIdAndExecutedAtBetweenOrderByExecutedAtDesc(
                tenantId, startDate, endDate);
        } else {
            logs = logRepository.findByTenantIdOrderByExecutedAtDesc(tenantId);
        }

        return buildAnalytics(logs);
    }

    /**
     * Gets analytics for a specific workflow rule.
     */
    @Transactional(readOnly = true)
    public WorkflowAnalyticsDto getRuleAnalytics(String ruleId, Instant startDate, Instant endDate) {
        List<WorkflowExecutionLog> logs;
        if (startDate != null && endDate != null) {
            logs = logRepository.findByWorkflowRuleIdAndExecutedAtBetweenOrderByExecutedAtDesc(
                ruleId, startDate, endDate);
        } else {
            logs = logRepository.findByWorkflowRuleIdOrderByExecutedAtDesc(ruleId);
        }

        return buildAnalytics(logs);
    }

    private WorkflowAnalyticsDto buildAnalytics(List<WorkflowExecutionLog> logs) {
        WorkflowAnalyticsDto analytics = new WorkflowAnalyticsDto();
        analytics.setTotalExecutions(logs.size());

        long successCount = logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long failureCount = logs.stream().filter(l -> "FAILURE".equals(l.getStatus())).count();
        long partialCount = logs.stream().filter(l -> "PARTIAL_FAILURE".equals(l.getStatus())).count();

        analytics.setSuccessCount(successCount);
        analytics.setFailureCount(failureCount);
        analytics.setPartialFailureCount(partialCount);
        analytics.setSuccessRate(logs.isEmpty() ? 0.0 : (double) successCount / logs.size() * 100.0);

        Double avgDuration = logs.stream()
                .filter(l -> l.getDurationMs() != null)
                .mapToInt(WorkflowExecutionLog::getDurationMs)
                .average()
                .orElse(0.0);
        analytics.setAvgDurationMs(avgDuration > 0 ? avgDuration : null);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("SUCCESS", successCount);
        byStatus.put("FAILURE", failureCount);
        byStatus.put("PARTIAL_FAILURE", partialCount);
        long otherCount = logs.size() - successCount - failureCount - partialCount;
        if (otherCount > 0) {
            byStatus.put("OTHER", otherCount);
        }
        analytics.setExecutionsByStatus(byStatus);

        Map<String, Long> byTrigger = logs.stream()
                .collect(Collectors.groupingBy(
                    WorkflowExecutionLog::getTriggerType,
                    LinkedHashMap::new,
                    Collectors.counting()));
        analytics.setExecutionsByTriggerType(byTrigger);

        return analytics;
    }

    // --- Log Retention ---

    /**
     * Deletes workflow execution logs and associated action logs older than the specified date.
     *
     * @param cutoffDate logs older than this date will be deleted
     * @return the number of execution logs deleted
     */
    @Transactional
    public int deleteLogsOlderThan(Instant cutoffDate) {
        List<WorkflowExecutionLog> oldLogs = logRepository.findByExecutedAtBefore(cutoffDate);
        if (oldLogs.isEmpty()) {
            return 0;
        }

        int actionLogsDeleted = 0;
        for (WorkflowExecutionLog executionLog : oldLogs) {
            var actionLogs = actionLogRepository.findByExecutionLogIdOrderByExecutedAtAsc(executionLog.getId());
            actionLogRepository.deleteAll(actionLogs);
            actionLogsDeleted += actionLogs.size();
        }

        logRepository.deleteAll(oldLogs);

        log.info("Log retention cleanup: deleted {} execution logs and {} action logs older than {}",
            oldLogs.size(), actionLogsDeleted, cutoffDate);

        return oldLogs.size();
    }

    // --- Event Publishing ---

    private void publishWorkflowChanged(WorkflowRule rule, ChangeType changeType) {
        if (configEventPublisher != null) {
            configEventPublisher.publishWorkflowRuleChanged(rule, changeType);
        }
    }
}
