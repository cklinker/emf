package com.emf.controlplane.dto;

import com.emf.controlplane.entity.FieldPolicy;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for FieldPolicy API responses.
 * Represents a policy applied to a specific field within a collection.
 */
public class FieldPolicyDto {

    private String id;
    private String fieldId;
    private String fieldName;
    private String operation;
    private String policyId;
    private String policyName;
    private Instant createdAt;

    public FieldPolicyDto() {
    }

    public FieldPolicyDto(String id, String fieldId, String fieldName, String operation,
                          String policyId, String policyName, Instant createdAt) {
        this.id = id;
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.operation = operation;
        this.policyId = policyId;
        this.policyName = policyName;
        this.createdAt = createdAt;
    }

    /**
     * Creates a FieldPolicyDto from a FieldPolicy entity.
     *
     * @param fieldPolicy The field policy entity to convert
     * @return A new FieldPolicyDto with data from the entity
     */
    public static FieldPolicyDto fromEntity(FieldPolicy fieldPolicy) {
        if (fieldPolicy == null) {
            return null;
        }
        return new FieldPolicyDto(
                fieldPolicy.getId(),
                fieldPolicy.getField() != null ? fieldPolicy.getField().getId() : null,
                fieldPolicy.getField() != null ? fieldPolicy.getField().getName() : null,
                fieldPolicy.getOperation(),
                fieldPolicy.getPolicy() != null ? fieldPolicy.getPolicy().getId() : null,
                fieldPolicy.getPolicy() != null ? fieldPolicy.getPolicy().getName() : null,
                fieldPolicy.getCreatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFieldId() {
        return fieldId;
    }

    public void setFieldId(String fieldId) {
        this.fieldId = fieldId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "FieldPolicyDto{" +
                "id='" + id + '\'' +
                ", fieldId='" + fieldId + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", operation='" + operation + '\'' +
                ", policyId='" + policyId + '\'' +
                ", policyName='" + policyName + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldPolicyDto that = (FieldPolicyDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(fieldId, that.fieldId) &&
                Objects.equals(fieldName, that.fieldName) &&
                Objects.equals(operation, that.operation) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyName, that.policyName) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fieldId, fieldName, operation, policyId, policyName, createdAt);
    }
}
