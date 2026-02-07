package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateGlobalPicklistRequest {

    @NotBlank(message = "Picklist name is required")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private boolean sorted = false;
    private boolean restricted = true;
    private List<PicklistValueRequest> values;

    public CreateGlobalPicklistRequest() {}

    public CreateGlobalPicklistRequest(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSorted() { return sorted; }
    public void setSorted(boolean sorted) { this.sorted = sorted; }

    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }

    public List<PicklistValueRequest> getValues() { return values; }
    public void setValues(List<PicklistValueRequest> values) { this.values = values; }
}
