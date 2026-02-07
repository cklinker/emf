package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "email_template")
public class EmailTemplate extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_collection_id")
    private Collection relatedCollection;

    @Column(name = "folder", length = 100)
    private String folder;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    public EmailTemplate() { super(); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
    public Collection getRelatedCollection() { return relatedCollection; }
    public void setRelatedCollection(Collection relatedCollection) { this.relatedCollection = relatedCollection; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
