package com.emf.controlplane.dto;

public class CreateEmailTemplateRequest {

    private String name;
    private String description;
    private String subject;
    private String bodyHtml;
    private String bodyText;
    private String relatedCollectionId;
    private String folder;
    private Boolean active;

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
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
