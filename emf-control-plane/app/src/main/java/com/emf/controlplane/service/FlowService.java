package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateFlowRequest;
import com.emf.controlplane.entity.Flow;
import com.emf.controlplane.entity.FlowExecution;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.FlowExecutionRepository;
import com.emf.controlplane.repository.FlowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FlowService {

    private static final Logger log = LoggerFactory.getLogger(FlowService.class);

    private final FlowRepository flowRepository;
    private final FlowExecutionRepository executionRepository;

    public FlowService(FlowRepository flowRepository,
                       FlowExecutionRepository executionRepository) {
        this.flowRepository = flowRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public List<Flow> listFlows(String tenantId) {
        return flowRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Flow getFlow(String id) {
        return flowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flow", id));
    }

    @Transactional
    @SetupAudited(section = "Flows", entityType = "Flow")
    public Flow createFlow(String tenantId, String userId, CreateFlowRequest request) {
        log.info("Creating flow '{}' for tenant: {}", request.getName(), tenantId);

        Flow flow = new Flow();
        flow.setTenantId(tenantId);
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        flow.setFlowType(request.getFlowType());
        flow.setActive(request.getActive() != null ? request.getActive() : false);
        flow.setTriggerConfig(request.getTriggerConfig());
        flow.setDefinition(request.getDefinition());
        flow.setCreatedBy(userId);

        return flowRepository.save(flow);
    }

    @Transactional
    @SetupAudited(section = "Flows", entityType = "Flow")
    public Flow updateFlow(String id, CreateFlowRequest request) {
        log.info("Updating flow: {}", id);
        Flow flow = getFlow(id);

        if (request.getName() != null) flow.setName(request.getName());
        if (request.getDescription() != null) flow.setDescription(request.getDescription());
        if (request.getFlowType() != null) flow.setFlowType(request.getFlowType());
        if (request.getActive() != null) flow.setActive(request.getActive());
        if (request.getTriggerConfig() != null) flow.setTriggerConfig(request.getTriggerConfig());
        if (request.getDefinition() != null) flow.setDefinition(request.getDefinition());

        return flowRepository.save(flow);
    }

    @Transactional
    @SetupAudited(section = "Flows", entityType = "Flow")
    public void deleteFlow(String id) {
        log.info("Deleting flow: {}", id);
        Flow flow = getFlow(id);
        flowRepository.delete(flow);
    }

    // --- Executions ---

    @Transactional(readOnly = true)
    public List<FlowExecution> listExecutions(String tenantId) {
        return executionRepository.findByTenantIdOrderByStartedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<FlowExecution> listExecutionsByFlow(String flowId) {
        return executionRepository.findByFlowIdOrderByStartedAtDesc(flowId);
    }

    @Transactional(readOnly = true)
    public FlowExecution getExecution(String id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlowExecution", id));
    }

    @Transactional
    public FlowExecution startExecution(String tenantId, String flowId, String userId, String triggerRecordId) {
        log.info("Starting flow execution for flow: {}", flowId);
        Flow flow = getFlow(flowId);

        FlowExecution execution = new FlowExecution();
        execution.setTenantId(tenantId);
        execution.setFlow(flow);
        execution.setStatus("RUNNING");
        execution.setStartedBy(userId);
        execution.setTriggerRecordId(triggerRecordId);
        execution.setStartedAt(Instant.now());

        return executionRepository.save(execution);
    }

    @Transactional
    public FlowExecution cancelExecution(String id) {
        log.info("Cancelling flow execution: {}", id);
        FlowExecution execution = getExecution(id);
        execution.setStatus("CANCELLED");
        execution.setCompletedAt(Instant.now());
        return executionRepository.save(execution);
    }
}
