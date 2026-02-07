package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateRecordShareRequest {

    @NotBlank
    private String recordId;

    @NotBlank
    private String sharedWithId;

    @NotBlank
    @Pattern(regexp = "USER|GROUP|ROLE")
    private String sharedWithType;

    @NotBlank
    @Pattern(regexp = "READ|READ_WRITE")
    private String accessLevel;

    @Pattern(regexp = "MANUAL|RULE|TEAM|TERRITORY")
    private String reason = "MANUAL";

    public CreateRecordShareRequest() {}

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getSharedWithId() { return sharedWithId; }
    public void setSharedWithId(String sharedWithId) { this.sharedWithId = sharedWithId; }

    public String getSharedWithType() { return sharedWithType; }
    public void setSharedWithType(String sharedWithType) { this.sharedWithType = sharedWithType; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
