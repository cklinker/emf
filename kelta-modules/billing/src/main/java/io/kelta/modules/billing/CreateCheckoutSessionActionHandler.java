package io.kelta.modules.billing;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.runtime.module.integration.spi.CredentialResolverPort;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Starts a Stripe hosted checkout for the calling member and returns the URL to send them to.
 *
 * <p>Invoked as a flow action (`execute_flow`), which is how a module exposes an operation to the
 * end-user app without a controller of its own. The caller never names whose subscription to act
 * on — the member comes from the flow's actor — so there is no id to tamper with.
 *
 * <p>Return URLs are validated against the tenant's registered origins before they reach Stripe.
 * Without that check a caller could redirect a paying member to a page they control.
 */
public class CreateCheckoutSessionActionHandler implements ActionHandler {

    public static final String KEY = "billing:create-checkout-session";

    private static final Logger log =
            LoggerFactory.getLogger(CreateCheckoutSessionActionHandler.class);

    static final String CREDENTIAL_NAME = "stripe";
    static final String KIND_DEFAULT = "DEFAULT";
    static final String KIND_SUBSCRIPTION = "SUBSCRIPTION";

    private final BillingCollections collections;
    private final CredentialResolverPort credentialResolver;
    private final StripeApiClient stripeApiClient;

    public CreateCheckoutSessionActionHandler(BillingCollections collections,
                                              CredentialResolverPort credentialResolver,
                                              StripeApiClient stripeApiClient) {
        this.collections = collections;
        this.credentialResolver = credentialResolver;
        this.stripeApiClient = stripeApiClient;
    }

    @Override
    public String getActionTypeKey() {
        return KEY;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String tenantId = context.tenantId();
        String userId = context.userId();
        Map<String, Object> input = context.resolvedData() == null
                ? Map.of() : context.resolvedData();

        String planCode = string(input, "planCode");
        String successUrl = string(input, "successUrl");
        String cancelUrl = string(input, "cancelUrl");
        if (userId == null || userId.isBlank()) {
            return ActionResult.failure("No calling member");
        }
        if (planCode == null || successUrl == null || cancelUrl == null) {
            return ActionResult.failure("planCode, successUrl and cancelUrl are required");
        }

        Optional<Map<String, Object>> found = collections.findPlanByCode(planCode);
        if (found.isEmpty() || !Boolean.TRUE.equals(found.get().get("active"))) {
            return ActionResult.failure("Unknown plan");
        }
        Map<String, Object> plan = found.get();

        String kind = String.valueOf(plan.getOrDefault("kind", KIND_SUBSCRIPTION));
        if (KIND_DEFAULT.equals(kind)) {
            // The free baseline is what a member falls back to; it is not for sale.
            return ActionResult.failure("Plan is not purchasable");
        }
        String priceId = string(plan, "stripePriceId");
        if (priceId == null) {
            log.warn("Plan {} of tenant {} has no Stripe price id — cannot check out",
                    planCode, tenantId);
            return ActionResult.failure("Plan is not available for purchase");
        }

        ResolvedCredential credential;
        try {
            credential = credentialResolver.resolve(tenantId, CREDENTIAL_NAME,
                    "STRIPE_API:checkout");
        } catch (RuntimeException e) {
            log.warn("Tenant {} has no usable Stripe credential: {}", tenantId, e.getMessage());
            return ActionResult.failure("Billing is not configured for this tenant");
        }

        List<String> allowedOrigins = allowedReturnOrigins(credential);
        if (!isAllowedUrl(successUrl, allowedOrigins) || !isAllowedUrl(cancelUrl, allowedOrigins)) {
            // Log the rejected value — attacker-supplied but not secret — so a tenant
            // misconfiguration is diagnosable. Never echo it to the caller.
            log.warn("Rejected return URL outside tenant {}'s allowed origins", tenantId);
            return ActionResult.failure("Return URL is not allowed");
        }

        Object secretKey = credential.secret("secretKey");
        if (secretKey == null || secretKey.toString().isBlank()) {
            return ActionResult.failure("Billing is not configured for this tenant");
        }

        String customerId = collections.findCustomerByUserId(userId)
                .map(c -> String.valueOf(c.get("stripeCustomerId")))
                .orElse(null);
        String mode = KIND_SUBSCRIPTION.equals(kind) ? "subscription" : "payment";

        try {
            JsonNode session = stripeApiClient.createCheckoutSession(
                    secretKey.toString(), tenantId, userId, mode, priceId, planCode,
                    customerId, successUrl, cancelUrl);
            JsonNode url = session.get("url");
            if (url == null || !url.isTextual()) {
                return ActionResult.failure("Stripe did not return a checkout URL");
            }
            return ActionResult.success(Map.of("url", url.stringValue()));
        } catch (StripeApiException e) {
            // Stripe's own message is logged, never returned — it can name account internals.
            log.error("Stripe rejected a checkout session (HTTP {}, type={}, code={}): {}",
                    e.getStatus(), e.getErrorType(), e.getErrorCode(), e.getMessage());
            return ActionResult.failure(e.isAuthFailure()
                    ? "Billing is not configured correctly for this tenant"
                    : "Could not start checkout");
        }
    }

    /**
     * Reads {@code allowedReturnOrigins} from credential metadata, tolerating both a JSON array
     * and a comma-separated string — the value is tenant-authored through the credential UI.
     */
    static List<String> allowedReturnOrigins(ResolvedCredential credential) {
        Object raw = credential.metadata("allowedReturnOrigins");
        List<String> origins = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(o -> {
                if (o != null && !o.toString().isBlank()) {
                    origins.add(o.toString().trim());
                }
            });
        } else if (raw instanceof String s && !s.isBlank()) {
            for (String part : s.split(",")) {
                if (!part.isBlank()) {
                    origins.add(part.trim());
                }
            }
        }
        return origins;
    }

    /**
     * Matches on scheme + host + port, never a string prefix: {@code https://good.example.com} must
     * not authorize {@code https://good.example.com.evil.test}.
     *
     * <p>Fails closed — no configured origins means no allowed URL.
     */
    static boolean isAllowedUrl(String url, List<String> allowedOrigins) {
        if (url == null || url.isBlank() || allowedOrigins.isEmpty()) {
            return false;
        }
        URI candidate;
        try {
            candidate = URI.create(url);
        } catch (RuntimeException e) {
            return false;
        }
        if (candidate.getHost() == null || candidate.getScheme() == null) {
            return false;
        }
        for (String allowed : allowedOrigins) {
            try {
                URI origin = URI.create(allowed);
                if (origin.getHost() == null) {
                    continue;
                }
                boolean sameScheme = candidate.getScheme()
                        .equalsIgnoreCase(origin.getScheme());
                boolean sameHost = candidate.getHost().toLowerCase(Locale.ROOT)
                        .equals(origin.getHost().toLowerCase(Locale.ROOT));
                boolean samePort = candidate.getPort() == origin.getPort();
                if (sameScheme && sameHost && samePort) {
                    return true;
                }
            } catch (RuntimeException e) {
                // A malformed configured origin authorizes nothing.
            }
        }
        return false;
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
