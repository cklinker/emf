package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "validation_rule")
public class ValidationRule extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "error_condition_formula", nullable = false, columnDefinition = "TEXT")
    private String errorConditionFormula;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "error_field", length = 100)
    private String errorField;

    @Column(name = "evaluate_on", nullable = false, length = 20)
    private String evaluateOn = "CREATE_AND_UPDATE";

    public ValidationRule() {
        super();
    }

    public ValidationRule(String tenantId, Collection collection, String name,
                          String errorConditionFormula, String errorMessage) {
        super(tenantId);
        this.collection = collection;
        this.name = name;
        this.errorConditionFormula = errorConditionFormula;
        this.errorMessage = errorMessage;
    }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getErrorConditionFormula() { return errorConditionFormula; }
    public void setErrorConditionFormula(String errorConditionFormula) { this.errorConditionFormula = errorConditionFormula; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorField() { return errorField; }
    public void setErrorField(String errorField) { this.errorField = errorField; }

    public String getEvaluateOn() { return evaluateOn; }
    public void setEvaluateOn(String evaluateOn) { this.evaluateOn = evaluateOn; }

    @Override
    public String toString() {
        return "ValidationRule{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                '}';
    }
}
