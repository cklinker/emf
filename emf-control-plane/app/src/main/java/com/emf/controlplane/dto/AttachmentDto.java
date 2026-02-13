package com.emf.controlplane.dto;

import com.emf.controlplane.entity.FileAttachment;

import java.time.Instant;

/**
 * Response DTO for file attachment metadata.
 * Matches the UI AttachmentsSection expected shape:
 * { id, fileName, fileSize, contentType, uploadedBy, uploadedAt }.
 *
 * @since 1.0.0
 */
public class AttachmentDto {

    private String id;
    private String fileName;
    private long fileSize;
    private String contentType;
    private String uploadedBy;
    private Instant uploadedAt;

    public AttachmentDto() {
    }

    /**
     * Creates a DTO from a FileAttachment entity.
     *
     * @param entity the FileAttachment entity
     * @return the DTO, or null if entity is null
     */
    public static AttachmentDto fromEntity(FileAttachment entity) {
        if (entity == null) {
            return null;
        }
        AttachmentDto dto = new AttachmentDto();
        dto.setId(entity.getId());
        dto.setFileName(entity.getFileName());
        dto.setFileSize(entity.getFileSize());
        dto.setContentType(entity.getContentType());
        dto.setUploadedBy(entity.getUploadedBy());
        dto.setUploadedAt(entity.getUploadedAt());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
