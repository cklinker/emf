package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Portal-billing webhook through the real stack (consumer-alerting slice 1).
 *
 * <p>What only a real database can prove, and what mocked worker tests cannot:
 * <ul>
 *   <li>A <b>redelivered</b> event grants exactly one pass. The idempotency rests
 *       on {@code billing_pass.stripe_checkout_session_id UNIQUE} plus
 *       {@code ON CONFLICT DO NOTHING} plus the {@code billing_webhook_event}
 *       claim — three DB-level mechanisms a Mockito test asserts nothing about.</li>
 *   <li>The endpoint is reachable <b>unauthenticated</b> through the gateway and
 *       still rejects a bad signature, which exercises the real
 *       {@code unauthenticated-paths} + static-route wiring.</li>
 *   <li>An unknown tenant is indistinguishable from a bad signature (both 401),
 *       so the endpoint is not a tenant-enumeration oracle.</li>
 * </ul>
 *
 * <p>The signing secret is stored through the real credential API so it is
 * encrypted by {@code CredentialEncryptionHook} and read back by
 * {@code CredentialResolver} exactly as in production.
 */
@DisplayName("Portal Billing Webhook Scenario")
class BillingWebhookScenarioTest extends ScenarioBase {

    private static final String WEBHOOK_SECRET = "whsec_harness_secret_value";
    private static final String PLAN_CODE_PREFIX = "harness-pass-";

    @Test
    @DisplayName("a redelivered webhook grants exactly one pass")
    @SuppressWarnings("unchecked")
    void redeliveredWebhookGrantsOnePass() throws Exception {
        String adminToken = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(adminToken);
        String slug = tenants.slugForTenantId(tenantId);
        var client = gatewayClientWithToken(adminToken);
        waitForStatus(client, "/" + slug + "/api/billing-plans", HttpStatus.OK, 60);

        String suffix = Long.toHexString(System.nanoTime());
        String planCode = PLAN_CODE_PREFIX + suffix;
        String sessionId = "cs_harness_" + suffix;
        String memberEmail = "member-" + suffix + "@example.com";

        String credentialId = null;
        String planId = null;

        try (Connection db = openDbConnection()) {
            String memberId = null;
            try {
                // ---- Processor credential (encrypted by the real hook)
                credentialId = createCredential(client, slug, suffix);

                // ---- A one-time plan to buy
                planId = createPlan(client, slug, planCode);

                // ---- The member the checkout belongs to
                String profileId = profileIdByName(db, tenantId, "Standard User");
                assertThat(profileId).as("Standard User profile").isNotNull();
                memberId = seedUser(db, tenantId, memberEmail, profileId);

                String body = checkoutCompletedBody(sessionId, planCode, memberId);
                String signature = sign(body, WEBHOOK_SECRET, Instant.now().getEpochSecond());

                // ---- First delivery
                ResponseEntity<Void> first = postWebhook(tenantId, body, signature);
                assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

                // ---- Redelivery of the identical event
                ResponseEntity<Void> second = postWebhook(tenantId, body, signature);
                assertThat(second.getStatusCode())
                        .as("a duplicate is accepted so the processor stops retrying")
                        .isEqualTo(HttpStatus.OK);

                // ---- Exactly one pass, and one claim row
                assertThat(countPasses(db, tenantId, sessionId))
                        .as("redelivery must not mint a second pass")
                        .isEqualTo(1);
                assertThat(countWebhookEvents(db, "evt_harness_" + suffix))
                        .as("the event id is claimed exactly once")
                        .isEqualTo(1);

                // ---- The pass is ACTIVE with an expiry derived from the plan
                Map<String, Object> pass = passRow(db, tenantId, sessionId);
                assertThat(pass.get("status")).isEqualTo("ACTIVE");
                assertThat(pass.get("user_id")).isEqualTo(memberId);
                assertThat(pass.get("expires_at")).as("30-day plan sets an expiry").isNotNull();

                // ---- The customer mapping was recorded
                assertThat(countCustomers(db, tenantId, memberId)).isEqualTo(1);
            } finally {
                cleanupBilling(db, tenantId, sessionId, "evt_harness_" + suffix, memberId);
                if (memberId != null) {
                    deleteUser(db, memberId);
                }
                if (planId != null) {
                    deleteRow(db, "billing_plan", planId);
                }
                if (credentialId != null) {
                    deleteRow(db, "credential", credentialId);
                }
            }
        }
    }

    @Test
    @DisplayName("a bad signature and an unknown tenant are both a bare 401")
    void badSignatureAndUnknownTenantBothReject() throws Exception {
        String adminToken = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(adminToken);
        String slug = tenants.slugForTenantId(tenantId);
        var client = gatewayClientWithToken(adminToken);
        waitForStatus(client, "/" + slug + "/api/billing-plans", HttpStatus.OK, 60);

        String suffix = Long.toHexString(System.nanoTime());
        String credentialId = null;
        try (Connection db = openDbConnection()) {
            try {
                credentialId = createCredential(client, slug, suffix);
                String body = checkoutCompletedBody("cs_bad_" + suffix, "nope", "u1");

                // Signed with the wrong secret.
                String wrong = sign(body, "whsec_not_the_secret", Instant.now().getEpochSecond());
                HttpClientErrorException badSig = catchThrowableOfType(
                        () -> postWebhook(tenantId, body, wrong), HttpClientErrorException.class);
                assertThat(badSig).isNotNull();
                assertThat(badSig.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

                // Correctly signed, but for a tenant that does not exist. Must be
                // the SAME response — otherwise this endpoint tells an anonymous
                // caller which tenants have billing configured.
                String right = sign(body, WEBHOOK_SECRET, Instant.now().getEpochSecond());
                HttpClientErrorException unknownTenant = catchThrowableOfType(
                        () -> postWebhook(UUID.randomUUID().toString(), body, right),
                        HttpClientErrorException.class);
                assertThat(unknownTenant).isNotNull();
                assertThat(unknownTenant.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(unknownTenant.getResponseBodyAsString())
                        .as("no detail that would distinguish the two cases")
                        .isEqualTo(badSig.getResponseBodyAsString());

                // A stale timestamp is rejected even with a valid signature.
                long old = Instant.now().minusSeconds(3600).getEpochSecond();
                HttpClientErrorException replayed = catchThrowableOfType(
                        () -> postWebhook(tenantId, body, sign(body, WEBHOOK_SECRET, old)),
                        HttpClientErrorException.class);
                assertThat(replayed).as("replay outside the tolerance window").isNotNull();
                assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            } finally {
                if (credentialId != null) {
                    deleteRow(db, "credential", credentialId);
                }
            }
        }
    }

    // ------------------------------------------------------------- HTTP

    /** Posts to the unauthenticated webhook path — no bearer token, no slug prefix. */
    private ResponseEntity<Void> postWebhook(String tenantId, String body, String signature) {
        return gatewayClient().post()
                .uri("/api/billing/webhooks/stripe/" + tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private String createCredential(org.springframework.web.client.RestClient client,
                                    String slug, String suffix) {
        ResponseEntity<Map> created = client.post()
                .uri("/" + slug + "/api/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("data", Map.of(
                        "type", "credentials",
                        "attributes", Map.of(
                                "name", "stripe",
                                "type", "stripe",
                                "secretKey", "sk_test_harness_" + suffix,
                                "webhookSecret", WEBHOOK_SECRET,
                                "allowedReturnOrigins", List.of("https://app.example.com"),
                                "active", true))))
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<String, Object>) created.getBody().get("data")).get("id");
    }

    @SuppressWarnings("unchecked")
    private String createPlan(org.springframework.web.client.RestClient client,
                              String slug, String planCode) {
        ResponseEntity<Map> created = client.post()
                .uri("/" + slug + "/api/billing-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("data", Map.of(
                        "type", "billing-plans",
                        "attributes", Map.of(
                                "code", planCode,
                                "name", "Harness Pass",
                                "kind", "ONE_TIME",
                                "passDurationDays", 30,
                                "entitlements", Map.of("maxActiveWatches", 10),
                                "active", true))))
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<String, Object>) created.getBody().get("data")).get("id");
    }

    // ------------------------------------------------------------- Signing

    /** Builds the processor's {@code t=…,v1=…} header over {@code "<t>.<body>"}. */
    private static String sign(String body, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String checkoutCompletedBody(String sessionId, String planCode, String userId) {
        // Deterministic string, not a serialized map: the signature is computed
        // over these exact bytes and any re-serialization would invalidate it.
        return """
                {"id":"evt_harness_%s","type":"checkout.session.completed",
                 "data":{"object":{
                   "id":"%s","mode":"payment","customer":"cus_harness_%s",
                   "payment_intent":"pi_harness_%s","client_reference_id":"%s",
                   "customer_details":{"email":"harness@example.com"},
                   "metadata":{"planCode":"%s"}}}}
                """.formatted(sessionId.replace("cs_harness_", "").replace("cs_bad_", ""),
                sessionId, sessionId, sessionId, userId, planCode);
    }

    // ------------------------------------------------------------- DB

    private int countPasses(Connection db, String tenantId, String sessionId) throws Exception {
        return count(db, "SELECT COUNT(*) FROM billing_pass "
                + "WHERE tenant_id = ? AND stripe_checkout_session_id = ?", tenantId, sessionId);
    }

    private int countCustomers(Connection db, String tenantId, String userId) throws Exception {
        return count(db, "SELECT COUNT(*) FROM billing_customer "
                + "WHERE tenant_id = ? AND user_id = ?", tenantId, userId);
    }

    private int countWebhookEvents(Connection db, String eventId) throws Exception {
        return count(db, "SELECT COUNT(*) FROM billing_webhook_event WHERE event_id = ?", eventId);
    }

    private int count(Connection db, String sql, String... params) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Map<String, Object> passRow(Connection db, String tenantId, String sessionId)
            throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status, user_id, expires_at FROM billing_pass "
                        + "WHERE tenant_id = ? AND stripe_checkout_session_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("pass row exists").isTrue();
                return Map.of(
                        "status", rs.getString("status"),
                        "user_id", rs.getString("user_id"),
                        "expires_at", String.valueOf(rs.getTimestamp("expires_at")));
            }
        }
    }

    private String profileIdByName(Connection db, String tenantId, String name) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT id FROM profile WHERE tenant_id = ? AND name = ? LIMIT 1")) {
            ps.setString(1, tenantId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String seedUser(Connection db, String tenantId, String email, String profileId)
            throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.prepareStatement("""
                INSERT INTO platform_user
                    (id, tenant_id, email, username, first_name, last_name, status, profile_id,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Harness', 'Member', 'ACTIVE', ?, NOW(), NOW())
                """)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, email);
            ps.setString(4, email);
            ps.setString(5, profileId);
            ps.executeUpdate();
        }
        return id;
    }

    private void cleanupBilling(Connection db, String tenantId, String sessionId,
                                String eventId, String memberId) throws Exception {
        exec(db, "DELETE FROM billing_pass WHERE tenant_id = ? AND stripe_checkout_session_id = ?",
                tenantId, sessionId);
        exec(db, "DELETE FROM billing_webhook_event WHERE event_id = ?", eventId);
        if (memberId != null) {
            exec(db, "DELETE FROM billing_customer WHERE tenant_id = ? AND user_id = ?",
                    tenantId, memberId);
        }
    }

    private void deleteUser(Connection db, String userId) throws Exception {
        exec(db, "DELETE FROM login_history WHERE user_id = ?", userId);
        exec(db, "DELETE FROM platform_user WHERE id = ?", userId);
    }

    private void deleteRow(Connection db, String table, String id) throws Exception {
        exec(db, "DELETE FROM " + table + " WHERE id = ?", id);
    }

    private void exec(Connection db, String sql, String... params) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
