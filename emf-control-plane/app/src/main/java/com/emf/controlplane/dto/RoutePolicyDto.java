package com.emf.controlplane.dto;

import com.emf.controlplane.entity.RoutePolicy;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for RoutePolicy API responses.
 * Represents a policy applied to a collection route/operation.
 */
public class RoutePolicyDto {

    private String id;
    private String collectionId;
    private String operation;
    private String policyId;
    private String policyName;
    private Instant createdAt;

    public RoutePolicyDto() {
    }

    public RoutePolicyDto(String id, String collectionId, String operation, 
                          String policyId, String policyName, Instant createdAt) {
        this.id = id;
        this.collectionId = collectionId;
        this.operation = operation;
        this.policyId = policyId;
        this.policyName = policyName;
        this.createdAt = createdAt;
    }

    /**
     * Creates a RoutePolicyDto from a RoutePolicy entity.
     *
     * @param routePolicy The route policy entity to convert
     * @return A new RoutePolicyDto with data from the entity
     */
    public static RoutePolicyDto fromEntity(RoutePolicy routePolicy) {
        if (routePolicy == null) {
            return null;
        }
        return new RoutePolicyDto(
                routePolicy.getId(),
                routePolicy.getCollection() != null ? routePolicy.getCollection().getId() : null,
                routePolicy.getOperation(),
                routePolicy.getPolicy() != null ? routePolicy.getPolicy().getId() : null,
                routePolicy.getPolicy() != null ? routePolicy.getPolicy().getName() : null,
                routePolicy.getCreatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
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
        return "RoutePolicyDto{" +
                "id='" + id + '\'' +
                ", collectionId='" + collectionId + '\'' +
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
        RoutePolicyDto that = (RoutePolicyDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(collectionId, that.collectionId) &&
                Objects.equals(operation, that.operation) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyName, that.policyName) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, collectionId, operation, policyId, policyName, createdAt);
    }
}
