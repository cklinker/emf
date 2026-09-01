package io.kelta.worker.service.mailbox.inbound;

import io.kelta.worker.security.SnsSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Optional;

/**
 * SES receipt rule → SNS notification.
 *
 * <p>The receipt rule uses a single S3 action carrying a {@code TopicArn}, so the notification
 * says where the message was stored rather than containing it. A bare SNS action would inline the
 * message instead, but SNS caps at 256 KB — roughly 110 KB of mail after base64 — and
 * <b>SES truncates silently</b> past that. One screenshot would cost the body with no error
 * anywhere, so the inline shape exists only as {@code SES_SNS_INLINE} for local development.
 *
 * @since 1.0.0
 */
@Component
public class SesSnsInboundAdapter implements InboundMailAdapter {

    private static final Logger log = LoggerFactory.getLogger(SesSnsInboundAdapter.class);

    public static final String PROVIDER = "SES_SNS";
    public static final String PROVIDER_INLINE = "SES_SNS_INLINE";

    private final SnsSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /**
     * {@link SnsSignatureVerifier} is not a Spring bean — {@code SesNotificationController}
     * constructs its own the same way. Injecting it would fail context startup, which unit tests
     * cannot see because none of them load the context.
     *
     * <p>The cost is a second certificate cache alongside the bounce controller's. That is a few
     * cached X.509 certs, refetched once per pod, and it keeps this change from touching the
     * live bounce path.
     */
    @Autowired
    public SesSnsInboundAdapter(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.signatureVerifier = new SnsSignatureVerifier(RestClient.create());
    }

    /**
     * Visible for testing, so a test can supply a verifier that does not fetch certificates.
     *
     * <p>Its presence is why the constructor above carries {@code @Autowired}: with two
     * candidates and neither annotated, Spring cannot choose and falls back to looking for a
     * no-arg default, which fails the whole context at startup.
     */
    SesSnsInboundAdapter(SnsSignatureVerifier signatureVerifier, ObjectMapper objectMapper,
                         RestClient restClient) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public Optional<ResponseEntity<Void>> handleControl(InboundRequest request, MailboxRef mailbox) {
        JsonNode envelope = readTree(request);
        if (envelope == null) {
            return Optional.empty();
        }
        String type = envelope.path("Type").asText(null);

        if ("SubscriptionConfirmation".equals(type)) {
            // Only confirm after the signature and topic pin have already passed — verify() runs
            // before this — and only to a genuine SNS host, or this is an SSRF primitive.
            String subscribeUrl = envelope.path("SubscribeURL").asText(null);
            if (subscribeUrl != null && SnsSignatureVerifier.isRealSnsCertUrl(subscribeUrl)) {
                try {
                    restClient.get().uri(subscribeUrl).retrieve().toBodilessEntity();
                    log.info("Confirmed SNS subscription for mailbox {}", mailbox.id());
                } catch (Exception e) {
                    log.warn("Failed to confirm SNS subscription for mailbox {}: {}",
                            mailbox.id(), e.getMessage());
                }
            } else {
                log.warn("Refusing to confirm SNS subscription: SubscribeURL is not an SNS endpoint");
            }
            return Optional.of(ResponseEntity.ok().build());
        }

        if ("UnsubscribeConfirmation".equals(type)) {
            // Deliberately not auto-resubscribed. AWS sends this after *any* unsubscribe,
            // including an operator pausing the feed on purpose during an incident — silently
            // undoing that would leave no way to stop the feed short of deleting the topic.
            log.info("SNS subscription removed for mailbox {} — not auto-resubscribing", mailbox.id());
            return Optional.of(ResponseEntity.ok().build());
        }

        return Optional.empty();
    }

    @Override
    public boolean verify(InboundRequest request, MailboxRef mailbox, InboundSecrets secrets) {
        JsonNode envelope = readTree(request);
        if (envelope == null) {
            return false;
        }
        // The TopicArn pin is what makes the signature mean anything. A valid signature only
        // proves some SNS topic in some AWS account signed this, and anyone can create a topic
        // and publish to a public endpoint.
        return signatureVerifier.verify(envelope, mailbox.topicArn());
    }

    @Override
    public Optional<String> providerEventId(InboundRequest request) {
        JsonNode envelope = readTree(request);
        if (envelope == null) {
            return Optional.empty();
        }
        String id = envelope.path("MessageId").asText(null);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    @Override
    public InboundEnvelope extract(InboundRequest request, MailboxRef mailbox) {
        JsonNode envelope = readTree(request);
        if (envelope == null || !"Notification".equals(envelope.path("Type").asText(null))) {
            return InboundEnvelope.ignored();
        }

        JsonNode message = parseInner(envelope.path("Message").asText(null));
        if (message == null) {
            return InboundEnvelope.ignored();
        }

        // SES sends a setup probe when a receipt rule is first pointed at a topic. It has no
        // mail payload, and treating it as a message would create an empty thread on day one.
        if (message.has("notificationType")
                && !"Received".equals(message.path("notificationType").asText(null))) {
            return InboundEnvelope.ignored();
        }

        NormalizedInboundMail.Verdicts verdicts = verdictsOf(message.path("receipt"));

        // Inline content, when the rule was configured that way (dev only).
        String content = message.path("content").asText(null);
        if (content != null && !content.isBlank()) {
            try {
                return InboundEnvelope.ofRaw(Base64.getMimeDecoder().decode(content), verdicts);
            } catch (IllegalArgumentException e) {
                // Not base64 — SES sends the raw string in some configurations.
                return InboundEnvelope.ofRaw(content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        verdicts);
            }
        }

        JsonNode action = message.path("receipt").path("action");
        String bucket = action.path("bucketName").asText(null);
        String key = action.path("objectKey").asText(null);
        if (bucket == null || key == null) {
            log.warn("SES notification for mailbox {} carried neither inline content nor an S3 object",
                    mailbox.id());
            return InboundEnvelope.ignored();
        }
        return InboundEnvelope.ofDeferred(new DeferredFetch(bucket, key), verdicts);
    }

    /**
     * Reads SES's own verdicts.
     *
     * <p>These are computed by the MTA that accepted the connection and cannot be recomputed
     * later — SPF is a property of the connecting IP, which is gone by the time we hold the
     * bytes. Anything derived downstream would be a guess wearing a verdict's name.
     */
    private NormalizedInboundMail.Verdicts verdictsOf(JsonNode receipt) {
        return new NormalizedInboundMail.Verdicts(
                verdict(receipt, "spfVerdict"),
                verdict(receipt, "dkimVerdict"),
                verdict(receipt, "dmarcVerdict"),
                receipt.path("dmarcPolicy").asText(null),
                verdict(receipt, "spamVerdict"),
                verdict(receipt, "virusVerdict"));
    }

    private String verdict(JsonNode receipt, String name) {
        String v = receipt.path(name).path("status").asText(null);
        return v == null || v.isBlank() ? null : v;
    }

    private JsonNode readTree(InboundRequest request) {
        try {
            return objectMapper.readTree(request.bodyAsString());
        } catch (Exception e) {
            log.warn("SES/SNS webhook body is not JSON: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode parseInner(String message) {
        if (message == null) {
            return null;
        }
        try {
            return objectMapper.readTree(message);
        } catch (Exception e) {
            log.warn("SNS Message payload is not JSON: {}", e.getMessage());
            return null;
        }
    }
}
