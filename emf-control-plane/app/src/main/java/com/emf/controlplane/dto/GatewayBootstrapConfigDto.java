package com.emf.controlplane.dto;

import java.util.List;

/**
 * Bootstrap configuration for the API Gateway.
 * Contains collections and authorization policies needed for routing.
 */
public class GatewayBootstrapConfigDto {

    private List<CollectionDto> collections;
    private AuthorizationDto authorization;

    public GatewayBootstrapConfigDto() {
    }

    public GatewayBootstrapConfigDto(List<CollectionDto> collections,
                                    AuthorizationDto authorization) {
        this.collections = collections;
        this.authorization = authorization;
    }

    public List<CollectionDto> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionDto> collections) {
        this.collections = collections;
    }

    public AuthorizationDto getAuthorization() {
        return authorization;
    }

    public void setAuthorization(AuthorizationDto authorization) {
        this.authorization = authorization;
    }

    /**
     * Collection configuration for the gateway.
     */
    public static class CollectionDto {
        private String id;
        private String name;
        private String path;
        private String workerBaseUrl;
        private List<FieldDto> fields;

        public CollectionDto() {
        }

        public CollectionDto(String id, String name, String path, List<FieldDto> fields) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.fields = fields;
        }

        public CollectionDto(String id, String name, String path, String workerBaseUrl, List<FieldDto> fields) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.workerBaseUrl = workerBaseUrl;
            this.fields = fields;
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

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getWorkerBaseUrl() {
            return workerBaseUrl;
        }

        public void setWorkerBaseUrl(String workerBaseUrl) {
            this.workerBaseUrl = workerBaseUrl;
        }

        public List<FieldDto> getFields() {
            return fields;
        }

        public void setFields(List<FieldDto> fields) {
            this.fields = fields;
        }
    }

    /**
     * Field configuration for collections.
     */
    public static class FieldDto {
        private String name;
        private String type;

        public FieldDto() {
        }

        public FieldDto(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * Authorization configuration for the gateway.
     */
    public static class AuthorizationDto {
        private List<Object> roles;
        private List<Object> policies;
        private List<Object> routePolicies;
        private List<Object> fieldPolicies;
        private List<CollectionAuthzDto> collectionAuthz;

        public AuthorizationDto() {
        }

        public AuthorizationDto(List<Object> roles, List<Object> policies,
                               List<Object> routePolicies, List<Object> fieldPolicies) {
            this.roles = roles;
            this.policies = policies;
            this.routePolicies = routePolicies;
            this.fieldPolicies = fieldPolicies;
        }

        public List<Object> getRoles() {
            return roles;
        }

        public void setRoles(List<Object> roles) {
            this.roles = roles;
        }

        public List<Object> getPolicies() {
            return policies;
        }

        public void setPolicies(List<Object> policies) {
            this.policies = policies;
        }

        public List<Object> getRoutePolicies() {
            return routePolicies;
        }

        public void setRoutePolicies(List<Object> routePolicies) {
            this.routePolicies = routePolicies;
        }

        public List<Object> getFieldPolicies() {
            return fieldPolicies;
        }

        public void setFieldPolicies(List<Object> fieldPolicies) {
            this.fieldPolicies = fieldPolicies;
        }

        public List<CollectionAuthzDto> getCollectionAuthz() {
            return collectionAuthz;
        }

        public void setCollectionAuthz(List<CollectionAuthzDto> collectionAuthz) {
            this.collectionAuthz = collectionAuthz;
        }
    }

    /**
     * Per-collection authorization data for populating the gateway's AuthzConfigCache on startup.
     */
    public static class CollectionAuthzDto {
        private String collectionId;
        private List<RoutePolicyEntry> routePolicies;

        public CollectionAuthzDto() {
        }

        public CollectionAuthzDto(String collectionId, List<RoutePolicyEntry> routePolicies) {
            this.collectionId = collectionId;
            this.routePolicies = routePolicies;
        }

        public String getCollectionId() {
            return collectionId;
        }

        public void setCollectionId(String collectionId) {
            this.collectionId = collectionId;
        }

        public List<RoutePolicyEntry> getRoutePolicies() {
            return routePolicies;
        }

        public void setRoutePolicies(List<RoutePolicyEntry> routePolicies) {
            this.routePolicies = routePolicies;
        }
    }

    /**
     * A single route policy entry binding an operation to a policy.
     */
    public static class RoutePolicyEntry {
        private String operation;
        private String policyId;
        private String policyName;
        private String policyRules;

        public RoutePolicyEntry() {
        }

        public RoutePolicyEntry(String operation, String policyId, String policyName, String policyRules) {
            this.operation = operation;
            this.policyId = policyId;
            this.policyName = policyName;
            this.policyRules = policyRules;
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

        public String getPolicyRules() {
            return policyRules;
        }

        public void setPolicyRules(String policyRules) {
            this.policyRules = policyRules;
        }
    }
}
