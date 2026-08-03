package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Stripe API credentials for portal billing.
 *
 * <p>Two secrets, with different jobs:
 * <ul>
 *   <li>{@code secretKey} authenticates <em>outbound</em> calls (create customer,
 *       create checkout session, create billing-portal session).</li>
 *   <li>{@code webhookSecret} verifies <em>inbound</em> webhooks. It is the sole
 *       trust anchor for that endpoint, which the gateway serves
 *       unauthenticated — a request that fails the HMAC check is rejected before
 *       its body is parsed as an event.</li>
 * </ul>
 *
 * <p>{@code allowedReturnOrigins} bounds where a checkout or portal session may
 * send the member afterwards; without it a caller could redirect a paying member
 * to an attacker-controlled page.
 *
 * <p>Deliberately no {@code stripe-java}: its large Gson-reflective model surface
 * is a native-image hazard, and the platform needs only a handful of calls, so
 * those go through a plain REST client in the worker.
 */
@Component
public class StripeCredentialType extends AbstractCredentialType {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final String ACCOUNT_URL = "https://api.stripe.com/v1/account";

    public StripeCredentialType(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getKey() {
        return "stripe";
    }

    @Override
    public String getDisplayName() {
        return "Stripe";
    }

    @Override
    public String getDescription() {
        return "Stripe API keys for portal billing: checkout, billing portal, and webhook verification.";
    }

    @Override
    public Set<String> getSecretFields() {
        return Set.of("secretKey", "webhookSecret");
    }

    @Override
    public Set<String> getMetadataFields() {
        return Set.of("publishableKey", "allowedReturnOrigins");
    }

    @Override
    public List<String> validate(ObjectNode plaintext) {
        List<String> errors = new java.util.ArrayList<>(
                validateRequired(plaintext, "secretKey", "webhookSecret"));
        String secretKey = string(plaintext, "secretKey");
        if (secretKey != null && !secretKey.startsWith("sk_") && !secretKey.startsWith("rk_")) {
            // Catches the common paste error of storing the publishable key here,
            // which would otherwise fail much later with an opaque 401.
            errors.add("secretKey must be a Stripe secret or restricted key (sk_… or rk_…)");
        }
        String webhookSecret = string(plaintext, "webhookSecret");
        if (webhookSecret != null && !webhookSecret.startsWith("whsec_")) {
            errors.add("webhookSecret must be a Stripe endpoint signing secret (whsec_…)");
        }
        return errors;
    }

    /**
     * Verifies the secret key against {@code GET /v1/account} — the cheapest call
     * that proves the key is live and readable. The webhook secret cannot be
     * tested without a real delivery, so it is only format-checked above.
     */
    @Override
    public CredentialTestResult test(CredentialMaterial material, ObjectNode metadata) {
        String secretKey = string(material.plaintext(), "secretKey");
        if (secretKey == null) {
            return CredentialTestResult.failure("secretKey is required");
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ACCOUNT_URL))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return CredentialTestResult.success("Stripe account reachable with this key.");
            }
            if (response.statusCode() == 401) {
                return CredentialTestResult.failure("Stripe rejected the key (HTTP 401).");
            }
            return CredentialTestResult.failure(
                    "Stripe returned HTTP " + response.statusCode() + " for GET /v1/account.");
        } catch (Exception e) {
            // Never echo the response body — it can carry account details.
            return CredentialTestResult.failure("Could not reach Stripe: " + e.getMessage());
        }
    }
}
