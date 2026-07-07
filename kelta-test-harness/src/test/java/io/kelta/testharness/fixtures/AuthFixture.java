package io.kelta.testharness.fixtures;

import io.kelta.testharness.KeltaStack;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

/**
 * Mints JWTs against the in-harness kelta-auth instance via the direct-login endpoint.
 *
 * <p>The {@code default} tenant is seeded by the Flyway baseline with
 * {@code admin@kelta.local / password}. Every other tenant is provisioned at runtime
 * by the worker's {@code TenantProvisioningHook}, which seeds its admin as
 * {@code <slug>-admin@kelta.local} (username {@code <slug>-admin}, password
 * {@code password}). {@link #loginAsAdmin(String)} picks the right identity per slug.
 */
public final class AuthFixture {

    private final RestClient client;

    public AuthFixture() {
        this.client = RestClient.builder()
                .baseUrl(KeltaStack.authBaseUrl())
                .build();
    }

    /**
     * Logs in as the {@code admin@kelta.local} user in the {@code default} tenant.
     * kelta-auth refuses to authenticate without a tenant context because the
     * platform_user table is RLS-scoped, so a tenant slug is always required.
     */
    public String loginAsAdmin() {
        return loginAsAdmin(TenantFixture.DEFAULT_SLUG);
    }

    /**
     * Logs in as the admin user of the specified tenant and returns the raw access
     * token string. The admin identity depends on how the tenant was created:
     * <ul>
     *   <li>{@code default} — {@code admin@kelta.local} (Flyway baseline seed)</li>
     *   <li>any other slug — {@code <slug>-admin@kelta.local}, seeded by the worker's
     *       {@code TenantProvisioningHook} when the tenant was created via the admin API
     *       (e.g. the {@code threadline-clothing} fixture tenant)</li>
     * </ul>
     * Both share the password {@code password}.
     *
     * @param tenantSlug the slug of the tenant to scope the login to (e.g.
     *                   {@code "default"} or {@code "threadline-clothing"})
     * @return a valid RS256 JWT access token whose {@code tenant_id} claim
     *         matches the tenant identified by {@code tenantSlug}
     */
    public String loginAsAdmin(String tenantSlug) {
        // A tenant created at runtime (via TenantProvisioningHook) is loginnable only once
        // kelta-auth's user lookup resolves the new tenant — the slug→tenant view can lag the
        // worker's slug-map by a moment after creation. The default tenant (seeded before
        // startup) resolves on the first try; a runtime tenant may 401 briefly, so retry.
        AuthenticationException last = null;
        for (int attempt = 0; attempt < MAX_LOGIN_ATTEMPTS; attempt++) {
            try {
                return attemptLogin(tenantSlug);
            } catch (AuthenticationException e) {
                last = e;
                sleep(LOGIN_RETRY_MILLIS);
            }
        }
        throw new RuntimeException("Direct login for tenant '" + tenantSlug + "' still failing after "
                + MAX_LOGIN_ATTEMPTS + " attempts", last);
    }

    private static final int MAX_LOGIN_ATTEMPTS = 40;   // ~20s at 500ms
    private static final long LOGIN_RETRY_MILLIS = 500;

    /** Thrown when direct-login returns 401 for credentials that should be valid (propagation lag). */
    private static final class AuthenticationException extends RuntimeException {
        AuthenticationException(String message) { super(message); }
    }

    @SuppressWarnings("unchecked")
    private String attemptLogin(String tenantSlug) {
        String username = adminUsernameForSlug(tenantSlug);
        Map<String, Object> response;
        try {
            response = client.post()
                    .uri("/auth/direct-login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", username,
                            "password", "password",
                            "tenantSlug", tenantSlug))
                    .retrieve()
                    .body(Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new AuthenticationException("401 for " + username + " on tenant " + tenantSlug);
        }
        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Direct login did not return access_token. Response: " + response);
        }
        return (String) response.get("access_token");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying direct-login", ie);
        }
    }

    /**
     * The admin email for a tenant. The Flyway baseline seeds {@code admin@kelta.local}
     * for the {@code default} tenant; every runtime-provisioned tenant gets
     * {@code <slug>-admin@kelta.local} from {@code TenantProvisioningHook}. Direct-login
     * accepts email or username, so the email form is used uniformly.
     */
    private static String adminUsernameForSlug(String tenantSlug) {
        return TenantFixture.DEFAULT_SLUG.equals(tenantSlug)
                ? "admin@kelta.local"
                : tenantSlug + "-admin@kelta.local";
    }

    /**
     * Extracts the {@code tenant_id} claim from the access token without signature
     * verification (the harness trusts the token was issued by the in-process auth service).
     */
    public String extractTenantId(String accessToken) {
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Not a JWT: " + accessToken);
        byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
        String payload = new String(payloadBytes);
        // Naive extraction — sufficient for harness use; avoids Jackson dependency in fixtures
        return extractClaim(payload, "tenant_id");
    }

    private static String extractClaim(String json, String claim) {
        String key = "\"" + claim + "\":\"";
        int start = json.indexOf(key);
        if (start == -1) throw new IllegalArgumentException("Claim '" + claim + "' not found in: " + json);
        start += key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private static String padBase64(String base64url) {
        return base64url + "==".substring(base64url.length() % 4 == 0 ? 2 : base64url.length() % 4);
    }
}
