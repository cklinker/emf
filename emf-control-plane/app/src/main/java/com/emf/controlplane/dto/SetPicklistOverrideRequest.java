package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class SetPicklistOverrideRequest {

    @NotEmpty(message = "Available values are required")
    private List<String> availableValues;

    private String defaultValue;

    public SetPicklistOverrideRequest() {}

    public List<String> getAvailableValues() { return availableValues; }
    public void setAvailableValues(List<String> availableValues) { this.availableValues = availableValues; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
