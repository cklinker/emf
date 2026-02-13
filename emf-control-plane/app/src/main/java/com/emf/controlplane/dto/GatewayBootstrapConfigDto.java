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
        private List<FieldDto> fields;

        public CollectionDto() {
        }

        public CollectionDto(String id, String name, String path, List<FieldDto> fields) {
            this.id = id;
            this.name = name;
            this.path = path;
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
    }
}
