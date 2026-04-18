package io.kelta.worker.config;

import io.kelta.runtime.context.TenantContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that tenant isolation actually holds at the database layer.
 *
 * <p>Spins up a real PostgreSQL container, sets up a tiny test schema that
 * mirrors the production RLS pattern (per-tenant rows + FORCE ROW LEVEL
 * SECURITY + a {@code platform_bypass} policy matching the reserved
 * {@code __platform__} sentinel), wraps the connection pool in the
 * fail-closed {@link TenantAwareDataSourceConfig.TenantAwareDataSource}, and
 * asserts the three invariants that the hardening work was intended to
 * guarantee:
 *
 * <ol>
 *   <li>A caller bound to tenant A sees only tenant A's rows — never B's.</li>
 *   <li>A caller bound to no tenant cannot even check out a connection.</li>
 *   <li>A caller opted into {@code runAsPlatform} sees every tenant's rows.</li>
 * </ol>
 *
 * <p>Lives in {@code kelta-worker} so it can reach the package-private
 * {@code TenantAwareDataSource} directly. Uses Testcontainers — requires a
 * running Docker daemon; CI already has this in place for the platform's other
 * integration tests.
 */
@Testcontainers
@EnabledIf("dockerAvailable")
class TenantIsolationTest {

    @SuppressWarnings("unused") // referenced by @EnabledIf
    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }


    private static final String TENANT_A = UUID.randomUUID().toString();
    private static final String TENANT_B = UUID.randomUUID().toString();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("isolation")
                    .withUsername("kelta")
                    .withPassword("kelta");

    static DataSource dataSource;

    @BeforeAll
    static void setUp() throws SQLException {
        org.postgresql.ds.PGSimpleDataSource pg = new org.postgresql.ds.PGSimpleDataSource();
        pg.setUrl(POSTGRES.getJdbcUrl());
        pg.setUser(POSTGRES.getUsername());
        pg.setPassword(POSTGRES.getPassword());
        dataSource = new TenantAwareDataSourceConfig.TenantAwareDataSource(pg);

        // Use the platform sentinel for the initial schema setup so the
        // fail-closed wrapper doesn't reject the setup connection.
        TenantContext.runAsPlatform(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE widget (
                            id UUID PRIMARY KEY,
                            tenant_id TEXT NOT NULL,
                            name TEXT NOT NULL
                        )
                        """);
                stmt.execute("ALTER TABLE widget ENABLE ROW LEVEL SECURITY");
                stmt.execute("ALTER TABLE widget FORCE ROW LEVEL SECURITY");
                stmt.execute("""
                        CREATE POLICY tenant_isolation ON widget
                            USING (tenant_id = current_setting('app.current_tenant_id', true))
                        """);
                stmt.execute("""
                        CREATE POLICY platform_bypass ON widget
                            USING (current_setting('app.current_tenant_id', true) = '__platform__')
                        """);

                insertWidget(conn, TENANT_A, "alpha-1");
                insertWidget(conn, TENANT_A, "alpha-2");
                insertWidget(conn, TENANT_B, "beta-1");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("tenant A sees only its own rows — never tenant B's")
    void tenantScopedReadsAreIsolated() {
        List<String> aNames = TenantContext.callWithTenant(TENANT_A,
                () -> selectWidgetNames());
        assertEquals(List.of("alpha-1", "alpha-2"), aNames.stream().sorted().toList());

        List<String> bNames = TenantContext.callWithTenant(TENANT_B,
                () -> selectWidgetNames());
        assertEquals(List.of("beta-1"), bNames);
    }

    @Test
    @DisplayName("tenant A cannot see tenant B's row by direct id lookup")
    void crossTenantIdLookupReturnsEmpty() {
        // Grab a B-owned id while platform-scoped, then try to read it as A.
        UUID bWidgetId = TenantContext.callAsPlatform(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT id FROM widget WHERE tenant_id = ? LIMIT 1")) {
                ps.setString(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "seeded tenant B row must exist under platform scope");
                    return UUID.fromString(rs.getString(1));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        boolean visibleToA = TenantContext.callWithTenant(TENANT_A, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT 1 FROM widget WHERE id = ?")) {
                ps.setObject(1, bWidgetId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(false, visibleToA,
                "tenant A must not be able to observe a tenant B row even by id");
    }

    @Test
    @DisplayName("unbound tenant context fails closed at connection checkout")
    void blankContextIsRejected() {
        IllegalStateException err = assertThrows(IllegalStateException.class,
                () -> {
                    try (Connection ignored = dataSource.getConnection()) {
                        // unreachable — checkout should throw
                    }
                });
        assertTrue(err.getMessage().contains("TenantContext"),
                () -> "expected a TenantContext-specific error, got: " + err.getMessage());
    }

    @Test
    @DisplayName("runAsPlatform sees every tenant's rows for legitimate cross-tenant reads")
    void platformSentinelReadsEveryTenant() {
        List<String> allNames = TenantContext.callAsPlatform(() -> selectWidgetNames());
        assertEquals(
                List.of("alpha-1", "alpha-2", "beta-1"),
                allNames.stream().sorted().toList());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static void insertWidget(Connection conn, String tenantId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO widget (id, tenant_id, name) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, tenantId);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private static List<String> selectWidgetNames() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM widget")) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
