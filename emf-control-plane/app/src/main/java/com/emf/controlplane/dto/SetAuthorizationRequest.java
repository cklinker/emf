package com.emf.controlplane.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for setting authorization configuration on a collection.
 * Contains route policies (operation-level) and field policies (field-level).
 */
public class SetAuthorizationRequest {

    /**
     * Route policies define authorization for collection operations (CREATE, READ, UPDATE, DELETE).
     */
    @Valid
    private List<RoutePolicyRequest> routePolicies = new ArrayList<>();

    /**
     * Field policies define authorization for specific field operations (READ, WRITE).
     */
    @Valid
    private List<FieldPolicyRequest> fieldPolicies = new ArrayList<>();

    public SetAuthorizationRequest() {
    }

    public SetAuthorizationRequest(List<RoutePolicyRequest> routePolicies, List<FieldPolicyRequest> fieldPolicies) {
        this.routePolicies = routePolicies != null ? routePolicies : new ArrayList<>();
        this.fieldPolicies = fieldPolicies != null ? fieldPolicies : new ArrayList<>();
    }

    public List<RoutePolicyRequest> getRoutePolicies() {
        return routePolicies;
    }

    public void setRoutePolicies(List<RoutePolicyRequest> routePolicies) {
        this.routePolicies = routePolicies;
    }

    public List<FieldPolicyRequest> getFieldPolicies() {
        return fieldPolicies;
    }

    public void setFieldPolicies(List<FieldPolicyRequest> fieldPolicies) {
        this.fieldPolicies = fieldPolicies;
    }

    @Override
    public String toString() {
        return "SetAuthorizationRequest{" +
                "routePolicies=" + routePolicies +
                ", fieldPolicies=" + fieldPolicies +
                '}';
    }

    /**
     * Request for a route policy assignment.
     */
    public static class RoutePolicyRequest {

        @NotBlank(message = "Operation is required")
        private String operation;

        @NotBlank(message = "Policy ID is required")
        private String policyId;

        public RoutePolicyRequest() {
        }

        public RoutePolicyRequest(String operation, String policyId) {
            this.operation = operation;
            this.policyId = policyId;
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

        @Override
        public String toString() {
            return "RoutePolicyRequest{" +
                    "operation='" + operation + '\'' +
                    ", policyId='" + policyId + '\'' +
                    '}';
        }
    }

    /**
     * Request for a field policy assignment.
     */
    public static class FieldPolicyRequest {

        @NotBlank(message = "Field ID is required")
        private String fieldId;

        @NotBlank(message = "Operation is required")
        private String operation;

        @NotBlank(message = "Policy ID is required")
        private String policyId;

        public FieldPolicyRequest() {
        }

        public FieldPolicyRequest(String fieldId, String operation, String policyId) {
            this.fieldId = fieldId;
            this.operation = operation;
            this.policyId = policyId;
        }

        public String getFieldId() {
            return fieldId;
        }

        public void setFieldId(String fieldId) {
            this.fieldId = fieldId;
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

        @Override
        public String toString() {
            return "FieldPolicyRequest{" +
                    "fieldId='" + fieldId + '\'' +
                    ", operation='" + operation + '\'' +
                    ", policyId='" + policyId + '\'' +
                    '}';
        }
    }
}
