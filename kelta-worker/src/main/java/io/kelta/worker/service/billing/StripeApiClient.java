package io.kelta.worker.service.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal REST client for the payment processor.
 *
 * <p>Deliberately <b>not</b> {@code stripe-java}: its large Gson-reflective model
 * surface has no maintained reachability metadata and would need extensive
 * {@code reflect-config.json} entries to survive the native image. The platform
 * makes three calls, so form-encoded requests and {@code JsonNode} responses on
 * Spring's {@link RestClient} (the native-proven client already used by
 * {@code RestClientExecutor}) are cheaper and safer.
 *
 * <p><b>API version is pinned.</b> The processor rolls its API forward per
 * account; without an explicit {@code Stripe-Version} header, response shapes
 * could change under a running deployment. The pin and the webhook parser must
 * move together.
 *
 * <p><b>Every POST carries an {@code Idempotency-Key}.</b> A retried create must
 * not mint a second customer or a second checkout session.
 *
 * <p>Non-2xx responses are surfaced as {@link StripeApiException} carrying the
 * processor's own error type/code — the default {@code RestClient} error
 * handling is suppressed so the error body can be read rather than discarded.
 */
@Component
public class StripeApiClient {

    private static final Logger log = LoggerFactory.getLogger(StripeApiClient.class);

    private static final String BASE_URL = "https://api.stripe.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiVersion;

    /**
     * The constructor Spring uses. {@code @Autowired} is required, not decorative:
     * this class has a second (test-seam) constructor, and with more than one
     * candidate Spring will not guess — it falls back to looking for a no-arg
     * constructor and fails bean creation outright.
     */
    @Autowired
    public StripeApiClient(ObjectMapper objectMapper,
                           @Value("${kelta.billing.stripe.api-version:2024-06-20}") String apiVersion) {
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
        this.restClient = RestClient.create();
    }

    /** Test seam: lets a unit test bind a {@code MockRestServiceServer}-backed client. */
    StripeApiClient(RestClient restClient, ObjectMapper objectMapper, String apiVersion) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
    }

    /**
     * Creates a processor customer for a member.
     *
     * <p>{@code metadata} carries the tenant and member ids so a webhook can
     * resolve the member even when the event body omits
     * {@code client_reference_id}.
     */
    public JsonNode createCustomer(String secretKey, String tenantId, String userId, String email) {
        StripeFormBody body = new StripeFormBody()
                .add("email", email)
                .addMap("metadata", Map.of("tenantId", tenantId, "userId", userId));
        // DETERMINISTIC: a member gets exactly one processor customer, ever. If two
        // concurrent requests race (two tabs, or a retry after a timeout we never
        // saw the response to), the processor replays the first result instead of
        // creating a duplicate that would then violate the unique constraint on
        // billing_customer.
        return post(secretKey, "/v1/customers", body, "cust:" + tenantId + ":" + userId);
    }

    /**
     * Creates a hosted checkout session.
     *
     * @param mode        {@code subscription} or {@code payment}
     * @param priceId     the processor price the member is buying
     * @param planCode    platform plan code, echoed back on the completed event
     * @param customerId  existing processor customer, or null to let checkout create one
     */
    public JsonNode createCheckoutSession(String secretKey, String tenantId, String userId,
                                          String mode, String priceId, String planCode,
                                          String customerId, String successUrl, String cancelUrl) {
        Map<String, String> metadata =
                Map.of("tenantId", tenantId, "userId", userId, "planCode", planCode);

        StripeFormBody body = new StripeFormBody()
                .add("mode", mode)
                .add("success_url", successUrl)
                .add("cancel_url", cancelUrl)
                // Ties the session back to the platform member without relying on
                // metadata surviving every event shape.
                .add("client_reference_id", userId)
                .add("customer", customerId)
                .add("line_items[0][price]", priceId)
                .add("line_items[0][quantity]", "1")
                // The processor computes and remits tax; the platform never does.
                .add("automatic_tax[enabled]", true)
                .addMap("metadata", metadata);

        if ("subscription".equals(mode)) {
            // Metadata on the session does NOT propagate to the subscription
            // object, so stamp it on both — the subscription webhook reads its own.
            body.addMap("subscription_data[metadata]", metadata);
        }

        // PER-ATTEMPT: each member click is a distinct purchase attempt, so this key
        // must NOT be derived from (member, plan). A deterministic key would replay
        // the previous session for the processor's 24h idempotency window — which
        // would stop a member buying a second one-time pass of the same plan on the
        // same day. There is no retry loop above this call, so one call is one
        // attempt.
        return post(secretKey, "/v1/checkout/sessions", body, newAttemptKey("checkout"));
    }

    /** Creates a billing-portal session so a member can self-serve their subscription. */
    public JsonNode createBillingPortalSession(String secretKey, String customerId, String returnUrl) {
        StripeFormBody body = new StripeFormBody()
                .add("customer", customerId)
                .add("return_url", returnUrl);
        // Per-attempt for the same reason as checkout; portal sessions are
        // short-lived and a member may legitimately open one repeatedly.
        return post(secretKey, "/v1/billing_portal/sessions", body, newAttemptKey("portal"));
    }

    // ------------------------------------------------------------- Transport

    private JsonNode post(String secretKey, String path, StripeFormBody body, String idempotencyKey) {
        ResponseEntity<String> response = restClient
                .method(HttpMethod.POST)
                .uri(BASE_URL + path)
                .header("Authorization", "Bearer " + secretKey)
                .header("Stripe-Version", apiVersion)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(body.encode())
                // Suppress default 4xx/5xx throwing so the processor's error body
                // is readable — its `code` is the whole point of the response.
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);

        return parse(path, response.getStatusCode().value(), response.getBody());
    }

    private JsonNode parse(String path, int status, String rawBody) {
        JsonNode json;
        try {
            json = rawBody == null || rawBody.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(rawBody);
        } catch (RuntimeException e) {
            throw new StripeApiException(status, null, null,
                    "Unreadable response from " + path + " (HTTP " + status + ")");
        }

        if (status / 100 == 2) {
            return json;
        }

        JsonNode error = json.path("error");
        String type = text(error, "type");
        String code = text(error, "code");
        String message = text(error, "message");
        log.warn("Processor call {} failed: HTTP {} type={} code={}", path, status, type, code);
        throw new StripeApiException(status, type, code,
                message != null ? message : "Processor returned HTTP " + status + " for " + path);
    }

    /**
     * A fresh key for an operation where each call is a genuinely new attempt.
     * Still sent, so that if a transport-level retry is ever added above this
     * client it has a key to reuse.
     */
    private static String newAttemptKey(String operation) {
        return operation + ":" + UUID.randomUUID();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String s = value.stringValue();
        return s.isBlank() ? null : s;
    }

    /** Modes the platform supports at checkout. */
    public static final List<String> SUPPORTED_MODES = List.of("subscription", "payment");
}
