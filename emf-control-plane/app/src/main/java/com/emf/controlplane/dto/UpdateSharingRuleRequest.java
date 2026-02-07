package com.emf.controlplane.dto;

import jakarta.validation.constraints.Pattern;

public class UpdateSharingRuleRequest {

    private String name;

    private String sharedFrom;

    private String sharedTo;

    @Pattern(regexp = "ROLE|GROUP|QUEUE")
    private String sharedToType;

    @Pattern(regexp = "READ|READ_WRITE")
    private String accessLevel;

    private String criteria;

    private Boolean active;

    public UpdateSharingRuleRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSharedFrom() { return sharedFrom; }
    public void setSharedFrom(String sharedFrom) { this.sharedFrom = sharedFrom; }

    public String getSharedTo() { return sharedTo; }
    public void setSharedTo(String sharedTo) { this.sharedTo = sharedTo; }

    public String getSharedToType() { return sharedToType; }
    public void setSharedToType(String sharedToType) { this.sharedToType = sharedToType; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
