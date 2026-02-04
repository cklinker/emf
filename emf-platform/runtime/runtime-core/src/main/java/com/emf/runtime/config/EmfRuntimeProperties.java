package com.emf.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for EMF Runtime.
 * 
 * <p>Properties are prefixed with {@code emf} and include:
 * <ul>
 *   <li>emf.storage.mode - Storage mode (PHYSICAL_TABLES or JSONB_STORE)</li>
 *   <li>emf.query.default-page-size - Default page size for queries</li>
 *   <li>emf.events.enabled - Whether event publishing is enabled</li>
 *   <li>emf.events.topic-prefix - Kafka topic prefix</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "emf")
public class EmfRuntimeProperties {
    
    private Storage storage = new Storage();
    private Query query = new Query();
    private Events events = new Events();
    
    public Storage getStorage() {
        return storage;
    }
    
    public void setStorage(Storage storage) {
        this.storage = storage;
    }
    
    public Query getQuery() {
        return query;
    }
    
    public void setQuery(Query query) {
        this.query = query;
    }
    
    public Events getEvents() {
        return events;
    }
    
    public void setEvents(Events events) {
        this.events = events;
    }
    
    /**
     * Storage configuration properties.
     */
    public static class Storage {
        
        /**
         * Storage mode: PHYSICAL_TABLES (default) or JSONB_STORE.
         */
        private String mode = "PHYSICAL_TABLES";
        
        public String getMode() {
            return mode;
        }
        
        public void setMode(String mode) {
            this.mode = mode;
        }
    }
    
    /**
     * Query configuration properties.
     */
    public static class Query {
        
        /**
         * Default page size for queries.
         */
        private int defaultPageSize = 20;
        
        /**
         * Maximum allowed page size.
         */
        private int maxPageSize = 1000;
        
        public int getDefaultPageSize() {
            return defaultPageSize;
        }
        
        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }
        
        public int getMaxPageSize() {
            return maxPageSize;
        }
        
        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }
    }
    
    /**
     * Events configuration properties.
     */
    public static class Events {
        
        /**
         * Whether event publishing is enabled.
         */
        private boolean enabled = false;
        
        /**
         * Kafka topic prefix for events.
         */
        private String topicPrefix = "emf.events";
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public String getTopicPrefix() {
            return topicPrefix;
        }
        
        public void setTopicPrefix(String topicPrefix) {
            this.topicPrefix = topicPrefix;
        }
    }
}
