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
            private String serviceChanged = "config.service.changed";
            private String collectionChanged = "config.collection.changed";
            private String authzChanged = "config.authz.changed";
            private String uiChanged = "config.ui.changed";
            private String oidcChanged = "config.oidc.changed";

            public String getServiceChanged() {
                return serviceChanged;
            }

            public void setServiceChanged(String serviceChanged) {
                this.serviceChanged = serviceChanged;
            }

            public String getCollectionChanged() {
                return collectionChanged;
            }

            public void setCollectionChanged(String collectionChanged) {
                this.collectionChanged = collectionChanged;
            }

            public String getAuthzChanged() {
                return authzChanged;
            }

            public void setAuthzChanged(String authzChanged) {
                this.authzChanged = authzChanged;
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
        }
    }

    /**
     * Cache configuration.
     */
    public static class CacheConfig {
        private CacheEntry collections = new CacheEntry("collections", 3600);
        private CacheEntry jwks = new CacheEntry("jwks", 86400);

        public CacheEntry getCollections() {
            return collections;
        }

        public void setCollections(CacheEntry collections) {
            this.collections = collections;
        }

        public CacheEntry getJwks() {
            return jwks;
        }

        public void setJwks(CacheEntry jwks) {
            this.jwks = jwks;
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
        private String adminRole = "ADMIN";
        private String roleClaimName = "roles";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
