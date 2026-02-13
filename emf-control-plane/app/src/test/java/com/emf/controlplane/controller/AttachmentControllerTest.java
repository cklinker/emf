package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AttachmentDto;
import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.service.AttachmentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttachmentController}.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    private AttachmentController attachmentController;

    private static final String COLLECTION_ID = "col-001";
    private static final String RECORD_ID = "rec-001";
    private static final String ATTACHMENT_ID = "att-001";
    private static final String USERNAME = "test-user";

    @BeforeEach
    void setUp() {
        attachmentController = new AttachmentController(attachmentService);

        // Set up SecurityContext with a test user
        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(
                new TestingAuthenticationToken(USERNAME, null));
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /{collectionId}/{recordId} - listAttachments")
    class ListAttachments {

        @Test
        @DisplayName("should return 200 with list of attachments")
        void shouldReturnAttachmentsWithOkStatus() {
            // Given
            FileAttachment att1 = createAttachment("att-1", "report.pdf");
            FileAttachment att2 = createAttachment("att-2", "image.png");
            when(attachmentService.listAttachments(COLLECTION_ID, RECORD_ID))
                    .thenReturn(List.of(att1, att2));

            // When
            ResponseEntity<List<AttachmentDto>> response =
                    attachmentController.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getId()).isEqualTo("att-1");
            assertThat(response.getBody().get(0).getFileName()).isEqualTo("report.pdf");
            assertThat(response.getBody().get(1).getId()).isEqualTo("att-2");
            verify(attachmentService).listAttachments(COLLECTION_ID, RECORD_ID);
        }

        @Test
        @DisplayName("should return 200 with empty list when no attachments exist")
        void shouldReturnEmptyList() {
            // Given
            when(attachmentService.listAttachments(COLLECTION_ID, RECORD_ID))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<AttachmentDto>> response =
                    attachmentController.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("DELETE /{attachmentId} - deleteAttachment")
    class DeleteAttachment {

        @Test
        @DisplayName("should return 204 No Content")
        void shouldReturnNoContent() {
            // When
            ResponseEntity<Void> response = attachmentController.deleteAttachment(ATTACHMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(attachmentService).deleteAttachment(ATTACHMENT_ID, USERNAME);
        }
    }

    @Nested
    @DisplayName("POST /{collectionId}/{recordId} - uploadAttachment")
    class UploadAttachment {

        @Test
        @DisplayName("should return 501 Not Implemented")
        void shouldReturnNotImplemented() {
            // When
            ResponseEntity<String> response =
                    attachmentController.uploadAttachment(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
            assertThat(response.getBody()).contains("not yet implemented");
        }
    }

    /**
     * Helper to create a FileAttachment with common fields set.
     */
    private FileAttachment createAttachment(String id, String fileName) {
        FileAttachment att = new FileAttachment();
        att.setId(id);
        att.setTenantId("tenant-001");
        att.setCollectionId(COLLECTION_ID);
        att.setRecordId(RECORD_ID);
        att.setFileName(fileName);
        att.setFileSize(2048L);
        att.setContentType("application/pdf");
        att.setUploadedBy(USERNAME);
        att.setUploadedAt(Instant.now());
        return att;
    }
}
