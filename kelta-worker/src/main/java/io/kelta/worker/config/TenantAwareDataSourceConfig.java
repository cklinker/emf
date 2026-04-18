package io.kelta.worker.config;

import io.kelta.runtime.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Configures a tenant-aware {@link DataSource} that binds the PostgreSQL session
 * variable {@code app.current_tenant_id} on every connection checkout, so Row
 * Level Security policies on shared system tables filter rows by tenant.
 *
 * <h3>Fail-closed semantics</h3>
 *
 * If {@link TenantContext#get()} is {@code null} or blank at checkout time this
 * wrapper throws {@link IllegalStateException}. That guarantees no connection
 * ever reaches application code while bound to a permissive fallback value —
 * callers that legitimately operate across tenants must opt in explicitly via
 * {@link TenantContext#runAsPlatform(Runnable)}, which binds the reserved
 * {@code __platform__} sentinel.
 *
 * <p>Combined with the {@code platform_bypass} RLS policy introduced in the
 * V126 migration (which matches only that sentinel), blank or forgotten tenant
 * contexts cannot silently leak data across tenants.
 *
 * <h3>Tenant values</h3>
 * <ul>
 *   <li><b>real UUID / slug</b> — bound by {@code TenantContextFilter} or
 *       explicit {@link TenantContext#runWithTenant}; RLS filters to that tenant.</li>
 *   <li><b>{@link TenantContext#PLATFORM_SENTINEL}</b> — bound by
 *       {@link TenantContext#runAsPlatform}; matches {@code platform_bypass}.</li>
 *   <li><b>blank / null</b> — rejected with {@link IllegalStateException}.</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
public class TenantAwareDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSourceConfig.class);

    @Bean
    public static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource ds) {
                    log.info("Wrapping DataSource with fail-closed tenant-aware RLS support");
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }

    /**
     * DataSource decorator that binds {@code app.current_tenant_id} on each connection
     * or fails closed when no tenant context is in scope.
     */
    static class TenantAwareDataSource implements DataSource {

        private final DataSource delegate;

        TenantAwareDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection conn = delegate.getConnection();
            try {
                setTenantVariable(conn);
            } catch (RuntimeException | SQLException e) {
                // Always return the connection to the pool — leaking it would
                // starve the pool under a storm of missing-context errors.
                try { conn.close(); } catch (SQLException ignored) {}
                throw e;
            }
            return conn;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection conn = delegate.getConnection(username, password);
            try {
                setTenantVariable(conn);
            } catch (RuntimeException | SQLException e) {
                try { conn.close(); } catch (SQLException ignored) {}
                throw e;
            }
            return conn;
        }

        private void setTenantVariable(Connection conn) throws SQLException {
            String tenantId = TenantContext.get();
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalStateException(
                        "DB connection checkout requires a bound TenantContext. "
                        + "Wrap the caller in TenantContext.runWithTenant(...) for tenant-scoped "
                        + "work or TenantContext.runAsPlatform(...) for explicit cross-tenant access.");
            }
            // Tenant IDs are UUIDs or the platform sentinel; we still escape
            // defensively in case a caller passes a value that carries single
            // quotes (PostgreSQL's SET VALUE syntax does not parameterize).
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET app.current_tenant_id = '" + tenantId.replace("'", "''") + "'");
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
