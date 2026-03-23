package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Manages per-tenant PostgreSQL users for Superset database connections.
 *
 * <p>Each tenant gets a dedicated PostgreSQL role ({@code superset_{slug}}) with:
 * <ul>
 *   <li>USAGE on only {@code public} and the tenant's schema</li>
 *   <li>SELECT on all tables in those schemas</li>
 *   <li>A hardcoded {@code app.current_tenant_id} session variable so RLS
 *       policies on public tables automatically filter to tenant data</li>
 *   <li>A {@code search_path} restricted to the tenant schema + public</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class SupersetDatabaseUserService {

    private static final Logger log = LoggerFactory.getLogger(SupersetDatabaseUserService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    public SupersetDatabaseUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a per-tenant PostgreSQL user for Superset with proper schema
     * isolation and RLS session variable.
     *
     * @param tenantId   the tenant UUID (used for RLS)
     * @param tenantSlug the tenant slug (used as schema name and username suffix)
     * @return the generated password for the new user
     */
    public String ensureTenantUser(String tenantId, String tenantSlug) {
        String username = toUsername(tenantSlug);
        String password = generatePassword();

        try {
            // Create role if it doesn't exist
            boolean exists = Boolean.TRUE.equals(
                    jdbcTemplate.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname = ?)",
                            Boolean.class, username));

            if (exists) {
                // Update password and settings in case they changed
                jdbcTemplate.execute(String.format(
                        "ALTER ROLE \"%s\" WITH PASSWORD '%s'", username, password));
                log.info("Updated existing Superset DB user '{}'", username);
            } else {
                jdbcTemplate.execute(String.format(
                        "CREATE ROLE \"%s\" WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE",
                        username, password));
                log.info("Created Superset DB user '{}'", username);
            }

            // Set default session variables on the role so RLS works automatically
            jdbcTemplate.execute(String.format(
                    "ALTER ROLE \"%s\" SET app.current_tenant_id = '%s'",
                    username, tenantId));

            // Set search_path so queries default to tenant schema + public
            jdbcTemplate.execute(String.format(
                    "ALTER ROLE \"%s\" SET search_path = \"%s\", public",
                    username, tenantSlug));

            // Grant CONNECT on the database
            jdbcTemplate.execute(String.format(
                    "GRANT CONNECT ON DATABASE emf_control_plane TO \"%s\"", username));

            // Grant USAGE on only public and tenant schema
            jdbcTemplate.execute(String.format(
                    "GRANT USAGE ON SCHEMA public TO \"%s\"", username));
            jdbcTemplate.execute(String.format(
                    "GRANT USAGE ON SCHEMA \"%s\" TO \"%s\"", tenantSlug, username));

            // Grant SELECT on all existing tables
            jdbcTemplate.execute(String.format(
                    "GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"%s\"", username));
            jdbcTemplate.execute(String.format(
                    "GRANT SELECT ON ALL TABLES IN SCHEMA \"%s\" TO \"%s\"",
                    tenantSlug, username));

            // Grant SELECT on future tables
            jdbcTemplate.execute(String.format(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO \"%s\"",
                    username));
            jdbcTemplate.execute(String.format(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA \"%s\" GRANT SELECT ON TABLES TO \"%s\"",
                    tenantSlug, username));

            log.info("Configured Superset DB user '{}' with tenant_id={}, schema={}",
                    username, tenantId, tenantSlug);
            return password;

        } catch (Exception e) {
            log.error("Failed to create Superset DB user '{}': {}", username, e.getMessage(), e);
            throw new RuntimeException("Failed to create Superset DB user: " + e.getMessage(), e);
        }
    }

    /**
     * Drops the per-tenant PostgreSQL user.
     *
     * @param tenantSlug the tenant slug
     */
    public void dropTenantUser(String tenantSlug) {
        String username = toUsername(tenantSlug);
        try {
            boolean exists = Boolean.TRUE.equals(
                    jdbcTemplate.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname = ?)",
                            Boolean.class, username));
            if (!exists) {
                log.info("Superset DB user '{}' does not exist — nothing to drop", username);
                return;
            }

            // Revoke all privileges before dropping
            jdbcTemplate.execute(String.format(
                    "REVOKE ALL ON ALL TABLES IN SCHEMA public FROM \"%s\"", username));
            jdbcTemplate.execute(String.format(
                    "REVOKE USAGE ON SCHEMA public FROM \"%s\"", username));
            jdbcTemplate.execute(String.format(
                    "REVOKE USAGE ON SCHEMA \"%s\" FROM \"%s\"", tenantSlug, username));
            jdbcTemplate.execute(String.format(
                    "DROP ROLE IF EXISTS \"%s\"", username));

            log.info("Dropped Superset DB user '{}'", username);
        } catch (Exception e) {
            log.error("Failed to drop Superset DB user '{}': {}", username, e.getMessage(), e);
        }
    }

    /**
     * Converts a tenant slug to a PostgreSQL username.
     * Format: superset_{slug} with hyphens replaced by underscores.
     */
    static String toUsername(String tenantSlug) {
        return "superset_" + tenantSlug.replaceAll("[^a-z0-9]", "_");
    }

    private String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
