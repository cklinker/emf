package com.emf.controlplane.repository;

import com.emf.controlplane.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link FileAttachment} entity operations.
 *
 * @since 1.0.0
 */
@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, String> {

    /**
     * Find all attachments for a specific record, ordered by upload date descending.
     *
     * @param collectionId the collection UUID
     * @param recordId     the record UUID
     * @return attachments in reverse chronological order
     */
    List<FileAttachment> findByCollectionIdAndRecordIdOrderByUploadedAtDesc(
            String collectionId, String recordId);
}
