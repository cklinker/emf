package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.List;

public class SetDependencyRequest {

    @NotBlank(message = "Controlling field ID is required")
    private String controllingFieldId;

    @NotBlank(message = "Dependent field ID is required")
    private String dependentFieldId;

    @NotNull(message = "Mapping is required")
    private Map<String, List<String>> mapping;

    public SetDependencyRequest() {}

    public String getControllingFieldId() { return controllingFieldId; }
    public void setControllingFieldId(String controllingFieldId) { this.controllingFieldId = controllingFieldId; }

    public String getDependentFieldId() { return dependentFieldId; }
    public void setDependentFieldId(String dependentFieldId) { this.dependentFieldId = dependentFieldId; }

    public Map<String, List<String>> getMapping() { return mapping; }
    public void setMapping(Map<String, List<String>> mapping) { this.mapping = mapping; }
}
