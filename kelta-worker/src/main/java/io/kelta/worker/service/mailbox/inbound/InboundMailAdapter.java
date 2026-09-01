package io.kelta.worker.service.mailbox.inbound;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

/**
 * One inbound-mail provider.
 *
 * <p>Implementations own everything provider-specific: how a request is authenticated, how a
 * stable delivery id is extracted for idempotency, and how the payload becomes bytes or fields.
 * They produce an {@link InboundEnvelope} and nothing downstream knows which provider ran.
 *
 * <p>The provider is chosen from the <b>mailbox row</b>, never from the request. Letting a caller
 * pick the adapter — via a URL segment or a header — would let anyone who learns a mailbox key
 * choose the weakest verifier available.
 *
 * @since 1.0.0
 */
public interface InboundMailAdapter {

    /** Matches {@code mailbox.inbound_provider}. */
    String providerId();

    /**
     * Handles a non-message control request, such as an SNS subscription confirmation.
     *
     * @return a response to return immediately, or empty to continue with normal ingest
     */
    default Optional<ResponseEntity<Void>> handleControl(InboundRequest request, MailboxRef mailbox) {
        return Optional.empty();
    }

    /**
     * Authenticates the request.
     *
     * <p>Always required. The mailbox key in the URL is an identifier, not a credential — it
     * appears in provider consoles and access logs, so it can only say <i>which</i> mailbox, never
     * <i>whether</i> the caller is allowed.
     */
    boolean verify(InboundRequest request, MailboxRef mailbox, InboundSecrets secrets);

    /**
     * The provider's own id for this delivery, used as the idempotency key.
     *
     * <p>Empty when the provider has none, in which case the pipeline falls back to hashing the
     * body.
     */
    default Optional<String> providerEventId(InboundRequest request) {
        return Optional.empty();
    }

    /** Extracts the message. */
    InboundEnvelope extract(InboundRequest request, MailboxRef mailbox);

    /** The raw HTTP request, kept as bytes because signatures cover exact bytes. */
    record InboundRequest(byte[] rawBody, Map<String, String> headers, String remoteIp) {

        public InboundRequest {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        /** Header lookup is case-insensitive per RFC 9110. */
        public String header(String name) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return null;
        }

        public String bodyAsString() {
            return new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** The subset of the mailbox row an adapter is allowed to see. */
    record MailboxRef(String id, String tenantId, String provider, String topicArn,
                      String allowedCidrs, long maxMessageBytes, int maxAttachments,
                      long maxAttachmentBytes) {
    }

    /**
     * Decrypted signing material.
     *
     * <p>Two slots because rotation overlaps: a provider cannot change its signing key at the
     * same instant we do, so for a window both must verify or every in-flight delivery is
     * rejected — and a rejected inbound message is a lost customer email.
     */
    record InboundSecrets(String current, String previous) {

        public static InboundSecrets none() {
            return new InboundSecrets(null, null);
        }
    }

    /**
     * What an adapter produces.
     *
     * <p>Exactly one of {@code rawMime} and {@code preParsed} is set: providers that hand over the
     * original message go through the MIME parser, and providers that already parsed it map their
     * fields directly. Both converge on {@link NormalizedInboundMail}.
     *
     * @param deferredFetch when set, the raw MIME is not in the request and must be fetched from
     *                      object storage — SES's S3 action, where the notification carries only a
     *                      bucket and key
     */
    record InboundEnvelope(byte[] rawMime,
                           NormalizedInboundMail preParsed,
                           NormalizedInboundMail.Verdicts verdicts,
                           DeferredFetch deferredFetch,
                           boolean ignore) {

        /** Nothing to ingest — a control message, or a notification we do not act on. */
        public static InboundEnvelope ignored() {
            return new InboundEnvelope(null, null, null, null, true);
        }

        public static InboundEnvelope ofRaw(byte[] rawMime, NormalizedInboundMail.Verdicts verdicts) {
            return new InboundEnvelope(rawMime, null, verdicts, null, false);
        }

        public static InboundEnvelope ofDeferred(DeferredFetch fetch,
                                                 NormalizedInboundMail.Verdicts verdicts) {
            return new InboundEnvelope(null, null, verdicts, fetch, false);
        }
    }

    /** Where to go and get the message body from. */
    record DeferredFetch(String bucket, String key) {
    }
}
