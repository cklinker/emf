package io.kelta.worker.controller;

import io.kelta.worker.service.billing.BillingWebhookService;
import io.kelta.worker.service.billing.StripeSignatureVerifier;
import io.kelta.worker.service.credential.CredentialResolver;
import io.kelta.worker.service.credential.ResolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound payment-processor webhooks for portal billing.
 *
 * <p>This is a gateway <b>unauthenticated</b> path: no JWT, no tenant context, no
 * {@code X-User-Id}. Its only trust anchor is the HMAC signature, verified
 * against the raw body before anything is parsed as an event.
 *
 * <p>The {@code tenantId} in the path is <b>untrusted input</b>. It selects which
 * tenant's signing secret to verify against — nothing more. An attacker can name
 * any tenant they like; without that tenant's secret the signature fails and the
 * request is rejected, so a passing signature is what proves the event belongs to
 * the named tenant.
 *
 * <p>Answers 200 for anything successfully verified — including duplicates and
 * event types the platform ignores — so the processor stops retrying. Only a
 * failed signature returns 401.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookController.class);

    /** Credential name holding the tenant's processor keys. */
    static final String CREDENTIAL_NAME = "stripe";

    private final StripeSignatureVerifier signatureVerifier;
    private final BillingWebhookService webhookService;
    private final CredentialResolver credentialResolver;

    public BillingWebhookController(StripeSignatureVerifier signatureVerifier,
                                    BillingWebhookService webhookService,
                                    CredentialResolver credentialResolver) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.credentialResolver = credentialResolver;
    }

    /** UNAUTHENTICATED path — signature verified before processing. */
    @PostMapping("/webhooks/stripe/{tenantId}")
    public ResponseEntity<Void> stripeWebhook(
            @PathVariable String tenantId,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        String webhookSecret = resolveWebhookSecret(tenantId);
        if (webhookSecret == null) {
            // Unknown tenant, or one with no processor credential. Answer exactly
            // as we would for a bad signature — a different status here would let
            // an attacker enumerate which tenants have billing configured.
            log.warn("Rejected billing webhook: no usable processor credential for tenant {}", tenantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!signatureVerifier.verify(signature, rawBody, webhookSecret)) {
            log.warn("Rejected billing webhook with invalid signature for tenant {}", tenantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            webhookService.process(tenantId, rawBody);
        } catch (RuntimeException e) {
            // The claim rolled back with the failure, so the processor's retry
            // gets a genuine second attempt. 500 asks it to retry.
            log.error("Failed to process billing webhook for tenant {}: {}",
                    tenantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    private String resolveWebhookSecret(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            Object secret = credentialResolver
                    .resolve(tenantId, CREDENTIAL_NAME,
                            ResolutionContext.forUser(null, "STRIPE_WEBHOOK_VERIFY"))
                    .secret("webhookSecret");
            if (secret == null || secret.toString().isBlank()) {
                return null;
            }
            return secret.toString();
        } catch (RuntimeException e) {
            // Never echo the reason: it would distinguish "no such tenant" from
            // "credential disabled" to an unauthenticated caller.
            log.debug("Could not resolve processor credential for tenant {}: {}",
                    tenantId, e.getMessage());
            return null;
        }
    }
}
