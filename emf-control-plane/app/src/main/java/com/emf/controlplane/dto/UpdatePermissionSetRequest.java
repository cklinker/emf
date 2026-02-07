package com.emf.controlplane.dto;

import jakarta.validation.constraints.Size;

public class UpdatePermissionSetRequest {

    @Size(max = 100, message = "Permission set name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    public UpdatePermissionSetRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
