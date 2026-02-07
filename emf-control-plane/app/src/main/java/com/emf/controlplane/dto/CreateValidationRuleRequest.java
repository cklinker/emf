package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateValidationRuleRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotBlank(message = "Error condition formula is required")
    private String errorConditionFormula;

    @NotBlank(message = "Error message is required")
    @Size(max = 1000, message = "Error message must be at most 1000 characters")
    private String errorMessage;

    @Size(max = 100, message = "Error field must be at most 100 characters")
    private String errorField;

    private String evaluateOn;

    public CreateValidationRuleRequest() {}

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
}
