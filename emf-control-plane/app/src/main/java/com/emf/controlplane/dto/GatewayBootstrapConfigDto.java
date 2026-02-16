package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;

/**
 * Bootstrap configuration for the API Gateway.
 * Contains collections needed for routing and per-tenant governor limits.
 */
public class GatewayBootstrapConfigDto {

    private List<CollectionDto> collections;
    private Map<String, GovernorLimitDto> governorLimits;

    public GatewayBootstrapConfigDto() {
    }

    public GatewayBootstrapConfigDto(List<CollectionDto> collections) {
        this.collections = collections;
    }

    public GatewayBootstrapConfigDto(List<CollectionDto> collections, Map<String, GovernorLimitDto> governorLimits) {
        this.collections = collections;
        this.governorLimits = governorLimits;
    }

    public List<CollectionDto> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionDto> collections) {
        this.collections = collections;
    }

    public Map<String, GovernorLimitDto> getGovernorLimits() {
        return governorLimits;
    }

    public void setGovernorLimits(Map<String, GovernorLimitDto> governorLimits) {
        this.governorLimits = governorLimits;
    }

    /**
     * Governor limit configuration for a tenant, used by the gateway for rate limiting.
     */
    public static class GovernorLimitDto {
        private int apiCallsPerDay;

        public GovernorLimitDto() {
        }

        public GovernorLimitDto(int apiCallsPerDay) {
            this.apiCallsPerDay = apiCallsPerDay;
        }

        public int getApiCallsPerDay() { return apiCallsPerDay; }
        public void setApiCallsPerDay(int apiCallsPerDay) { this.apiCallsPerDay = apiCallsPerDay; }
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

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getWorkerBaseUrl() { return workerBaseUrl; }
        public void setWorkerBaseUrl(String workerBaseUrl) { this.workerBaseUrl = workerBaseUrl; }

        public List<FieldDto> getFields() { return fields; }
        public void setFields(List<FieldDto> fields) { this.fields = fields; }
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

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
