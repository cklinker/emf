package com.emf.controlplane.service;

import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.FileAttachmentRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AttachmentService}.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private FileAttachmentRepository attachmentRepository;

    @Mock
    private S3StorageService storageService;

    private AttachmentService attachmentService;
    private AttachmentService attachmentServiceWithoutS3;

    private static final String COLLECTION_ID = "col-001";
    private static final String RECORD_ID = "rec-001";
    private static final String USERNAME = "test-user";
    private static final String TENANT_ID = "tenant-001";

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(attachmentRepository, storageService);
        attachmentServiceWithoutS3 = new AttachmentService(attachmentRepository, null);

        // Set up tenant context
        TenantContextHolder.set(TENANT_ID, "test-slug");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("listAttachments")
    class ListAttachments {

        @Test
        @DisplayName("should return attachments for a record")
        void shouldReturnAttachments() {
            // Given
            FileAttachment att1 = createAttachment("att-1", "report.pdf");
            FileAttachment att2 = createAttachment("att-2", "image.png");
            when(attachmentRepository.findByCollectionIdAndRecordIdOrderByUploadedAtDesc(
                    COLLECTION_ID, RECORD_ID))
                    .thenReturn(List.of(att2, att1));

            // When
            List<FileAttachment> result = attachmentService.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getFileName()).isEqualTo("image.png");
            assertThat(result.get(1).getFileName()).isEqualTo("report.pdf");
            verify(attachmentRepository).findByCollectionIdAndRecordIdOrderByUploadedAtDesc(
                    COLLECTION_ID, RECORD_ID);
        }

        @Test
        @DisplayName("should return empty list when no attachments exist")
        void shouldReturnEmptyList() {
            // Given
            when(attachmentRepository.findByCollectionIdAndRecordIdOrderByUploadedAtDesc(
                    COLLECTION_ID, RECORD_ID))
                    .thenReturn(Collections.emptyList());

            // When
            List<FileAttachment> result = attachmentService.listAttachments(COLLECTION_ID, RECORD_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createAttachment")
    class CreateAttachment {

        @Test
        @DisplayName("should create and save attachment metadata")
        void shouldCreateAttachment() {
            // Given
            when(attachmentRepository.save(any(FileAttachment.class))).thenAnswer(inv -> {
                FileAttachment a = inv.getArgument(0);
                a.setId("new-att-id");
                return a;
            });

            // When
            FileAttachment result = attachmentService.createAttachment(
                    COLLECTION_ID, RECORD_ID, "report.pdf", 2048L,
                    "application/pdf", "tenant-001/col-001/rec-001/uuid/report.pdf",
                    USERNAME);

            // Then
            assertThat(result.getId()).isEqualTo("new-att-id");
            assertThat(result.getCollectionId()).isEqualTo(COLLECTION_ID);
            assertThat(result.getRecordId()).isEqualTo(RECORD_ID);
            assertThat(result.getFileName()).isEqualTo("report.pdf");
            assertThat(result.getFileSize()).isEqualTo(2048L);
            assertThat(result.getContentType()).isEqualTo("application/pdf");
            assertThat(result.getStorageKey()).isEqualTo("tenant-001/col-001/rec-001/uuid/report.pdf");
            assertThat(result.getUploadedBy()).isEqualTo(USERNAME);
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);

            ArgumentCaptor<FileAttachment> captor = ArgumentCaptor.forClass(FileAttachment.class);
            verify(attachmentRepository).save(captor.capture());
            assertThat(captor.getValue().getFileName()).isEqualTo("report.pdf");
        }
    }

    @Nested
    @DisplayName("deleteAttachment")
    class DeleteAttachment {

        @Test
        @DisplayName("should delete attachment and S3 object when storage is available")
        void shouldDeleteAttachmentWithS3() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setStorageKey("tenant-001/col-001/rec-001/uuid/report.pdf");
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));

            // When
            attachmentService.deleteAttachment("att-1", USERNAME);

            // Then
            verify(storageService).delete("tenant-001/col-001/rec-001/uuid/report.pdf");
            verify(attachmentRepository).delete(existing);
        }

        @Test
        @DisplayName("should delete attachment metadata even when S3 deletion fails")
        void shouldDeleteMetadataEvenWhenS3Fails() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setStorageKey("tenant-001/col-001/rec-001/uuid/report.pdf");
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));
            doThrow(new RuntimeException("S3 error"))
                    .when(storageService).delete(anyString());

            // When
            attachmentService.deleteAttachment("att-1", USERNAME);

            // Then
            verify(attachmentRepository).delete(existing);
        }

        @Test
        @DisplayName("should delete attachment without S3 when storage is not available")
        void shouldDeleteWithoutS3() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setStorageKey("some-key");
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));

            // When
            attachmentServiceWithoutS3.deleteAttachment("att-1", USERNAME);

            // Then
            verify(attachmentRepository).delete(existing);
            verifyNoInteractions(storageService);
        }

        @Test
        @DisplayName("should delete attachment without S3 call when storage key is null")
        void shouldDeleteWithoutS3CallWhenNoStorageKey() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setStorageKey(null);
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));

            // When
            attachmentService.deleteAttachment("att-1", USERNAME);

            // Then
            verify(attachmentRepository).delete(existing);
            verify(storageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when attachment not found")
        void shouldThrowWhenAttachmentNotFound() {
            // Given
            when(attachmentRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> attachmentService.deleteAttachment("nonexistent", USERNAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("FileAttachment")
                    .hasMessageContaining("nonexistent");
        }
    }

    @Nested
    @DisplayName("getDownloadUrl")
    class GetDownloadUrl {

        @Test
        @DisplayName("should return presigned URL when S3 is available")
        void shouldReturnPresignedUrl() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setStorageKey("tenant-001/col-001/rec-001/uuid/report.pdf");
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));
            when(storageService.getPresignedDownloadUrl(eq("tenant-001/col-001/rec-001/uuid/report.pdf"),
                    any())).thenReturn("https://s3.example.com/presigned-url");

            // When
            String url = attachmentService.getDownloadUrl("att-1");

            // Then
            assertThat(url).isEqualTo("https://s3.example.com/presigned-url");
        }

        @Test
        @DisplayName("should return null when S3 is not available")
        void shouldReturnNullWhenNoS3() {
            // When
            String url = attachmentServiceWithoutS3.getDownloadUrl("att-1");

            // Then
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("should return null when storage key is null")
        void shouldReturnNullWhenNoStorageKey() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setStorageKey(null);
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));

            // When
            String url = attachmentService.getDownloadUrl("att-1");

            // Then
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when attachment not found")
        void shouldThrowWhenNotFound() {
            // Given
            when(attachmentRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> attachmentService.getDownloadUrl("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getDownloadUrlForKey")
    class GetDownloadUrlForKey {

        @Test
        @DisplayName("should return presigned URL for a given key")
        void shouldReturnUrl() {
            // Given
            when(storageService.getPresignedDownloadUrl(eq("some-key"), any()))
                    .thenReturn("https://s3.example.com/url");

            // When
            String url = attachmentService.getDownloadUrlForKey("some-key");

            // Then
            assertThat(url).isEqualTo("https://s3.example.com/url");
        }

        @Test
        @DisplayName("should return null when S3 is not available")
        void shouldReturnNullWhenNoS3() {
            // When
            String url = attachmentServiceWithoutS3.getDownloadUrlForKey("some-key");

            // Then
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("should return null when key is null")
        void shouldReturnNullWhenKeyIsNull() {
            // When
            String url = attachmentService.getDownloadUrlForKey(null);

            // Then
            assertThat(url).isNull();
        }
    }

    @Nested
    @DisplayName("isStorageAvailable")
    class IsStorageAvailable {

        @Test
        @DisplayName("should return true when S3 is available")
        void shouldReturnTrue() {
            assertThat(attachmentService.isStorageAvailable()).isTrue();
        }

        @Test
        @DisplayName("should return false when S3 is not available")
        void shouldReturnFalse() {
            assertThat(attachmentServiceWithoutS3.isStorageAvailable()).isFalse();
        }
    }

    /**
     * Helper to create a FileAttachment with common fields set.
     */
    private FileAttachment createAttachment(String id, String fileName) {
        FileAttachment att = new FileAttachment();
        att.setId(id);
        att.setTenantId(TENANT_ID);
        att.setCollectionId(COLLECTION_ID);
        att.setRecordId(RECORD_ID);
        att.setFileName(fileName);
        att.setFileSize(1024L);
        att.setContentType("application/pdf");
        att.setUploadedBy(USERNAME);
        att.setUploadedAt(Instant.now());
        return att;
    }
}
