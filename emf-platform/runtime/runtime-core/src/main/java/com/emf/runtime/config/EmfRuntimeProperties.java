package com.emf.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for EMF Runtime.
 * 
 * <p>Properties are prefixed with {@code emf} and include:
 * <ul>
 *   <li>emf.query.default-page-size - Default page size for queries</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "emf")
public class EmfRuntimeProperties {
    
    private Query query = new Query();

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
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
}
