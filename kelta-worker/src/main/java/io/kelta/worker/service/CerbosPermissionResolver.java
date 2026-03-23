package io.kelta.worker.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves Cerbos principal attributes from gateway-forwarded request headers.
 *
 * <p>The gateway sets these headers after JWT validation and identity resolution:
 * <ul>
 *   <li>{@code X-User-Email} — authenticated user's email</li>
 *   <li>{@code X-User-Profile-Id} — resolved profile UUID</li>
 *   <li>{@code X-User-Profile-Name} — resolved profile name</li>
 *   <li>{@code X-Cerbos-Scope} — tenant UUID (used as Cerbos policy scope)</li>
 * </ul>
 */
@Component
public class CerbosPermissionResolver {

    public String getEmail(HttpServletRequest request) {
        return request.getHeader("X-User-Email");
    }

    public String getProfileId(HttpServletRequest request) {
        return request.getHeader("X-User-Profile-Id");
    }

    public String getProfileName(HttpServletRequest request) {
        return request.getHeader("X-User-Profile-Name");
    }

    public String getTenantId(HttpServletRequest request) {
        return request.getHeader("X-Cerbos-Scope");
    }

    public boolean hasIdentity(HttpServletRequest request) {
        String email = getEmail(request);
        String profileId = getProfileId(request);
        String tenantId = getTenantId(request);
        return email != null && !email.isEmpty()
                && profileId != null && !profileId.isEmpty()
                && tenantId != null && !tenantId.isEmpty();
    }
}
