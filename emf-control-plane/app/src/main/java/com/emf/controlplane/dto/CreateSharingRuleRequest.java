package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateSharingRuleRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "OWNER_BASED|CRITERIA_BASED")
    private String ruleType;

    private String sharedFrom;

    @NotBlank
    private String sharedTo;

    @NotBlank
    @Pattern(regexp = "ROLE|GROUP|QUEUE")
    private String sharedToType;

    @NotBlank
    @Pattern(regexp = "READ|READ_WRITE")
    private String accessLevel;

    private String criteria;

    public CreateSharingRuleRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

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
}
