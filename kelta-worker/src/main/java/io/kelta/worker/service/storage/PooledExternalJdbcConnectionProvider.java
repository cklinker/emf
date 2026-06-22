package io.kelta.worker.service.storage;

import com.zaxxer.hikari.HikariDataSource;
import io.kelta.runtime.storage.ExternalJdbcConnectionProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ExternalJdbcConnectionProvider} that maintains one pooled {@link JdbcTemplate}
 * (HikariCP) per distinct foreign-database connection, for {@code ExternalJdbcStorageAdapter}.
 *
 * <p>Templates are cached by {@code jdbcUrl|username} so repeated queries against the same
 * external database reuse a small pool rather than opening a connection per call. Pools are
 * created lazily (Hikari connects on first use).
 */
@Component
public class PooledExternalJdbcConnectionProvider implements ExternalJdbcConnectionProvider {

    private static final int MAX_POOL_SIZE = 5;

    private final ConcurrentHashMap<String, JdbcTemplate> templates = new ConcurrentHashMap<>();

    @Override
    public JdbcTemplate jdbcTemplate(JdbcConfig config) {
        String key = config.jdbcUrl() + "|" + (config.username() == null ? "" : config.username());
        return templates.computeIfAbsent(key, k -> new JdbcTemplate(pool(config, k)));
    }

    private static HikariDataSource pool(JdbcConfig config, String key) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.jdbcUrl());
        if (config.username() != null) {
            ds.setUsername(config.username());
        }
        if (config.password() != null) {
            ds.setPassword(config.password());
        }
        ds.setMaximumPoolSize(MAX_POOL_SIZE);
        ds.setPoolName("ext-jdbc-" + Integer.toHexString(key.hashCode()));
        return ds;
    }
}
