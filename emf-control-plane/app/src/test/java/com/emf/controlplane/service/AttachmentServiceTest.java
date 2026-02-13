package com.emf.controlplane.service;

import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.FileAttachmentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttachmentService}.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private FileAttachmentRepository attachmentRepository;

    private AttachmentService attachmentService;

    private static final String COLLECTION_ID = "col-001";
    private static final String RECORD_ID = "rec-001";
    private static final String USERNAME = "test-user";

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(attachmentRepository);
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
    @DisplayName("deleteAttachment")
    class DeleteAttachment {

        @Test
        @DisplayName("should delete an existing attachment")
        void shouldDeleteAttachment() {
            // Given
            FileAttachment existing = createAttachment("att-1", "report.pdf");
            existing.setUploadedBy(USERNAME);
            when(attachmentRepository.findById("att-1")).thenReturn(Optional.of(existing));

            // When
            attachmentService.deleteAttachment("att-1", USERNAME);

            // Then
            verify(attachmentRepository).delete(existing);
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
        att.setFileSize(1024L);
        att.setContentType("application/pdf");
        att.setUploadedBy(USERNAME);
        att.setUploadedAt(Instant.now());
        return att;
    }
}
