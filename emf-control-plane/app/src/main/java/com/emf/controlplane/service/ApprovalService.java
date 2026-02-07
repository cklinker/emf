package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateApprovalProcessRequest;
import com.emf.controlplane.entity.*;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ApprovalInstanceRepository;
import com.emf.controlplane.repository.ApprovalProcessRepository;
import com.emf.controlplane.repository.ApprovalStepInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalProcessRepository processRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalStepInstanceRepository stepInstanceRepository;
    private final CollectionService collectionService;

    public ApprovalService(ApprovalProcessRepository processRepository,
                           ApprovalInstanceRepository instanceRepository,
                           ApprovalStepInstanceRepository stepInstanceRepository,
                           CollectionService collectionService) {
        this.processRepository = processRepository;
        this.instanceRepository = instanceRepository;
        this.stepInstanceRepository = stepInstanceRepository;
        this.collectionService = collectionService;
    }

    // --- Process CRUD ---

    @Transactional(readOnly = true)
    public List<ApprovalProcess> listProcesses(String tenantId) {
        return processRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public ApprovalProcess getProcess(String id) {
        return processRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalProcess", id));
    }

    @Transactional
    @SetupAudited(section = "Approvals", entityType = "ApprovalProcess")
    public ApprovalProcess createProcess(String tenantId, CreateApprovalProcessRequest request) {
        log.info("Creating approval process '{}' for tenant: {}", request.getName(), tenantId);

        Collection collection = collectionService.getCollection(request.getCollectionId());

        ApprovalProcess process = new ApprovalProcess();
        process.setTenantId(tenantId);
        process.setCollection(collection);
        process.setName(request.getName());
        process.setDescription(request.getDescription());
        process.setActive(request.getActive() != null ? request.getActive() : true);
        process.setEntryCriteria(request.getEntryCriteria());
        process.setRecordEditability(request.getRecordEditability() != null ? request.getRecordEditability() : "LOCKED");
        process.setInitialSubmitterField(request.getInitialSubmitterField());
        process.setOnSubmitFieldUpdates(request.getOnSubmitFieldUpdates() != null ? request.getOnSubmitFieldUpdates() : "[]");
        process.setOnApprovalFieldUpdates(request.getOnApprovalFieldUpdates() != null ? request.getOnApprovalFieldUpdates() : "[]");
        process.setOnRejectionFieldUpdates(request.getOnRejectionFieldUpdates() != null ? request.getOnRejectionFieldUpdates() : "[]");
        process.setOnRecallFieldUpdates(request.getOnRecallFieldUpdates() != null ? request.getOnRecallFieldUpdates() : "[]");
        process.setAllowRecall(request.getAllowRecall() != null ? request.getAllowRecall() : true);
        process.setExecutionOrder(request.getExecutionOrder() != null ? request.getExecutionOrder() : 0);

        if (request.getSteps() != null) {
            for (CreateApprovalProcessRequest.StepRequest stepReq : request.getSteps()) {
                ApprovalStep step = new ApprovalStep();
                step.setApprovalProcess(process);
                step.setStepNumber(stepReq.getStepNumber());
                step.setName(stepReq.getName());
                step.setDescription(stepReq.getDescription());
                step.setEntryCriteria(stepReq.getEntryCriteria());
                step.setApproverType(stepReq.getApproverType());
                step.setApproverId(stepReq.getApproverId());
                step.setApproverField(stepReq.getApproverField());
                step.setUnanimityRequired(stepReq.getUnanimityRequired() != null ? stepReq.getUnanimityRequired() : false);
                step.setEscalationTimeoutHours(stepReq.getEscalationTimeoutHours());
                step.setEscalationAction(stepReq.getEscalationAction());
                step.setOnApproveAction(stepReq.getOnApproveAction() != null ? stepReq.getOnApproveAction() : "NEXT_STEP");
                step.setOnRejectAction(stepReq.getOnRejectAction() != null ? stepReq.getOnRejectAction() : "REJECT_FINAL");
                process.getSteps().add(step);
            }
        }

        return processRepository.save(process);
    }

    @Transactional
    @SetupAudited(section = "Approvals", entityType = "ApprovalProcess")
    public ApprovalProcess updateProcess(String id, CreateApprovalProcessRequest request) {
        log.info("Updating approval process: {}", id);
        ApprovalProcess process = getProcess(id);

        if (request.getName() != null) process.setName(request.getName());
        if (request.getDescription() != null) process.setDescription(request.getDescription());
        if (request.getActive() != null) process.setActive(request.getActive());
        if (request.getEntryCriteria() != null) process.setEntryCriteria(request.getEntryCriteria());
        if (request.getRecordEditability() != null) process.setRecordEditability(request.getRecordEditability());
        if (request.getInitialSubmitterField() != null) process.setInitialSubmitterField(request.getInitialSubmitterField());
        if (request.getOnSubmitFieldUpdates() != null) process.setOnSubmitFieldUpdates(request.getOnSubmitFieldUpdates());
        if (request.getOnApprovalFieldUpdates() != null) process.setOnApprovalFieldUpdates(request.getOnApprovalFieldUpdates());
        if (request.getOnRejectionFieldUpdates() != null) process.setOnRejectionFieldUpdates(request.getOnRejectionFieldUpdates());
        if (request.getOnRecallFieldUpdates() != null) process.setOnRecallFieldUpdates(request.getOnRecallFieldUpdates());
        if (request.getAllowRecall() != null) process.setAllowRecall(request.getAllowRecall());
        if (request.getExecutionOrder() != null) process.setExecutionOrder(request.getExecutionOrder());

        if (request.getCollectionId() != null) {
            Collection collection = collectionService.getCollection(request.getCollectionId());
            process.setCollection(collection);
        }

        if (request.getSteps() != null) {
            process.getSteps().clear();
            for (CreateApprovalProcessRequest.StepRequest stepReq : request.getSteps()) {
                ApprovalStep step = new ApprovalStep();
                step.setApprovalProcess(process);
                step.setStepNumber(stepReq.getStepNumber());
                step.setName(stepReq.getName());
                step.setDescription(stepReq.getDescription());
                step.setEntryCriteria(stepReq.getEntryCriteria());
                step.setApproverType(stepReq.getApproverType());
                step.setApproverId(stepReq.getApproverId());
                step.setApproverField(stepReq.getApproverField());
                step.setUnanimityRequired(stepReq.getUnanimityRequired() != null ? stepReq.getUnanimityRequired() : false);
                step.setEscalationTimeoutHours(stepReq.getEscalationTimeoutHours());
                step.setEscalationAction(stepReq.getEscalationAction());
                step.setOnApproveAction(stepReq.getOnApproveAction() != null ? stepReq.getOnApproveAction() : "NEXT_STEP");
                step.setOnRejectAction(stepReq.getOnRejectAction() != null ? stepReq.getOnRejectAction() : "REJECT_FINAL");
                process.getSteps().add(step);
            }
        }

        return processRepository.save(process);
    }

    @Transactional
    @SetupAudited(section = "Approvals", entityType = "ApprovalProcess")
    public void deleteProcess(String id) {
        log.info("Deleting approval process: {}", id);
        ApprovalProcess process = getProcess(id);
        processRepository.delete(process);
    }

    // --- Approval Instances ---

    @Transactional(readOnly = true)
    public List<ApprovalInstance> listInstances(String tenantId) {
        return instanceRepository.findByTenantIdOrderBySubmittedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public ApprovalInstance getInstance(String id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalInstance", id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalInstance> getPendingForUser(String userId) {
        return instanceRepository.findPendingForUser(userId);
    }

    @Transactional
    public ApprovalInstance submitForApproval(String tenantId, String collectionId,
                                              String recordId, String processId, String userId) {
        log.info("Submitting record {} for approval using process {}", recordId, processId);
        ApprovalProcess process = getProcess(processId);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setTenantId(tenantId);
        instance.setApprovalProcess(process);
        instance.setCollectionId(collectionId);
        instance.setRecordId(recordId);
        instance.setSubmittedBy(userId);
        instance.setSubmittedAt(Instant.now());
        instance.setStatus("PENDING");
        instance.setCurrentStepNumber(1);

        return instanceRepository.save(instance);
    }

    @Transactional
    public ApprovalStepInstance approveStep(String stepInstanceId, String userId, String comments) {
        log.info("Approving step instance: {}", stepInstanceId);
        ApprovalStepInstance stepInstance = stepInstanceRepository.findById(stepInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalStepInstance", stepInstanceId));

        stepInstance.setStatus("APPROVED");
        stepInstance.setComments(comments);
        stepInstance.setActedAt(Instant.now());

        return stepInstanceRepository.save(stepInstance);
    }

    @Transactional
    public ApprovalStepInstance rejectStep(String stepInstanceId, String userId, String comments) {
        log.info("Rejecting step instance: {}", stepInstanceId);
        ApprovalStepInstance stepInstance = stepInstanceRepository.findById(stepInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalStepInstance", stepInstanceId));

        stepInstance.setStatus("REJECTED");
        stepInstance.setComments(comments);
        stepInstance.setActedAt(Instant.now());

        ApprovalInstance instance = stepInstance.getApprovalInstance();
        instance.setStatus("REJECTED");
        instance.setCompletedAt(Instant.now());
        instanceRepository.save(instance);

        return stepInstanceRepository.save(stepInstance);
    }

    @Transactional
    public ApprovalInstance recallApproval(String instanceId, String userId) {
        log.info("Recalling approval instance: {}", instanceId);
        ApprovalInstance instance = getInstance(instanceId);
        instance.setStatus("RECALLED");
        instance.setCompletedAt(Instant.now());
        return instanceRepository.save(instance);
    }
}
