package com.emf.controlplane.dto;

import jakarta.validation.constraints.Size;

public class UpdateValidationRuleRequest {

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private String errorConditionFormula;

    @Size(max = 1000, message = "Error message must be at most 1000 characters")
    private String errorMessage;

    @Size(max = 100, message = "Error field must be at most 100 characters")
    private String errorField;

    private String evaluateOn;

    private Boolean active;

    public UpdateValidationRuleRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getErrorConditionFormula() { return errorConditionFormula; }
    public void setErrorConditionFormula(String errorConditionFormula) { this.errorConditionFormula = errorConditionFormula; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorField() { return errorField; }
    public void setErrorField(String errorField) { this.errorField = errorField; }

    public String getEvaluateOn() { return evaluateOn; }
    public void setEvaluateOn(String evaluateOn) { this.evaluateOn = evaluateOn; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
