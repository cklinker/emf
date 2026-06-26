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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Configures a tenant-aware DataSource wrapper that scopes the PostgreSQL variable
 * {@code app.current_tenant_id} to each database operation, transactionally.
 *
 * <p>This enables Row Level Security (RLS) policies on shared system tables to enforce
 * tenant isolation at the database level. RLS policies use
 * {@code current_setting('app.current_tenant_id', true)} to filter rows by tenant.
 *
 * <p>The tenant ID is read from {@link TenantContext} (ThreadLocal), set by the
 * tenant-context filter / DynamicCollectionRouter from the {@code X-Tenant-ID} header.
 *
 * <h2>Transaction-scoped tenant variable (PgBouncer-safe)</h2>
 * <p>For <b>tenant-scoped</b> connections (a non-empty tenant in context) the variable is
 * applied with {@code SET LOCAL} <i>inside an explicit transaction</i> rather than as a
 * connection-session {@code SET}:
 * <ol>
 *   <li>If the borrowed connection is in autocommit mode (the common JdbcTemplate path with
 *       no Spring-managed transaction), autocommit is turned off so a transaction begins,
 *       {@code SET LOCAL app.current_tenant_id = '...'} is issued, and the connection is
 *       returned through a thin proxy that commits and restores autocommit on
 *       {@link Connection#close()}. The connection is held no longer than before — it is
 *       released right after the operation, exactly like the previous autocommit model.</li>
 *   <li>If the connection is already inside a Spring-managed transaction (autocommit already
 *       off), only {@code SET LOCAL} is issued; Spring owns commit/rollback/close.</li>
 * </ol>
 *
 * <p>Because the variable is set <i>per transaction</i> on the same backend that serves the
 * query, it survives PgBouncer's {@code pool_mode = transaction} (which recycles the physical
 * backend per transaction). The previous session {@code SET} was lost under transaction
 * pooling, causing {@code current_setting} to return the empty default and the
 * {@code admin_bypass} policy to leak rows across tenants. That tenant-isolation hazard is
 * now closed by default.
 *
 * <p><b>Admin / bypass connections</b> (no tenant in context — Flyway migrations, internal
 * and cross-tenant operations) keep the simple session {@code SET app.current_tenant_id = ''}
 * with no transaction management, so those paths are unchanged. The empty value already maps
 * to {@code admin_bypass}, so transaction scoping is irrelevant to their isolation.
 *
 * <p>Uses a {@link BeanPostProcessor} to wrap the auto-configured DataSource, avoiding the
 * circular dependency that a {@code @Primary @Bean} DataSource injecting another DataSource
 * would create.
 *
 * <p>See {@code .claude/docs/concerns.md} → "Connection Pooler Compatibility".
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
                    log.info("Wrapping DataSource with tenant-aware RLS support "
                            + "(transaction-scoped SET LOCAL for tenant connections)");
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }

    /**
     * DataSource decorator that scopes {@code app.current_tenant_id} to each operation.
     */
    static class TenantAwareDataSource implements DataSource {

        private final DataSource delegate;

        TenantAwareDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return applyTenantScope(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return applyTenantScope(delegate.getConnection(username, password));
        }

        /**
         * Applies the tenant variable to a freshly borrowed connection and returns either the
         * connection itself or a commit-on-close proxy, depending on the path.
         */
        private Connection applyTenantScope(Connection conn) throws SQLException {
            String tenantId = TenantContext.get();

            // No tenant in context (Flyway, internal, cross-tenant admin work): keep the
            // legacy session SET '' behavior. Empty already maps to admin_bypass, so there is
            // nothing to isolate and no need to manage a transaction.
            if (tenantId == null || tenantId.isBlank()) {
                setTenantSession(conn, "");
                return conn;
            }

            boolean priorAutoCommit = conn.getAutoCommit();
            if (priorAutoCommit) {
                // Begin a transaction so SET LOCAL has a transaction to scope to.
                conn.setAutoCommit(false);
            }
            setTenantLocal(conn, tenantId);

            if (!priorAutoCommit) {
                // A Spring-managed (or otherwise owned) transaction is in charge of
                // commit/rollback/close. SET LOCAL applies to it; nothing more to do.
                return conn;
            }

            // We started the transaction for an autocommit-style operation: commit it and
            // restore autocommit when the caller releases the connection.
            return wrapWithCommitOnClose(conn);
        }

        /** Issues a transaction-local {@code SET LOCAL} for a tenant-scoped connection. */
        private static void setTenantLocal(Connection conn, String tenantId) throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + escape(tenantId) + "'");
            }
        }

        /** Issues a session-level {@code SET} for the admin/bypass (empty tenant) path. */
        private static void setTenantSession(Connection conn, String value) throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET app.current_tenant_id = '" + escape(value) + "'");
            }
        }

        // Tenant IDs are UUIDs validated upstream; escape defensively regardless.
        private static String escape(String value) {
            return value.replace("'", "''");
        }

        /**
         * Wraps the connection so that {@code close()} commits the transaction we began (unless
         * an owning transaction manager already ended it) and restores autocommit, then returns
         * the connection to the pool. All other calls pass straight through to the real
         * connection.
         *
         * <p>The same autocommit-off path is taken whether the borrow is a plain JdbcTemplate
         * operation or the start of a Spring-managed transaction — at borrow time Spring has not
         * yet flipped autocommit. To avoid committing twice, the proxy watches for the owner's
         * own {@code commit()}/{@code rollback()}: if either fired, {@code close()} only restores
         * autocommit. If neither did (the plain autocommit path), {@code close()} commits.
         */
        private static Connection wrapWithCommitOnClose(Connection real) {
            final boolean[] ownerEndedTx = {false};
            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                        throws Throwable {
                    String name = method.getName();
                    boolean noArgs = (args == null || args.length == 0);
                    if (noArgs && ("commit".equals(name) || "rollback".equals(name))) {
                        ownerEndedTx[0] = true;
                        return method.invoke(real, args);
                    }
                    if (noArgs && "close".equals(name)) {
                        if (ownerEndedTx[0]) {
                            restoreAutoCommit(real);
                        } else {
                            finishTransaction(real);
                        }
                        return method.invoke(real, args);
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException ite) {
                        throw ite.getTargetException();
                    }
                }
            };
            return (Connection) Proxy.newProxyInstance(
                    TenantAwareDataSource.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    handler);
        }

        /**
         * Commits the operation's transaction (a failed statement leaves the transaction in an
         * aborted state, which Postgres turns COMMIT into ROLLBACK for, so no partial data
         * persists) and restores autocommit before the connection returns to the pool.
         */
        private static void finishTransaction(Connection real) {
            try {
                if (!real.isClosed() && !real.getAutoCommit()) {
                    try {
                        real.commit();
                    } catch (SQLException commitEx) {
                        log.warn("Tenant transaction commit failed; rolling back: {}",
                                commitEx.getMessage());
                        safeRollback(real);
                    }
                }
            } catch (SQLException e) {
                log.warn("Failed to finalize tenant transaction before close: {}", e.getMessage());
            } finally {
                restoreAutoCommit(real);
            }
        }

        private static void restoreAutoCommit(Connection real) {
            try {
                if (!real.isClosed()) {
                    real.setAutoCommit(true);
                }
            } catch (SQLException e) {
                log.warn("Failed to restore autocommit before returning connection: {}",
                        e.getMessage());
            }
        }

        private static void safeRollback(Connection real) {
            try {
                real.rollback();
            } catch (SQLException rollbackEx) {
                log.warn("Tenant transaction rollback failed: {}", rollbackEx.getMessage());
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
