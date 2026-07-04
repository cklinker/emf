package io.kelta.worker.service.email;

/**
 * A file attached to an outbound email.
 *
 * @param filename    the attachment filename shown to the recipient
 * @param contentType the MIME type (e.g. {@code text/csv})
 * @param content     the raw file bytes
 */
public record EmailAttachment(String filename, String contentType, byte[] content) {
}
