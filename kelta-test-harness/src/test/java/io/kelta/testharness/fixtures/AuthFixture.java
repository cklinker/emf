package io.kelta.testharness.fixtures;

import io.kelta.testharness.KeltaStack;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

/**
 * Mints JWTs against the in-harness kelta-auth instance via the direct-login endpoint.
 *
 * <p>The default Flyway seeds create {@code admin@kelta.local / password} for every
 * ACTIVE tenant. Use {@link #loginAsAdmin()} to authenticate and retrieve an access token.
 */
public final class AuthFixture {

    private final RestClient client;

    public AuthFixture() {
        this.client = RestClient.builder()
                .baseUrl(KeltaStack.authBaseUrl())
                .build();
    }

    /**
     * Logs in as the seeded admin user and returns the raw access token string.
     *
     * <p>The {@code admin@kelta.local} user exists for both the {@code default}
     * and {@code threadline-clothing} tenants (seeded by V102). The first DB match
     * is returned — the caller should not depend on which tenant is selected.
     *
     * @return a valid RS256 JWT access token
     */
    @SuppressWarnings("unchecked")
    public String loginAsAdmin() {
        Map<String, Object> response = client.post()
                .uri("/auth/direct-login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("username", "admin@kelta.local", "password", "password"))
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Direct login did not return access_token. Response: " + response);
        }
        return (String) response.get("access_token");
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
