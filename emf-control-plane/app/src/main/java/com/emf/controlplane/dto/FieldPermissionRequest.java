package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class FieldPermissionRequest {

    @NotBlank(message = "Field ID is required")
    private String fieldId;

    @NotBlank(message = "Visibility is required")
    @Pattern(regexp = "VISIBLE|READ_ONLY|HIDDEN", message = "Visibility must be VISIBLE, READ_ONLY, or HIDDEN")
    private String visibility;

    public FieldPermissionRequest() {}

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
