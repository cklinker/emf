package io.kelta.runtime.storage;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Supplies a {@link JdbcTemplate} for a foreign database, given a collection's
 * connection settings. Implementations own datasource construction, pooling, and
 * caching by URL — kept behind this seam so {@link ExternalJdbcStorageAdapter} stays
 * unit-testable (a fake can hand back a template wired to an in-memory DB) and
 * runtime-core need not manage external connection pools. The production
 * (pooling) implementation is wired in a later slice.
 *
 * @since 1.0.0
 */
public interface ExternalJdbcConnectionProvider {

    /**
     * @param config the foreign-database connection settings
     * @return a {@link JdbcTemplate} bound to that database
     */
    JdbcTemplate jdbcTemplate(JdbcConfig config);

    /** Connection settings parsed from a collection's {@code adapterConfig}. */
    record JdbcConfig(String jdbcUrl, String username, String password) {}
}
