package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Listens for Spring Security authentication events to enforce account lockout
 * and password expiration policies.
 *
 * <p>On success: resets failed attempts + checks password expiration.
 * On failure (bad credentials): increments failed attempts + locks if threshold reached.
 *
 * @since 1.0.0
 */
@Component
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventListener.class);

    private final PasswordPolicyService policyService;
    private final JdbcTemplate jdbcTemplate;

    public AuthenticationEventListener(PasswordPolicyService policyService, JdbcTemplate jdbcTemplate) {
        this.policyService = policyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof KeltaUserDetails userDetails) {
            String userId = userDetails.getId();
            String tenantId = userDetails.getTenantId();

            policyService.resetFailedAttempts(userId);
            policyService.checkPasswordExpiration(userId, tenantId);

            log.debug("Authentication success for user {}, failed attempts reset", userId);
        }
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();

        // Look up userId and tenantId from email
        var results = jdbcTemplate.queryForList(
                "SELECT pu.id, pu.tenant_id FROM platform_user pu WHERE pu.email = ? AND pu.status = 'ACTIVE'",
                username
        );

        if (!results.isEmpty()) {
            String userId = (String) results.get(0).get("id");
            String tenantId = (String) results.get(0).get("tenant_id");

            policyService.incrementFailedAttempts(userId, tenantId);
            log.debug("Authentication failure for user {}, failed attempts incremented", userId);
        }
    }
}
