package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.ApprovalService;
import io.kelta.worker.service.ApprovalService.ApprovalActionResult;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ApprovalController")
class ApprovalControllerTest {

    private ApprovalService approvalService;
    private ApprovalController controller;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        controller = new ApprovalController(approvalService);
        TenantContext.set("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("submitForApproval")
    class Submit {

        @Test
        @DisplayName("Should submit successfully")
        void shouldSubmitSuccessfully() {
            when(approvalService.submitForApproval("col-1", "rec-1", "user-1", null))
                    .thenReturn(ApprovalActionResult.success("inst-1", "PENDING", "Submitted"));

            var body = new HashMap<String, Object>();
            body.put("collectionId", "col-1");
            body.put("recordId", "rec-1");

            var response = controller.submitForApproval(body, "user-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("instanceId", "inst-1");
        }

        @Test
        @DisplayName("Should return 400 for missing collectionId")
        void shouldReturn400ForMissingCollectionId() {
            var body = new HashMap<String, Object>();
            body.put("recordId", "rec-1");

            var response = controller.submitForApproval(body, "user-1");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 for missing userId")
        void shouldReturn400ForMissingUserId() {
            var body = new HashMap<String, Object>();
            body.put("collectionId", "col-1");
            body.put("recordId", "rec-1");

            var response = controller.submitForApproval(body, null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should fall back to body submittedBy when header missing")
        void shouldFallBackToBodySubmittedBy() {
            when(approvalService.submitForApproval("col-1", "rec-1", "body-user", null))
                    .thenReturn(ApprovalActionResult.success("inst-1", "PENDING", "Submitted"));

            var body = new HashMap<String, Object>();
            body.put("collectionId", "col-1");
            body.put("recordId", "rec-1");
            body.put("submittedBy", "body-user");

            var response = controller.submitForApproval(body, null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(approvalService).submitForApproval("col-1", "rec-1", "body-user", null);
        }

        @Test
        @DisplayName("Should return 400 without tenant context")
        void shouldReturn400WithoutTenant() {
            TenantContext.clear();

            var body = new HashMap<String, Object>();
            body.put("collectionId", "col-1");
            body.put("recordId", "rec-1");

            var response = controller.submitForApproval(body, "user-1");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("Should approve successfully")
        void shouldApproveSuccessfully() {
            when(approvalService.approve("inst-1", "user-1", "LGTM"))
                    .thenReturn(ApprovalActionResult.success("inst-1", "APPROVED", "Approved"));

            var body = Map.<String, Object>of("comments", "LGTM");
            var response = controller.approve("inst-1", body, "user-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "APPROVED");
        }

        @Test
        @DisplayName("Should return 400 when service returns error")
        void shouldReturn400OnServiceError() {
            when(approvalService.approve("inst-1", "user-1", null))
                    .thenReturn(ApprovalActionResult.error("No pending step"));

            var response = controller.approve("inst-1", null, "user-1");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("Should reject successfully")
        void shouldRejectSuccessfully() {
            when(approvalService.reject("inst-1", "user-1", "Needs revision"))
                    .thenReturn(ApprovalActionResult.success("inst-1", "REJECTED", "Rejected"));

            var body = Map.<String, Object>of("comments", "Needs revision");
            var response = controller.reject("inst-1", body, "user-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "REJECTED");
        }
    }

    @Nested
    @DisplayName("recall")
    class Recall {

        @Test
        @DisplayName("Should recall successfully")
        void shouldRecallSuccessfully() {
            when(approvalService.recall("inst-1", "user-1"))
                    .thenReturn(ApprovalActionResult.success("inst-1", "RECALLED", "Recalled"));

            var response = controller.recall("inst-1", "user-1", null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "RECALLED");
        }
    }

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("Should return active approval status")
        void shouldReturnActiveStatus() {
            when(approvalService.getCurrentApprovalStatus("col-1", "rec-1"))
                    .thenReturn(Optional.of(new HashMap<>(Map.of("id", "inst-1", "status", "PENDING"))));

            var response = controller.getStatus("col-1", "rec-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("hasActiveApproval", true);
        }

        @Test
        @DisplayName("Should return no active approval")
        void shouldReturnNoActiveApproval() {
            when(approvalService.getCurrentApprovalStatus("col-1", "rec-1"))
                    .thenReturn(Optional.empty());

            var response = controller.getStatus("col-1", "rec-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("hasActiveApproval", false);
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("Should return approval history")
        void shouldReturnHistory() {
            when(approvalService.getApprovalHistory("col-1", "rec-1"))
                    .thenReturn(List.of(Map.of("id", "inst-1", "status", "APPROVED")));

            var response = controller.getHistory("col-1", "rec-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getLockStatus")
    class GetLockStatus {

        @Test
        @DisplayName("Should return locked status")
        void shouldReturnLockedStatus() {
            when(approvalService.isRecordLocked("col-1", "rec-1")).thenReturn(true);

            var response = controller.getLockStatus("col-1", "rec-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("locked", true);
        }
    }
}
