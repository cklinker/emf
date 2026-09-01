package io.kelta.worker.service.email;

/**
 * What the provider knows about a message once it has been handed off.
 *
 * <p>Carries the {@code Message-ID} the provider actually stamped on the outbound
 * message. That value cannot be chosen by the caller: Jakarta Mail generates it inside
 * {@code MimeMessage.saveChanges()}, which {@code Transport.send} invokes, overwriting
 * anything set beforehand. It can only be read back afterwards — which is why sending
 * returns a value at all.
 *
 * <p>It has to be read back, because it is the join key for the next inbound message:
 * a recipient's reply carries it in {@code In-Reply-To}, and without a record of what
 * we stamped, that reply cannot be matched to the conversation it belongs to.
 *
 * @param messageId the stamped {@code Message-ID} including angle brackets,
 *                  or {@code null} if the provider does not report one
 */
public record SendResult(String messageId) {

    private static final SendResult UNKNOWN = new SendResult(null);

    /** For providers that hand off without reporting an id. */
    public static SendResult unknown() {
        return UNKNOWN;
    }
}
