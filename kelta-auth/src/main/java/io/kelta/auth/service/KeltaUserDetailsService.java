package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeltaUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(KeltaUserDetailsService.class);

    private final JdbcTemplate jdbcTemplate;

    public KeltaUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
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
                  AND pu.status = 'ACTIVE'
                """,
                (rs, rowNum) -> new KeltaUserDetails(
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
                ),
                email, email
        );

        if (users.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + email);
        }

        if (users.size() > 1) {
            log.warn("Multiple users found for email {} across tenants, returning first match", email);
        }

        return users.get(0);
    }
}
