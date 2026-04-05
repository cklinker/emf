package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimMeta {

    private String resourceType;
    private String created;
    private String lastModified;
    private String location;

    public ScimMeta() {}

    public ScimMeta(String resourceType, String created, String lastModified, String location) {
        this.resourceType = resourceType;
        this.created = created;
        this.lastModified = lastModified;
        this.location = location;
    }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
