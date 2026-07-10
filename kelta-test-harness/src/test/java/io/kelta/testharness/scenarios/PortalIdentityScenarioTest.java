package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portal identity through the real stack (telehealth slice 1, V167): admin
 * invites an external portal user via {@code POST /api/admin/users/portal-invite},
 * the row lands with {@code user_type=PORTAL} + the seeded Portal User profile
 * and — critically — NO {@code user_credential} row (portal users are
 * passwordless), a hashed PORTAL_INVITE token exists, and a re-invite is
 * idempotent on the user while issuing a fresh token. Covers what Mockito
 * worker tests can't: the V167 DDL, the seeded profile, and the check
 * constraint (see the DB-constraint-test-gap lesson).
 */
@DisplayName("Portal Identity Scenario")
class PortalIdentityScenarioTest extends ScenarioBase {

    @Test
    @DisplayName("invites a portal user: PORTAL type, seeded profile, no credential, hashed token; re-invite reuses the user")
    void invitesPortalUser() throws Exception {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);
        String email = "portal-scenario@example.com";

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/users", HttpStatus.OK, 20);

        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/admin/users/portal-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "firstName", "Portal", "lastName", "Patient"))
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = (String) created.getBody().get("userId");
        assertThat(created.getBody().get("status")).isEqualTo("INVITED");

        try (Connection conn = openDbConnection()) {
            // user_type=PORTAL + Portal User profile actually persisted (V167 column + seed).
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pu.user_type, pu.status, p.name AS profile_name "
                            + "FROM platform_user pu LEFT JOIN profile p ON p.id = pu.profile_id "
                            + "WHERE pu.id = ?")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("user_type")).isEqualTo("PORTAL");
                    assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                    assertThat(rs.getString("profile_name")).isEqualTo("Portal User");
                }
            }
            // Passwordless: no user_credential row means the form-login query
            // (INNER JOIN user_credential) can never authenticate this user.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM user_credential WHERE user_id = ?")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
            // A hashed PORTAL_INVITE token exists (64-hex, not raw).
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT token_hash, purpose, consumed_at FROM portal_login_token WHERE user_id = ?")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("token_hash")).matches("[0-9a-f]{64}");
                    assertThat(rs.getString("purpose")).isEqualTo("PORTAL_INVITE");
                    assertThat(rs.getTimestamp("consumed_at")).isNull();
                }
            }
        }

        // Re-invite: same user, fresh token, 200 REINVITED.
        ResponseEntity<Map> reinvited = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/admin/users/portal-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .retrieve().toEntity(Map.class);
        assertThat(reinvited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reinvited.getBody().get("userId")).isEqualTo(userId);
        assertThat(reinvited.getBody().get("status")).isEqualTo("REINVITED");

        try (Connection conn = openDbConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM portal_login_token WHERE user_id = ?")) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM platform_user WHERE email = ? AND tenant_id = ?")) {
                ps.setString(1, email);
                ps.setString(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
