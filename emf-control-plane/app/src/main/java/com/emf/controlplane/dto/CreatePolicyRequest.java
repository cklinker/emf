package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new authorization policy.
 */
public class CreatePolicyRequest {

    @NotBlank(message = "Policy name is required")
    @Size(min = 1, max = 100, message = "Policy name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Policy expression for evaluation.
     * Example: "hasRole('ADMIN') && resource.owner == user.id"
     */
    private String expression;

    /**
     * JSON string containing the policy rules.
     * Example: {"roles": ["ADMIN", "EDITOR"], "conditions": {...}}
     */
    private String rules;

    public CreatePolicyRequest() {
    }

    public CreatePolicyRequest(String name, String description, String expression, String rules) {
        this.name = name;
        this.description = description;
        this.expression = expression;
        this.rules = rules;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    @Override
    public String toString() {
        return "CreatePolicyRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", expression='" + expression + '\'' +
                ", rules='" + rules + '\'' +
                '}';
    }
}
