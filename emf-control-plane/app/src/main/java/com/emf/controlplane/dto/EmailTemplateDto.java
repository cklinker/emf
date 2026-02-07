package com.emf.controlplane.dto;

import com.emf.controlplane.entity.EmailTemplate;

import java.time.Instant;

public class EmailTemplateDto {

    private String id;
    private String name;
    private String description;
    private String subject;
    private String bodyHtml;
    private String bodyText;
    private String relatedCollectionId;
    private String folder;
    private boolean active;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static EmailTemplateDto fromEntity(EmailTemplate entity) {
        EmailTemplateDto dto = new EmailTemplateDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setSubject(entity.getSubject());
        dto.setBodyHtml(entity.getBodyHtml());
        dto.setBodyText(entity.getBodyText());
        dto.setRelatedCollectionId(
                entity.getRelatedCollection() != null ? entity.getRelatedCollection().getId() : null);
        dto.setFolder(entity.getFolder());
        dto.setActive(entity.isActive());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public String getRelatedCollectionId() { return relatedCollectionId; }
    public void setRelatedCollectionId(String relatedCollectionId) { this.relatedCollectionId = relatedCollectionId; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
