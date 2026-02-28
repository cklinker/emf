package com.emf.worker.config;

import com.emf.runtime.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Configures a tenant-aware DataSource wrapper that sets the PostgreSQL session variable
 * {@code app.current_tenant_id} on each connection obtained from the pool.
 *
 * <p>This enables Row Level Security (RLS) policies on shared system tables to enforce
 * tenant isolation at the database level. RLS policies use
 * {@code current_setting('app.current_tenant_id', true)} to filter rows by tenant.
 *
 * <p>The tenant ID is read from {@link TenantContext} (ThreadLocal), which is set by
 * the DynamicCollectionRouter from the {@code X-Tenant-ID} request header.
 *
 * <p>When no tenant context is set (e.g., during Flyway migrations or internal operations),
 * the variable is set to an empty string, which matches the {@code admin_bypass} RLS policy.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "emf.tenant-isolation.rls-enabled", havingValue = "true")
public class TenantAwareDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource tenantAwareDataSource(DataSource dataSource) {
        log.info("Wrapping DataSource with tenant-aware RLS support");
        return new TenantAwareDataSource(dataSource);
    }

    /**
     * DataSource decorator that sets {@code app.current_tenant_id} on each connection.
     */
    static class TenantAwareDataSource implements DataSource {

        private final DataSource delegate;

        TenantAwareDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection conn = delegate.getConnection();
            setTenantVariable(conn);
            return conn;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection conn = delegate.getConnection(username, password);
            setTenantVariable(conn);
            return conn;
        }

        private void setTenantVariable(Connection conn) throws SQLException {
            String tenantId = TenantContext.get();
            // Use empty string when no tenant context is set — this matches the admin_bypass policy
            String value = (tenantId != null && !tenantId.isBlank()) ? tenantId : "";
            try (Statement stmt = conn.createStatement()) {
                // Use SET SESSION so the variable persists for the duration of the connection use.
                // The variable is SQL-injection safe because tenant IDs are UUIDs validated upstream.
                stmt.execute("SET app.current_tenant_id = '" + value.replace("'", "''") + "'");
            }
        }

        // Delegate all other DataSource methods

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException("getParentLogger not supported");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
    }
}
