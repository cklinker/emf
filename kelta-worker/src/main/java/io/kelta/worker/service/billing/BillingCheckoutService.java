package io.kelta.worker.service.billing;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.worker.repository.BillingCustomer;
import io.kelta.worker.repository.BillingCustomerRepository;
import io.kelta.worker.repository.BillingPlan;
import io.kelta.worker.repository.BillingPlanRepository;
import io.kelta.worker.service.credential.CredentialResolver;
import io.kelta.worker.service.credential.ResolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Member-facing commerce operations: starting a checkout and opening the
 * processor's billing portal.
 *
 * <p>Every call is scoped to the <b>calling</b> member — the caller never names
 * whose subscription to act on, so there is no id to tamper with.
 *
 * <p>Return URLs are validated against the tenant's registered origins before
 * they reach the processor; see {@link ReturnUrlValidator} for why that matters.
 */
@Service
public class BillingCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(BillingCheckoutService.class);

    /** Credential name holding the tenant's processor keys. */
    static final String CREDENTIAL_NAME = "stripe";

    private final BillingPlanRepository planRepository;
    private final BillingCustomerRepository customerRepository;
    private final StripeApiClient stripeApiClient;
    private final CredentialResolver credentialResolver;
    private final ReturnUrlValidator returnUrlValidator;

    public BillingCheckoutService(BillingPlanRepository planRepository,
                                  BillingCustomerRepository customerRepository,
                                  StripeApiClient stripeApiClient,
                                  CredentialResolver credentialResolver,
                                  ReturnUrlValidator returnUrlValidator) {
        this.planRepository = planRepository;
        this.customerRepository = customerRepository;
        this.stripeApiClient = stripeApiClient;
        this.credentialResolver = credentialResolver;
        this.returnUrlValidator = returnUrlValidator;
    }

    /**
     * Starts a hosted checkout for {@code planCode} and returns the URL to send
     * the member to.
     */
    public String createCheckoutSession(String tenantId, String userId,
                                        String planCode, String successUrl, String cancelUrl) {
        BillingPlan plan = planRepository.findByCode(tenantId, planCode)
                .filter(BillingPlan::active)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown plan"));

        if (BillingPlan.KIND_DEFAULT.equals(plan.kind())) {
            // The free baseline is what a member falls back to; it is not for sale.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Plan is not purchasable");
        }
        if (plan.stripePriceId() == null || plan.stripePriceId().isBlank()) {
            log.warn("Plan {} of tenant {} has no processor price id — cannot check out",
                    planCode, tenantId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Plan is not available for purchase");
        }

        ResolvedCredential credential = resolveCredential(tenantId);
        List<String> allowedOrigins = allowedReturnOrigins(credential);
        requireAllowedUrl(successUrl, allowedOrigins, "successUrl");
        requireAllowedUrl(cancelUrl, allowedOrigins, "cancelUrl");

        String secretKey = secret(credential, "secretKey");
        String customerId = customerRepository.findByUserId(tenantId, userId)
                .map(BillingCustomer::stripeCustomerId)
                .orElse(null);

        String mode = BillingPlan.KIND_SUBSCRIPTION.equals(plan.kind())
                ? "subscription"
                : "payment";

        try {
            JsonNode session = stripeApiClient.createCheckoutSession(
                    secretKey, tenantId, userId, mode, plan.stripePriceId(), plan.code(),
                    customerId, successUrl, cancelUrl);
            String url = text(session, "url");
            if (url == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Processor did not return a checkout URL");
            }
            return url;
        } catch (StripeApiException e) {
            throw processorFailure(e, "checkout session");
        }
    }

    /**
     * Opens the processor's billing portal for the member. Requires an existing
     * customer — a member who has never transacted has nothing to manage.
     */
    public String createPortalSession(String tenantId, String userId, String returnUrl) {
        BillingCustomer customer = customerRepository.findByUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No billing account for this member"));

        ResolvedCredential credential = resolveCredential(tenantId);
        requireAllowedUrl(returnUrl, allowedReturnOrigins(credential), "returnUrl");

        try {
            JsonNode session = stripeApiClient.createBillingPortalSession(
                    secret(credential, "secretKey"), customer.stripeCustomerId(), returnUrl);
            String url = text(session, "url");
            if (url == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Processor did not return a portal URL");
            }
            return url;
        } catch (StripeApiException e) {
            throw processorFailure(e, "billing portal session");
        }
    }

    // ------------------------------------------------------------- Helpers

    private ResolvedCredential resolveCredential(String tenantId) {
        try {
            return credentialResolver.resolve(tenantId, CREDENTIAL_NAME,
                    ResolutionContext.forUser(null, "STRIPE_API:checkout"));
        } catch (RuntimeException e) {
            log.warn("Tenant {} has no usable processor credential: {}", tenantId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Billing is not configured for this tenant");
        }
    }

    private void requireAllowedUrl(String url, List<String> allowedOrigins, String field) {
        if (!returnUrlValidator.isAllowed(url, allowedOrigins)) {
            // Log the rejected value (it is attacker-supplied but not secret) so a
            // tenant misconfiguration is diagnosable; do not echo it to the caller.
            log.warn("Rejected {} outside the tenant's allowed return origins: {}", field, url);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " is not an allowed return URL");
        }
    }

    /**
     * Reads {@code allowedReturnOrigins} from credential metadata. Tolerates both
     * a JSON array and a comma-separated string, since the value is
     * tenant-authored through the credential UI.
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

    private static String secret(ResolvedCredential credential, String key) {
        Object value = credential.secret(key);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Billing is not configured for this tenant");
        }
        return value.toString();
    }

    /**
     * Maps a processor failure to a caller-safe status. The processor's own
     * message is logged, never returned — it can name account internals.
     */
    private ResponseStatusException processorFailure(StripeApiException e, String what) {
        log.error("Processor rejected {} (HTTP {}, type={}, code={}): {}",
                what, e.getStatus(), e.getErrorType(), e.getErrorCode(), e.getMessage());
        if (e.isAuthFailure()) {
            // The tenant's key is bad — an operator problem, not the member's.
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "Billing is not configured correctly for this tenant");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Could not start " + what);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String s = value.stringValue();
        return s.isBlank() ? null : s;
    }
}
