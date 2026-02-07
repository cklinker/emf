package com.emf.controlplane.dto;

import jakarta.validation.constraints.Size;

public class UpdateGlobalPicklistRequest {

    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean sorted;
    private Boolean restricted;

    public UpdateGlobalPicklistRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getSorted() { return sorted; }
    public void setSorted(Boolean sorted) { this.sorted = sorted; }

    public Boolean getRestricted() { return restricted; }
    public void setRestricted(Boolean restricted) { this.restricted = restricted; }
}
