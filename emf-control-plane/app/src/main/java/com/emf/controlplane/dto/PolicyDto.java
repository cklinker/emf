package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Policy;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for Policy API responses.
 * Provides a clean API representation of a Policy entity.
 */
public class PolicyDto {

    private String id;
    private String name;
    private String description;
    private String expression;
    private String rules;
    private Instant createdAt;

    public PolicyDto() {
    }

    public PolicyDto(String id, String name, String description, String expression, String rules, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.expression = expression;
        this.rules = rules;
        this.createdAt = createdAt;
    }

    /**
     * Creates a PolicyDto from a Policy entity.
     *
     * @param policy The policy entity to convert
     * @return A new PolicyDto with data from the entity
     */
    public static PolicyDto fromEntity(Policy policy) {
        if (policy == null) {
            return null;
        }
        return new PolicyDto(
                policy.getId(),
                policy.getName(),
                policy.getDescription(),
                policy.getExpression(),
                policy.getRules(),
                policy.getCreatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "PolicyDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", expression='" + expression + '\'' +
                ", rules='" + rules + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyDto policyDto = (PolicyDto) o;
        return Objects.equals(id, policyDto.id) &&
                Objects.equals(name, policyDto.name) &&
                Objects.equals(description, policyDto.description) &&
                Objects.equals(expression, policyDto.expression) &&
                Objects.equals(rules, policyDto.rules) &&
                Objects.equals(createdAt, policyDto.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, expression, rules, createdAt);
    }
}
