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
 * <p>{@code delivered} is separate from {@code messageId} because the two genuinely differ.
 * A provider may accept a message and report no id ({@code delivered=true, messageId=null});
 * a send may fail outright ({@code delivered=false}). Collapsing both into one "unknown" value —
 * as an earlier version did — left the caller unable to tell a sent reply from a failed one, so
 * every outbound message was recorded with the same optimistic status regardless of what happened.
 *
 * @param messageId the stamped {@code Message-ID} including angle brackets,
 *                  or {@code null} if the provider does not report one
 * @param delivered whether the provider accepted the message for delivery
 */
public record SendResult(String messageId, boolean delivered) {

    /** The provider accepted the message. {@code messageId} may be null if it reported none. */
    public static SendResult sent(String messageId) {
        return new SendResult(messageId, true);
    }

    /** The message was not handed off — delivery failed, or sending is disabled. */
    public static SendResult notDelivered() {
        return new SendResult(null, false);
    }
}
