package com.emf.controlplane.dto;

import com.emf.controlplane.entity.EmailLog;

import java.time.Instant;

public class EmailLogDto {

    private String id;
    private String templateId;
    private String recipientEmail;
    private String subject;
    private String status;
    private String source;
    private String sourceId;
    private String errorMessage;
    private Instant sentAt;
    private Instant createdAt;

    public static EmailLogDto fromEntity(EmailLog entity) {
        EmailLogDto dto = new EmailLogDto();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplate() != null ? entity.getTemplate().getId() : null);
        dto.setRecipientEmail(entity.getRecipientEmail());
        dto.setSubject(entity.getSubject());
        dto.setStatus(entity.getStatus());
        dto.setSource(entity.getSource());
        dto.setSourceId(entity.getSourceId());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setSentAt(entity.getSentAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
