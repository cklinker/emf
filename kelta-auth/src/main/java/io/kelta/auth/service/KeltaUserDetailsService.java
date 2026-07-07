package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class KeltaUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(KeltaUserDetailsService.class);

    private final JdbcTemplate jdbcTemplate;

    public KeltaUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String emailOrUsername) throws UsernameNotFoundException {
        String tenantId = resolveTenantFromRequest();

        // Tenant context is required. The platform_user table has row-level security on
        // the public schema scoped by tenant_id; cross-tenant lookups are unsafe because
        // the same email/username (e.g. admin@kelta.local) can exist in multiple tenants.
        if (tenantId == null) {
            log.warn("Refusing to authenticate '{}': no tenant context in session", emailOrUsername);
            throw new UsernameNotFoundException(
                    "Tenant context is required to authenticate. "
                            + "Initiate login via /oauth2/authorize with a tenant-scoped redirect_uri "
                            + "or pass ?tenant=<slug> to /login.");
        }

        List<KeltaUserDetails> users = jdbcTemplate.query(
                """
                SELECT pu.id, pu.email, pu.tenant_id, pu.profile_id,
                       p.name AS profile_name,
                       COALESCE(pu.first_name || ' ' || pu.last_name, pu.email) AS display_name,
                       uc.password_hash,
                       pu.status,
                       uc.locked_until,
                       uc.force_change_on_login
                FROM platform_user pu
                JOIN user_credential uc ON uc.user_id = pu.id
                LEFT JOIN profile p ON p.id = pu.profile_id
                WHERE (pu.email = ? OR pu.username = ?)
                  AND pu.tenant_id = ?
                  AND pu.status = 'ACTIVE'
                """,
                userDetailsRowMapper(),
                emailOrUsername, emailOrUsername, tenantId
        );

        if (users.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + emailOrUsername);
        }

        return users.get(0);
    }

    private org.springframework.jdbc.core.RowMapper<KeltaUserDetails> userDetailsRowMapper() {
        return (rs, rowNum) -> new KeltaUserDetails(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("tenant_id"),
                rs.getString("profile_id"),
                rs.getString("profile_name"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                "ACTIVE".equals(rs.getString("status")),
                rs.getTimestamp("locked_until") != null
                        && rs.getTimestamp("locked_until").toInstant().isAfter(java.time.Instant.now()),
                rs.getBoolean("force_change_on_login")
        );
    }

    /**
     * Resolves the tenant UUID from the current request's session. The session attribute
     * "tenantId" may be a UUID (set during OAuth2 authorize redirect) or a slug (set from
     * the ?tenant= query parameter). Slugs are resolved to UUIDs via the tenant table.
     */
    private String resolveTenantFromRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpSession session = attrs.getRequest().getSession(false);
            if (session == null) return null;
            Object raw = session.getAttribute("tenantId");
            if (!(raw instanceof String str) || str.isBlank()) return null;

            // Already a UUID
            if (str.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return str;
            }

            // Resolve slug → UUID
            List<String> ids = jdbcTemplate.queryForList("SELECT id FROM tenant WHERE slug = ?", String.class, str);
            if (ids.isEmpty()) {
                log.warn("Tenant slug '{}' not found in tenant table", str);
                return null;
            }
            return ids.get(0);
        } catch (IllegalStateException e) {
            // No request context (e.g. called outside a request — shouldn't happen for login)
            return null;
        }
    }
}
