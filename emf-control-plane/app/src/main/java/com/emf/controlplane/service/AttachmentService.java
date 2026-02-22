package com.emf.controlplane.service;

import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.FileAttachmentRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service for managing file attachment metadata and S3 storage operations.
 *
 * <p>When S3 storage is enabled, this service coordinates between the
 * database metadata and the S3 object store. When S3 is disabled, it
 * operates in metadata-only mode.
 *
 * @since 1.0.0
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /** Default presigned URL expiry duration. */
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(15);

    private final FileAttachmentRepository attachmentRepository;
    private final S3StorageService storageService;

    public AttachmentService(FileAttachmentRepository attachmentRepository,
                             @Nullable S3StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
    }

    /**
     * List all attachments for a specific record in reverse chronological order.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return list of attachments
     */
    @Transactional(readOnly = true)
    public List<FileAttachment> listAttachments(String collectionId, String recordId) {
        return attachmentRepository.findByCollectionIdAndRecordIdOrderByUploadedAtDesc(
                collectionId, recordId);
    }

    /**
     * Create a new attachment metadata record.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @param fileName     the original file name
     * @param fileSize     the file size in bytes
     * @param contentType  the MIME content type
     * @param storageKey   the S3 object key
     * @param username     the authenticated username
     * @return the saved FileAttachment entity
     */
    @Transactional
    public FileAttachment createAttachment(String collectionId, String recordId,
                                           String fileName, long fileSize,
                                           String contentType, String storageKey,
                                           String username) {
        FileAttachment attachment = new FileAttachment();
        attachment.setTenantId(TenantContextHolder.requireTenantId());
        attachment.setCollectionId(collectionId);
        attachment.setRecordId(recordId);
        attachment.setFileName(fileName);
        attachment.setFileSize(fileSize);
        attachment.setContentType(contentType);
        attachment.setStorageKey(storageKey);
        attachment.setUploadedBy(username);
        attachment.setUploadedAt(Instant.now());

        FileAttachment saved = attachmentRepository.save(attachment);
        log.info("Created attachment {} (file: {}, key: {}) by user {}",
                saved.getId(), fileName, storageKey, username);
        return saved;
    }

    /**
     * Delete an attachment's metadata and its S3 object (if S3 is available).
     *
     * @param attachmentId the attachment UUID
     * @param username     the authenticated username (for logging)
     * @throws ResourceNotFoundException if the attachment is not found
     */
    @Transactional
    public void deleteAttachment(String attachmentId, String username) {
        FileAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("FileAttachment", attachmentId));

        log.info("Deleting attachment {} (file: {}) by user {}", attachmentId,
                attachment.getFileName(), username);

        // Delete from S3 if storage is available and a key exists
        if (storageService != null && attachment.getStorageKey() != null
                && !attachment.getStorageKey().isBlank()) {
            try {
                storageService.delete(attachment.getStorageKey());
            } catch (Exception e) {
                log.error("Failed to delete S3 object for attachment {}: {}",
                        attachmentId, e.getMessage(), e);
                // Continue with metadata deletion even if S3 deletion fails
            }
        }

        attachmentRepository.delete(attachment);
    }

    /**
     * Generate a presigned download URL for an attachment.
     *
     * @param attachmentId the attachment UUID
     * @return the presigned URL, or null if S3 is not available or no storage key exists
     * @throws ResourceNotFoundException if the attachment is not found
     */
    @Transactional(readOnly = true)
    @Nullable
    public String getDownloadUrl(String attachmentId) {
        if (storageService == null) {
            return null;
        }

        FileAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("FileAttachment", attachmentId));

        if (attachment.getStorageKey() == null || attachment.getStorageKey().isBlank()) {
            return null;
        }

        return storageService.getPresignedDownloadUrl(attachment.getStorageKey(), DOWNLOAD_URL_EXPIRY);
    }

    /**
     * Generate a presigned download URL for a given storage key.
     *
     * @param storageKey the S3 object key
     * @return the presigned URL, or null if S3 is not available
     */
    @Nullable
    public String getDownloadUrlForKey(String storageKey) {
        if (storageService == null || storageKey == null || storageKey.isBlank()) {
            return null;
        }
        return storageService.getPresignedDownloadUrl(storageKey, DOWNLOAD_URL_EXPIRY);
    }

    /**
     * Check whether S3 storage is available.
     *
     * @return true if S3 storage service is configured and enabled
     */
    public boolean isStorageAvailable() {
        return storageService != null;
    }
}
