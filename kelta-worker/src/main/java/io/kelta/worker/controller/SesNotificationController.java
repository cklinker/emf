package io.kelta.worker.controller;

import io.kelta.worker.repository.EmailRepository;
import io.kelta.worker.repository.EmailSuppressionRepository;
import io.kelta.worker.security.SnsSignatureVerifier;
import io.kelta.worker.util.TenantContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Receives Amazon SNS notifications for the SES identities a tenant points its per-tenant SMTP
 * override at (see {@link TenantEmailSettingsController}) and turns hard bounces and spam
 * complaints into {@code email_suppression} rows, so a later send to that address is blocked
 * before SES ever sees it again.
 *
 * <p>Unauthenticated — covered by the gateway's existing {@code /api/webhooks/**} allowlist (see
 * {@code FlowWebhookController}) — so {@link SnsSignatureVerifier} is the sole authenticator. A
 * message that fails verification, or isn't one we act on, is dropped with a 200: SNS retries
 * on non-2xx, and there is nothing useful to retry for a forged or irrelevant message.
 *
 * <p>The tenant is resolved from the notification's {@code mail.source} — the exact From address
 * used for that send — matched against {@code tenant.email_from_address}. That generalizes past
 * spotopened: any tenant that points its SMTP override at an SES identity gets bounce handling
 * for free once that identity's Bounce/Complaint topics are pointed at this endpoint.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/webhooks/ses")
public class SesNotificationController {

    private static final Logger log = LoggerFactory.getLogger(SesNotificationController.class);

    private final RestClient restClient;
    private final SnsSignatureVerifier signatureVerifier;
    private final EmailSuppressionRepository suppressionRepository;
    private final EmailRepository emailRepository;
    private final ObjectMapper objectMapper;

    public SesNotificationController(EmailSuppressionRepository suppressionRepository,
                                      EmailRepository emailRepository,
                                      ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.signatureVerifier = new SnsSignatureVerifier(restClient);
        this.suppressionRepository = suppressionRepository;
        this.emailRepository = emailRepository;
        this.objectMapper = objectMapper;
    }

    /** SNS posts with Content-Type text/plain even though the body is JSON. */
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestBody String rawBody) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Discarding SES/SNS webhook: body is not JSON ({})", e.getMessage());
            return ResponseEntity.ok().build();
        }

        if (!signatureVerifier.verify(envelope)) {
            log.warn("Discarding SES/SNS webhook: signature verification failed");
            return ResponseEntity.ok().build();
        }

        String type = envelope.path("Type").asText(null);
        if ("SubscriptionConfirmation".equals(type) || "UnsubscribeConfirmation".equals(type)) {
            confirmSubscription(envelope);
        } else if ("Notification".equals(type)) {
            handleNotification(envelope.path("Message").asText(null));
        }
        return ResponseEntity.ok().build();
    }

    private void confirmSubscription(JsonNode envelope) {
        String subscribeUrl = envelope.path("SubscribeURL").asText(null);
        if (subscribeUrl == null || !SnsSignatureVerifier.isRealSnsCertUrl(subscribeUrl)) {
            log.warn("Refusing to confirm SNS subscription: SubscribeURL '{}' is not a real SNS endpoint",
                    subscribeUrl);
            return;
        }
        try {
            restClient.get().uri(subscribeUrl).retrieve().toBodilessEntity();
            log.info("Confirmed SNS subscription for topic {}", envelope.path("TopicArn").asText(null));
        } catch (Exception e) {
            log.warn("Failed to confirm SNS subscription: {}", e.getMessage());
        }
    }

    private void handleNotification(String messageJson) {
        if (messageJson == null) return;
        JsonNode ses;
        try {
            ses = objectMapper.readTree(messageJson);
        } catch (Exception e) {
            log.warn("Discarding SES notification: inner Message is not JSON ({})", e.getMessage());
            return;
        }

        String fromAddress = ses.path("mail").path("source").asText(null);
        if (fromAddress == null) return;

        String tenantId = emailRepository.findTenantIdByFromAddress(fromAddress).orElse(null);
        if (tenantId == null) {
            log.warn("SES notification for unrecognized From address '{}' — no tenant matches it", fromAddress);
            return;
        }

        String notificationType = ses.path("notificationType").asText(null);
        List<String> recipients = new ArrayList<>();
        String reason;
        if ("Bounce".equals(notificationType)) {
            JsonNode bounce = ses.path("bounce");
            // Only hard (Permanent) bounces are suppressed — a Transient bounce (mailbox full,
            // greylisting) is often recoverable and shouldn't permanently blacklist the address.
            if (!"Permanent".equals(bounce.path("bounceType").asText(null))) {
                return;
            }
            reason = "BOUNCE";
            for (JsonNode r : bounce.path("bouncedRecipients")) {
                addRecipient(recipients, r.path("emailAddress").asText(null));
            }
        } else if ("Complaint".equals(notificationType)) {
            reason = "COMPLAINT";
            for (JsonNode r : ses.path("complaint").path("complainedRecipients")) {
                addRecipient(recipients, r.path("emailAddress").asText(null));
            }
        } else {
            return; // Delivery notifications carry nothing to suppress.
        }
        if (recipients.isEmpty()) return;

        String finalReason = reason;
        try {
            TenantContextUtils.withTenant(tenantId, () -> {
                for (String email : recipients) {
                    suppressionRepository.add(tenantId, email, finalReason, null, null);
                }
            });
            log.info("Suppressed {} address(es) for tenant {} ({})", recipients.size(), tenantId, finalReason);
        } catch (Exception e) {
            log.warn("Failed to record suppression for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private static void addRecipient(List<String> into, String email) {
        if (email != null && !email.isBlank()) into.add(email);
    }
}
