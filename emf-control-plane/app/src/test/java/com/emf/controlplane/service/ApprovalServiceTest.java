package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateApprovalProcessRequest;
import com.emf.controlplane.entity.*;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ApprovalInstanceRepository;
import com.emf.controlplane.repository.ApprovalProcessRepository;
import com.emf.controlplane.repository.ApprovalStepInstanceRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService")
class ApprovalServiceTest {

    @Mock private ApprovalProcessRepository processRepository;
    @Mock private ApprovalInstanceRepository instanceRepository;
    @Mock private ApprovalStepInstanceRepository stepInstanceRepository;
    @Mock private CollectionService collectionService;

    private ApprovalService service;

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_ID = "process-1";
    private static final String INSTANCE_ID = "instance-1";
    private static final String STEP_INSTANCE_ID = "step-1";
    private static final String COLLECTION_ID = "col-1";
    private static final String RECORD_ID = "rec-1";
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT_ID, "test-tenant");
        service = new ApprovalService(processRepository, instanceRepository,
                stepInstanceRepository, collectionService);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Process CRUD operations")
    class ProcessCrudTests {

        @Test
        @DisplayName("should list processes by tenant")
        void listProcessesByTenant() {
            ApprovalProcess process = new ApprovalProcess();
            process.setName("Test Process");
            when(processRepository.findByTenantIdOrderByNameAsc(TENANT_ID))
                    .thenReturn(List.of(process));

            List<ApprovalProcess> result = service.listProcesses(TENANT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Test Process");
        }

        @Test
        @DisplayName("should get process by id")
        void getProcessById() {
            ApprovalProcess process = new ApprovalProcess();
            process.setName("Test Process");
            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.of(process));

            ApprovalProcess result = service.getProcess(PROCESS_ID);

            assertThat(result.getName()).isEqualTo("Test Process");
        }

        @Test
        @DisplayName("should throw when process not found")
        void throwWhenProcessNotFound() {
            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProcess(PROCESS_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should create process with defaults")
        void createProcessWithDefaults() {
            Collection collection = new Collection();
            when(collectionService.getCollection(COLLECTION_ID)).thenReturn(collection);
            when(processRepository.save(any(ApprovalProcess.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateApprovalProcessRequest request = new CreateApprovalProcessRequest();
            request.setCollectionId(COLLECTION_ID);
            request.setName("New Process");

            ApprovalProcess result = service.createProcess(TENANT_ID, request);

            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getName()).isEqualTo("New Process");
            assertThat(result.isActive()).isTrue();
            assertThat(result.getRecordEditability()).isEqualTo("LOCKED");
            assertThat(result.isAllowRecall()).isTrue();
            assertThat(result.getExecutionOrder()).isEqualTo(0);
            assertThat(result.getOnSubmitFieldUpdates()).isEqualTo("[]");
            assertThat(result.getOnApprovalFieldUpdates()).isEqualTo("[]");
            assertThat(result.getOnRejectionFieldUpdates()).isEqualTo("[]");
            assertThat(result.getOnRecallFieldUpdates()).isEqualTo("[]");
            assertThat(result.getCollection()).isEqualTo(collection);
        }

        @Test
        @DisplayName("should create process with steps")
        void createProcessWithSteps() {
            Collection collection = new Collection();
            when(collectionService.getCollection(COLLECTION_ID)).thenReturn(collection);
            when(processRepository.save(any(ApprovalProcess.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateApprovalProcessRequest.StepRequest stepReq = new CreateApprovalProcessRequest.StepRequest();
            stepReq.setStepNumber(1);
            stepReq.setName("Manager Approval");
            stepReq.setApproverType("USER");
            stepReq.setApproverId("manager-1");

            CreateApprovalProcessRequest request = new CreateApprovalProcessRequest();
            request.setCollectionId(COLLECTION_ID);
            request.setName("Process With Steps");
            request.setSteps(List.of(stepReq));

            ApprovalProcess result = service.createProcess(TENANT_ID, request);

            assertThat(result.getSteps()).hasSize(1);
            ApprovalStep step = result.getSteps().get(0);
            assertThat(step.getStepNumber()).isEqualTo(1);
            assertThat(step.getName()).isEqualTo("Manager Approval");
            assertThat(step.getApproverType()).isEqualTo("USER");
            assertThat(step.isUnanimityRequired()).isFalse();
            assertThat(step.getOnApproveAction()).isEqualTo("NEXT_STEP");
            assertThat(step.getOnRejectAction()).isEqualTo("REJECT_FINAL");
            assertThat(step.getApprovalProcess()).isEqualTo(result);
        }

        @Test
        @DisplayName("should update process fields selectively")
        void updateProcessSelectively() {
            ApprovalProcess existing = new ApprovalProcess();
            existing.setName("Old Name");
            existing.setDescription("Old Desc");
            existing.setActive(true);
            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.of(existing));
            when(processRepository.save(any(ApprovalProcess.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateApprovalProcessRequest request = new CreateApprovalProcessRequest();
            request.setName("New Name");
            // description not set, should remain "Old Desc"

            ApprovalProcess result = service.updateProcess(PROCESS_ID, request);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("Old Desc");
        }

        @Test
        @DisplayName("should replace steps on update")
        void replaceStepsOnUpdate() {
            ApprovalProcess existing = new ApprovalProcess();
            existing.setName("Process");
            ApprovalStep oldStep = new ApprovalStep();
            oldStep.setStepNumber(1);
            oldStep.setName("Old Step");
            existing.getSteps().add(oldStep);

            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.of(existing));
            when(processRepository.save(any(ApprovalProcess.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateApprovalProcessRequest.StepRequest newStepReq = new CreateApprovalProcessRequest.StepRequest();
            newStepReq.setStepNumber(1);
            newStepReq.setName("New Step");
            newStepReq.setApproverType("ROLE");

            CreateApprovalProcessRequest request = new CreateApprovalProcessRequest();
            request.setSteps(List.of(newStepReq));

            ApprovalProcess result = service.updateProcess(PROCESS_ID, request);

            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).getName()).isEqualTo("New Step");
        }

        @Test
        @DisplayName("should delete process")
        void deleteProcess() {
            ApprovalProcess process = new ApprovalProcess();
            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.of(process));

            service.deleteProcess(PROCESS_ID);

            verify(processRepository).delete(process);
        }

        @Test
        @DisplayName("should throw when deleting non-existent process")
        void throwWhenDeletingNonExistent() {
            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteProcess(PROCESS_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Approval Instance operations")
    class InstanceTests {

        @Test
        @DisplayName("should list instances by tenant")
        void listInstancesByTenant() {
            ApprovalInstance instance = new ApprovalInstance();
            instance.setStatus("PENDING");
            when(instanceRepository.findByTenantIdOrderBySubmittedAtDesc(TENANT_ID))
                    .thenReturn(List.of(instance));

            List<ApprovalInstance> result = service.listInstances(TENANT_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should get instance by id")
        void getInstanceById() {
            ApprovalInstance instance = new ApprovalInstance();
            instance.setStatus("PENDING");
            when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));

            ApprovalInstance result = service.getInstance(INSTANCE_ID);

            assertThat(result.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should throw when instance not found")
        void throwWhenInstanceNotFound() {
            when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getInstance(INSTANCE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should get pending approvals for user")
        void getPendingForUser() {
            when(instanceRepository.findPendingForUser(USER_ID)).thenReturn(List.of());

            List<ApprovalInstance> result = service.getPendingForUser(USER_ID);

            assertThat(result).isEmpty();
            verify(instanceRepository).findPendingForUser(USER_ID);
        }

        @Test
        @DisplayName("should submit record for approval")
        void submitForApproval() {
            ApprovalProcess process = new ApprovalProcess();
            when(processRepository.findById(PROCESS_ID)).thenReturn(Optional.of(process));
            when(instanceRepository.save(any(ApprovalInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalInstance result = service.submitForApproval(
                    TENANT_ID, COLLECTION_ID, RECORD_ID, PROCESS_ID, USER_ID);

            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getCollectionId()).isEqualTo(COLLECTION_ID);
            assertThat(result.getRecordId()).isEqualTo(RECORD_ID);
            assertThat(result.getSubmittedBy()).isEqualTo(USER_ID);
            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(result.getCurrentStepNumber()).isEqualTo(1);
            assertThat(result.getSubmittedAt()).isNotNull();
            assertThat(result.getApprovalProcess()).isEqualTo(process);
        }
    }

    @Nested
    @DisplayName("Step approval/rejection operations")
    class StepActionTests {

        @Test
        @DisplayName("should approve step instance")
        void approveStep() {
            ApprovalStepInstance stepInstance = new ApprovalStepInstance();
            stepInstance.setStatus("PENDING");
            when(stepInstanceRepository.findById(STEP_INSTANCE_ID))
                    .thenReturn(Optional.of(stepInstance));
            when(stepInstanceRepository.save(any(ApprovalStepInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalStepInstance result = service.approveStep(
                    STEP_INSTANCE_ID, USER_ID, "Looks good");

            assertThat(result.getStatus()).isEqualTo("APPROVED");
            assertThat(result.getComments()).isEqualTo("Looks good");
            assertThat(result.getActedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw when approving non-existent step")
        void throwWhenApprovingNonExistentStep() {
            when(stepInstanceRepository.findById(STEP_INSTANCE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveStep(STEP_INSTANCE_ID, USER_ID, "ok"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should reject step and propagate to instance")
        void rejectStepAndPropagateToInstance() {
            ApprovalInstance instance = new ApprovalInstance();
            instance.setStatus("PENDING");

            ApprovalStepInstance stepInstance = new ApprovalStepInstance();
            stepInstance.setStatus("PENDING");
            stepInstance.setApprovalInstance(instance);

            when(stepInstanceRepository.findById(STEP_INSTANCE_ID))
                    .thenReturn(Optional.of(stepInstance));
            when(stepInstanceRepository.save(any(ApprovalStepInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(instanceRepository.save(any(ApprovalInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalStepInstance result = service.rejectStep(
                    STEP_INSTANCE_ID, USER_ID, "Not acceptable");

            assertThat(result.getStatus()).isEqualTo("REJECTED");
            assertThat(result.getComments()).isEqualTo("Not acceptable");
            assertThat(result.getActedAt()).isNotNull();

            // Verify parent instance was also rejected
            assertThat(instance.getStatus()).isEqualTo("REJECTED");
            assertThat(instance.getCompletedAt()).isNotNull();
            verify(instanceRepository).save(instance);
        }

        @Test
        @DisplayName("should recall approval instance")
        void recallApproval() {
            ApprovalInstance instance = new ApprovalInstance();
            instance.setStatus("PENDING");
            when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
            when(instanceRepository.save(any(ApprovalInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApprovalInstance result = service.recallApproval(INSTANCE_ID, USER_ID);

            assertThat(result.getStatus()).isEqualTo("RECALLED");
            assertThat(result.getCompletedAt()).isNotNull();
        }
    }
}
