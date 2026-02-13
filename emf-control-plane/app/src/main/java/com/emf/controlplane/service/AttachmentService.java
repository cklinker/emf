package com.emf.controlplane.service;

import com.emf.controlplane.entity.FileAttachment;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.FileAttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing file attachment metadata.
 *
 * <p>Currently supports metadata-only operations (list, delete).
 * S3 upload functionality is deferred to a future implementation.
 *
 * @since 1.0.0
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final FileAttachmentRepository attachmentRepository;

    public AttachmentService(FileAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
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
     * Delete an attachment's metadata.
     *
     * <p>Note: S3 file deletion is not yet implemented. When S3 storage is added,
     * this method should also delete the file from the storage bucket.
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

        // TODO: When S3 is implemented, delete from S3 first:
        // if (attachment.getStorageKey() != null) {
        //     s3Client.deleteObject(bucket, attachment.getStorageKey());
        // }

        attachmentRepository.delete(attachment);
    }
}
