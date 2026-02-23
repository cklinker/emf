package com.emf.controlplane.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the EMF Control Plane Service.
 */
@Component
@ConfigurationProperties(prefix = "emf.control-plane")
public class ControlPlaneProperties {

    private KafkaTopics kafka = new KafkaTopics();
    private CacheConfig cache = new CacheConfig();
    private SecurityConfig security = new SecurityConfig();
    private String workerServiceUrl = "http://emf-worker:80";

    public KafkaTopics getKafka() {
        return kafka;
    }

    public void setKafka(KafkaTopics kafka) {
        this.kafka = kafka;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public SecurityConfig getSecurity() {
        return security;
    }

    public void setSecurity(SecurityConfig security) {
        this.security = security;
    }

    public String getWorkerServiceUrl() {
        return workerServiceUrl;
    }

    public void setWorkerServiceUrl(String workerServiceUrl) {
        this.workerServiceUrl = workerServiceUrl;
    }

    /**
     * Kafka topic configuration.
     */
    public static class KafkaTopics {
        private boolean enabled = false;
        private Topics topics = new Topics();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Topics getTopics() {
            return topics;
        }

        public void setTopics(Topics topics) {
            this.topics = topics;
        }

        public static class Topics {
            private String collectionChanged = "config.collection.changed";
            private String uiChanged = "config.ui.changed";
            private String oidcChanged = "config.oidc.changed";
            private String workflowRuleChanged = "config.workflow.changed";

            public String getCollectionChanged() {
                return collectionChanged;
            }

            public void setCollectionChanged(String collectionChanged) {
                this.collectionChanged = collectionChanged;
            }

            public String getUiChanged() {
                return uiChanged;
            }

            public void setUiChanged(String uiChanged) {
                this.uiChanged = uiChanged;
            }

            public String getOidcChanged() {
                return oidcChanged;
            }

            public void setOidcChanged(String oidcChanged) {
                this.oidcChanged = oidcChanged;
            }

            public String getWorkflowRuleChanged() {
                return workflowRuleChanged;
            }

            public void setWorkflowRuleChanged(String workflowRuleChanged) {
                this.workflowRuleChanged = workflowRuleChanged;
            }
        }
    }

    /**
     * Cache configuration.
     */
    public static class CacheConfig {
        private CacheEntry collections = new CacheEntry("collections", 3600);
        private CacheEntry collectionsList = new CacheEntry("collections-list", 300);
        private CacheEntry jwks = new CacheEntry("jwks", 86400);
        private CacheEntry governorLimits = new CacheEntry("governor-limits", 60);
        private CacheEntry layouts = new CacheEntry("layouts", 300);
        private CacheEntry bootstrap = new CacheEntry("bootstrap", 600);

        public CacheEntry getCollections() {
            return collections;
        }

        public void setCollections(CacheEntry collections) {
            this.collections = collections;
        }

        public CacheEntry getCollectionsList() {
            return collectionsList;
        }

        public void setCollectionsList(CacheEntry collectionsList) {
            this.collectionsList = collectionsList;
        }

        public CacheEntry getJwks() {
            return jwks;
        }

        public void setJwks(CacheEntry jwks) {
            this.jwks = jwks;
        }

        public CacheEntry getGovernorLimits() {
            return governorLimits;
        }

        public void setGovernorLimits(CacheEntry governorLimits) {
            this.governorLimits = governorLimits;
        }

        public CacheEntry getLayouts() {
            return layouts;
        }

        public void setLayouts(CacheEntry layouts) {
            this.layouts = layouts;
        }

        public CacheEntry getBootstrap() {
            return bootstrap;
        }

        public void setBootstrap(CacheEntry bootstrap) {
            this.bootstrap = bootstrap;
        }

        public static class CacheEntry {
            private String name;
            private int ttl;

            public CacheEntry() {
            }

            public CacheEntry(String name, int ttl) {
                this.name = name;
                this.ttl = ttl;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public int getTtl() {
                return ttl;
            }

            public void setTtl(int ttl) {
                this.ttl = ttl;
            }
        }
    }

    /**
     * Security configuration.
     */
    public static class SecurityConfig {
        private boolean enabled = true;
        private boolean permissionsEnabled = true;
        private String adminRole = "ADMIN";
        private String roleClaimName = "roles";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPermissionsEnabled() {
            return permissionsEnabled;
        }

        public void setPermissionsEnabled(boolean permissionsEnabled) {
            this.permissionsEnabled = permissionsEnabled;
        }

        public String getAdminRole() {
            return adminRole;
        }

        public void setAdminRole(String adminRole) {
            this.adminRole = adminRole;
        }

        public String getRoleClaimName() {
            return roleClaimName;
        }

        public void setRoleClaimName(String roleClaimName) {
            this.roleClaimName = roleClaimName;
        }
    }
}
