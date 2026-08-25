package io.kelta.worker.controller;

import io.kelta.worker.cache.WorkerCacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Internal endpoint for gateway anonymous-access resolution.
 *
 * <p>A tenant that wants to grant anonymous (unauthenticated) callers any
 * access at all creates a {@code profiles} row named exactly {@code "Guest"}
 * and grants it permissions the same way as any other profile (via
 * {@code profile-system-permissions} / {@code profile-object-permissions}).
 * Nothing here grants anything by itself — this endpoint only tells the
 * gateway which profile id to attach to an unauthenticated request so Cerbos
 * has something to evaluate; Cerbos denies everything by default until the
 * tenant explicitly grants the Guest profile a permission.
 *
 * <p>A tenant with no {@code profiles} row named "Guest" is completely
 * unaffected: {@link JwtAuthenticationFilter} in the gateway still hard-401s
 * every unauthenticated request for it, exactly as before this existed.
 *
 * @since 1.0.0
 */
@RestController
public class GuestProfileController {

    private final JdbcTemplate jdbcTemplate;
    private final WorkerCacheManager cacheManager;

    public GuestProfileController(JdbcTemplate jdbcTemplate, WorkerCacheManager cacheManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/internal/tenants/{tenantId}/guest-profile")
    public ResponseEntity<String> resolveGuestProfile(@PathVariable String tenantId) {
        Optional<String> cached = cacheManager.getGuestProfile(tenantId);
        if (cached.isPresent()) {
            String value = cached.get();
            if (WorkerCacheManager.GUEST_PROFILE_NOT_FOUND.equals(value)) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(value);
        }

        List<String> results = jdbcTemplate.queryForList(
                "SELECT id FROM profile WHERE tenant_id = ? AND name = 'Guest'",
                String.class, tenantId);

        if (results.isEmpty()) {
            cacheManager.putGuestProfileNotFound(tenantId);
            return ResponseEntity.notFound().build();
        }

        String profileId = results.get(0);
        cacheManager.putGuestProfile(tenantId, profileId);
        return ResponseEntity.ok(profileId);
    }
}
