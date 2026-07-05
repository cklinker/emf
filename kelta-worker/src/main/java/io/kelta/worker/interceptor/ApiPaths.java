package io.kelta.worker.interceptor;

import java.util.List;

/**
 * Shared request-path classification for the field-security advice and the
 * masked-field predicate guard. Keeping the reserved-path set and the matching
 * rule in one place stops the two components from drifting — a mismatch would
 * mean one enforces masking on a path the other skips.
 */
final class ApiPaths {

    private ApiPaths() {}

    /**
     * Platform metadata / controller endpoints that serve configuration, not user
     * records, and are therefore exempt from field-level security + masking.
     */
    private static final List<String> METADATA_PREFIXES = List.of(
            "/api/collections",
            "/api/profiles",
            "/api/security-audit-logs",
            "/api/plugins",
            "/api/oidc",
            "/api/tenants",
            "/api/metrics",
            "/api/flows");

    /**
     * True when {@code path} is a reserved metadata endpoint. Matches by exact
     * path segment ({@code equals} or a {@code "/"} boundary) — <em>not</em> a raw
     * prefix — so a tenant user collection whose name merely starts with a reserved
     * token (e.g. {@code flowsheet} vs {@code /api/flows}, {@code metrics2} vs
     * {@code /api/metrics}) is still subject to masking/FLS instead of silently
     * skipping it.
     */
    static boolean isMetadataPath(String path) {
        for (String prefix : METADATA_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
