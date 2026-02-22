package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AttachmentDto;
import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.service.AttachmentService;
import com.emf.controlplane.service.S3StorageService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AttachmentController}.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private S3StorageService storageService;

    private AttachmentController controllerWithS3;
    private AttachmentController controllerWithoutS3;

    private static final String COLLECTION_ID = "col-001";
    private static final String RECORD_ID = "rec-001";
    private static final String ATTACHMENT_ID = "att-001";
    private static final String USERNAME = "test-user";
    private static final String TENANT_ID = "tenant-001";

    @BeforeEach
    void setUp() {
        controllerWithS3 = new AttachmentController(attachmentService, storageService);
        controllerWithoutS3 = new AttachmentController(attachmentService, null);

        // Set up SecurityContext with a test user
        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(
                new TestingAuthenticationToken(USERNAME, null));
        SecurityContextHolder.setContext(securityContext);

        // Set up tenant context
        TenantContextHolder.set(TENANT_ID, "test-slug");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("GET /{collectionId}/{recordId} - listAttachments")
    class ListAttachments {

        @Test
        @DisplayName("should return 200 with list of attachments with download URLs")
        void shouldReturnAttachmentsWithDownloadUrls() {
            // Given
            FileAttachment att1 = createAttachment("att-1", "report.pdf", "key-1");
            FileAttachment att2 = createAttachment("att-2", "image.png", "key-2");
            when(attachmentService.listAttachments(COLLECTION_ID, RECORD_ID))
                    .thenReturn(List.of(att1, att2));
            when(attachmentService.getDownloadUrlForKey("key-1"))
                    .thenReturn("https://s3.example.com/key-1");
            when(attachmentService.getDownloadUrlForKey("key-2"))
                    .thenReturn("https://s3.example.com/key-2");

            // When
            ResponseEntity<List<AttachmentDto>> response =
                    controllerWithS3.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getId()).isEqualTo("att-1");
            assertThat(response.getBody().get(0).getFileName()).isEqualTo("report.pdf");
            assertThat(response.getBody().get(0).getDownloadUrl()).isEqualTo("https://s3.example.com/key-1");
            assertThat(response.getBody().get(1).getId()).isEqualTo("att-2");
            assertThat(response.getBody().get(1).getDownloadUrl()).isEqualTo("https://s3.example.com/key-2");
            verify(attachmentService).listAttachments(COLLECTION_ID, RECORD_ID);
        }

        @Test
        @DisplayName("should return 200 with attachments without download URLs when S3 not available")
        void shouldReturnAttachmentsWithoutDownloadUrls() {
            // Given
            FileAttachment att1 = createAttachment("att-1", "report.pdf", "key-1");
            when(attachmentService.listAttachments(COLLECTION_ID, RECORD_ID))
                    .thenReturn(List.of(att1));
            when(attachmentService.getDownloadUrlForKey("key-1"))
                    .thenReturn(null);

            // When
            ResponseEntity<List<AttachmentDto>> response =
                    controllerWithoutS3.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getDownloadUrl()).isNull();
        }

        @Test
        @DisplayName("should return 200 with empty list when no attachments exist")
        void shouldReturnEmptyList() {
            // Given
            when(attachmentService.listAttachments(COLLECTION_ID, RECORD_ID))
                    .thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<AttachmentDto>> response =
                    controllerWithS3.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /{collectionId}/{recordId} - uploadAttachment")
    class UploadAttachment {

        @Test
        @DisplayName("should return 201 with attachment DTO on successful upload")
        void shouldUploadSuccessfully() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf",
                    "file content".getBytes());

            when(storageService.getMaxFileSize()).thenReturn(52428800L);

            FileAttachment saved = createAttachment("new-att-id", "report.pdf", "tenant-001/col-001/rec-001/uuid/report.pdf");
            when(attachmentService.createAttachment(
                    eq(COLLECTION_ID), eq(RECORD_ID), eq("report.pdf"),
                    anyLong(), eq("application/pdf"), anyString(), eq(USERNAME)))
                    .thenReturn(saved);
            when(attachmentService.getDownloadUrlForKey(anyString()))
                    .thenReturn("https://s3.example.com/presigned");

            // When
            ResponseEntity<AttachmentDto> response =
                    controllerWithS3.uploadAttachment(COLLECTION_ID, RECORD_ID, file);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getFileName()).isEqualTo("report.pdf");
            assertThat(response.getBody().getDownloadUrl()).isEqualTo("https://s3.example.com/presigned");

            verify(storageService).upload(anyString(), any(), eq((long) "file content".getBytes().length),
                    eq("application/pdf"));
            verify(attachmentService).createAttachment(
                    eq(COLLECTION_ID), eq(RECORD_ID), eq("report.pdf"),
                    eq((long) "file content".getBytes().length),
                    eq("application/pdf"), anyString(), eq(USERNAME));
        }

        @Test
        @DisplayName("should return 503 when S3 is not available")
        void shouldReturn503WhenNoS3() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf",
                    "content".getBytes());

            // When
            ResponseEntity<AttachmentDto> response =
                    controllerWithoutS3.uploadAttachment(COLLECTION_ID, RECORD_ID, file);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("should return 400 when file is empty")
        void shouldReturn400WhenFileEmpty() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.txt", "text/plain", new byte[0]);

            // When
            ResponseEntity<AttachmentDto> response =
                    controllerWithS3.uploadAttachment(COLLECTION_ID, RECORD_ID, file);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 413 when file exceeds max size")
        void shouldReturn413WhenFileTooLarge() {
            // Given
            byte[] largeContent = new byte[1024];
            MockMultipartFile file = new MockMultipartFile(
                    "file", "large.bin", "application/octet-stream", largeContent);

            when(storageService.getMaxFileSize()).thenReturn(512L); // max is 512 bytes

            // When
            ResponseEntity<AttachmentDto> response =
                    controllerWithS3.uploadAttachment(COLLECTION_ID, RECORD_ID, file);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("GET /download/{attachmentId} - downloadAttachment")
    class DownloadAttachment {

        @Test
        @DisplayName("should return 302 redirect to presigned URL")
        void shouldRedirectToPresignedUrl() {
            // Given
            when(attachmentService.getDownloadUrl(ATTACHMENT_ID))
                    .thenReturn("https://s3.example.com/presigned-url");

            // When
            ResponseEntity<Void> response = controllerWithS3.downloadAttachment(ATTACHMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(response.getHeaders().getLocation()).isNotNull();
            assertThat(response.getHeaders().getLocation().toString())
                    .isEqualTo("https://s3.example.com/presigned-url");
        }

        @Test
        @DisplayName("should return 503 when download URL is null")
        void shouldReturn503WhenNoUrl() {
            // Given
            when(attachmentService.getDownloadUrl(ATTACHMENT_ID)).thenReturn(null);

            // When
            ResponseEntity<Void> response = controllerWithS3.downloadAttachment(ATTACHMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("DELETE /{attachmentId} - deleteAttachment")
    class DeleteAttachment {

        @Test
        @DisplayName("should return 204 No Content")
        void shouldReturnNoContent() {
            // When
            ResponseEntity<Void> response = controllerWithS3.deleteAttachment(ATTACHMENT_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(attachmentService).deleteAttachment(ATTACHMENT_ID, USERNAME);
        }
    }

    /**
     * Helper to create a FileAttachment with common fields set.
     */
    private FileAttachment createAttachment(String id, String fileName, String storageKey) {
        FileAttachment att = new FileAttachment();
        att.setId(id);
        att.setTenantId(TENANT_ID);
        att.setCollectionId(COLLECTION_ID);
        att.setRecordId(RECORD_ID);
        att.setFileName(fileName);
        att.setFileSize(2048L);
        att.setContentType("application/pdf");
        att.setStorageKey(storageKey);
        att.setUploadedBy(USERNAME);
        att.setUploadedAt(Instant.now());
        return att;
    }
}
