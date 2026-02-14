package com.emf.gateway.config;

import java.util.List;

/**
 * Per-collection authorization configuration from the bootstrap response.
 * Used to warm the AuthzConfigCache on gateway startup.
 */
public class CollectionAuthzConfig {

    private String collectionId;
    private List<RoutePolicyEntry> routePolicies;

    public CollectionAuthzConfig() {
    }

    public CollectionAuthzConfig(String collectionId, List<RoutePolicyEntry> routePolicies) {
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
