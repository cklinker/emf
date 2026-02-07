package com.emf.controlplane.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for V8__add_tenant_table.sql migration.
 * Uses Testcontainers with real PostgreSQL to verify the migration
 * including PostgreSQL-specific features (regex CHECK, JSONB).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V8TenantMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("emf_test")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Nested
    @DisplayName("Table structure")
    class TableStructureTests {

        @Test
        @DisplayName("should create tenant table")
        void shouldCreateTenantTable() throws SQLException {
            try (Connection conn = getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet tables = meta.getTables(null, null, "tenant", new String[]{"TABLE"})) {
                    assertThat(tables.next()).isTrue();
                }
            }
        }

        @Test
        @DisplayName("should have all expected columns")
        void shouldHaveAllExpectedColumns() throws SQLException {
            try (Connection conn = getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet columns = meta.getColumns(null, null, "tenant", null)) {
                    List<String> columnNames = new ArrayList<>();
                    while (columns.next()) {
                        columnNames.add(columns.getString("COLUMN_NAME"));
                    }
                    assertThat(columnNames).containsExactlyInAnyOrder(
                            "id", "slug", "name", "edition", "status",
                            "settings", "limits", "created_at", "updated_at");
                }
            }
        }

        @Test
        @DisplayName("should have index on status column")
        void shouldHaveStatusIndex() throws SQLException {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT indexname FROM pg_indexes WHERE tablename = 'tenant' AND indexname = 'idx_tenant_status'")) {
                assertThat(rs.next()).isTrue();
            }
        }

        @Test
        @DisplayName("should have unique constraint on slug")
        void shouldHaveUniqueSlugConstraint() throws SQLException {
            try (Connection conn = getConnection()) {
                // Insert first tenant
                insertTenant(conn, UUID.randomUUID().toString(), "unique-slug", "Test Tenant");
                // Attempt duplicate slug
                assertThatThrownBy(() ->
                        insertTenant(conn, UUID.randomUUID().toString(), "unique-slug", "Duplicate"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("unique");
            }
        }
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValueTests {

        @Test
        @DisplayName("should default edition to PROFESSIONAL")
        void shouldDefaultEditionToProfessional() throws SQLException {
            try (Connection conn = getConnection()) {
                String id = UUID.randomUUID().toString();
                insertTenant(conn, id, "default-edition-" + id.substring(0, 8), "Test");
                try (PreparedStatement ps = conn.prepareStatement("SELECT edition FROM tenant WHERE id = ?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString("edition")).isEqualTo("PROFESSIONAL");
                    }
                }
            }
        }

        @Test
        @DisplayName("should default status to PROVISIONING")
        void shouldDefaultStatusToProvisioning() throws SQLException {
            try (Connection conn = getConnection()) {
                String id = UUID.randomUUID().toString();
                insertTenant(conn, id, "default-status-" + id.substring(0, 8), "Test");
                try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM tenant WHERE id = ?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString("status")).isEqualTo("PROVISIONING");
                    }
                }
            }
        }

        @Test
        @DisplayName("should default settings and limits to empty JSON object")
        void shouldDefaultJsonFieldsToEmptyObject() throws SQLException {
            try (Connection conn = getConnection()) {
                String id = UUID.randomUUID().toString();
                insertTenant(conn, id, "default-json-" + id.substring(0, 8), "Test");
                try (PreparedStatement ps = conn.prepareStatement("SELECT settings, limits FROM tenant WHERE id = ?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString("settings")).isEqualTo("{}");
                        assertThat(rs.getString("limits")).isEqualTo("{}");
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Edition check constraint")
    class EditionConstraintTests {

        @ParameterizedTest
        @ValueSource(strings = {"FREE", "PROFESSIONAL", "ENTERPRISE", "UNLIMITED"})
        @DisplayName("should accept valid edition")
        void shouldAcceptValidEdition(String edition) throws SQLException {
            try (Connection conn = getConnection()) {
                String id = UUID.randomUUID().toString();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO tenant (id, slug, name, edition) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, "ed-" + edition.toLowerCase() + "-" + id.substring(0, 8));
                    ps.setString(3, "Test");
                    ps.setString(4, edition);
                    ps.executeUpdate();
                }
            }
        }

        @Test
        @DisplayName("should reject invalid edition")
        void shouldRejectInvalidEdition() throws SQLException {
            try (Connection conn = getConnection()) {
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO tenant (id, slug, name, edition) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, "bad-edition-test");
                        ps.setString(3, "Test");
                        ps.setString(4, "INVALID");
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_tenant_edition");
            }
        }
    }

    @Nested
    @DisplayName("Status check constraint")
    class StatusConstraintTests {

        @ParameterizedTest
        @ValueSource(strings = {"PROVISIONING", "ACTIVE", "SUSPENDED", "DECOMMISSIONED"})
        @DisplayName("should accept valid status")
        void shouldAcceptValidStatus(String status) throws SQLException {
            try (Connection conn = getConnection()) {
                String id = UUID.randomUUID().toString();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO tenant (id, slug, name, status) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, "st-" + status.toLowerCase().substring(0, 4) + "-" + id.substring(0, 8));
                    ps.setString(3, "Test");
                    ps.setString(4, status);
                    ps.executeUpdate();
                }
            }
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() throws SQLException {
            try (Connection conn = getConnection()) {
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO tenant (id, slug, name, status) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, "bad-status-test");
                        ps.setString(3, "Test");
                        ps.setString(4, "DELETED");
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_tenant_status");
            }
        }
    }

    @Nested
    @DisplayName("Slug check constraint")
    class SlugConstraintTests {

        @ParameterizedTest
        @ValueSource(strings = {"acme", "my-company", "org-123", "a1b", "abc"})
        @DisplayName("should accept valid slugs")
        void shouldAcceptValidSlugs(String slug) throws SQLException {
            try (Connection conn = getConnection()) {
                String id = UUID.randomUUID().toString();
                // Append unique suffix to avoid collisions
                String uniqueSlug = slug + "-" + id.substring(0, 8);
                insertTenant(conn, id, uniqueSlug, "Test");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "A",                    // too short (must be 3+ chars)
                "ab",                   // too short
                "-invalid",             // starts with hyphen
                "1invalid",             // starts with number
                "UPPER",                // uppercase not allowed
                "has space",            // spaces not allowed
                "has_underscore",       // underscores not allowed
                "end-"                  // ends with hyphen
        })
        @DisplayName("should reject invalid slugs")
        void shouldRejectInvalidSlugs(String slug) throws SQLException {
            try (Connection conn = getConnection()) {
                assertThatThrownBy(() ->
                        insertTenant(conn, UUID.randomUUID().toString(), slug, "Test"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_tenant_slug");
            }
        }
    }

    @Nested
    @DisplayName("Existing tables")
    class ExistingTablesTests {

        @Test
        @DisplayName("should not affect existing collection table")
        void shouldNotAffectCollectionTable() throws SQLException {
            try (Connection conn = getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet tables = meta.getTables(null, null, "collection", new String[]{"TABLE"})) {
                    assertThat(tables.next()).isTrue();
                }
            }
        }

        @Test
        @DisplayName("should not affect existing field table")
        void shouldNotAffectFieldTable() throws SQLException {
            try (Connection conn = getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet tables = meta.getTables(null, null, "field", new String[]{"TABLE"})) {
                    assertThat(tables.next()).isTrue();
                }
            }
        }
    }

    /**
     * Helper: insert a tenant with only required fields (id, slug, name),
     * relying on defaults for edition, status, settings, limits.
     */
    private void insertTenant(Connection conn, String id, String slug, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenant (id, slug, name) VALUES (?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, slug);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }
}
