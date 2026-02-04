package com.emf.controlplane.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Response DTO for authorization configuration on a collection.
 * Contains the complete authorization setup including route and field policies.
 */
public class AuthorizationConfigDto {

    private String collectionId;
    private String collectionName;
    private List<RoutePolicyDto> routePolicies = new ArrayList<>();
    private List<FieldPolicyDto> fieldPolicies = new ArrayList<>();

    public AuthorizationConfigDto() {
    }

    public AuthorizationConfigDto(String collectionId, String collectionName,
                                   List<RoutePolicyDto> routePolicies, List<FieldPolicyDto> fieldPolicies) {
        this.collectionId = collectionId;
        this.collectionName = collectionName;
        this.routePolicies = routePolicies != null ? routePolicies : new ArrayList<>();
        this.fieldPolicies = fieldPolicies != null ? fieldPolicies : new ArrayList<>();
    }

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<RoutePolicyDto> getRoutePolicies() {
        return routePolicies;
    }

    public void setRoutePolicies(List<RoutePolicyDto> routePolicies) {
        this.routePolicies = routePolicies;
    }

    public List<FieldPolicyDto> getFieldPolicies() {
        return fieldPolicies;
    }

    public void setFieldPolicies(List<FieldPolicyDto> fieldPolicies) {
        this.fieldPolicies = fieldPolicies;
    }

    @Override
    public String toString() {
        return "AuthorizationConfigDto{" +
                "collectionId='" + collectionId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", routePolicies=" + routePolicies +
                ", fieldPolicies=" + fieldPolicies +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizationConfigDto that = (AuthorizationConfigDto) o;
        return Objects.equals(collectionId, that.collectionId) &&
                Objects.equals(collectionName, that.collectionName) &&
                Objects.equals(routePolicies, that.routePolicies) &&
                Objects.equals(fieldPolicies, that.fieldPolicies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, collectionName, routePolicies, fieldPolicies);
    }
}
