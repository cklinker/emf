package io.kelta.worker.service.email;

/**
 * Thrown when an email delivery attempt fails.
 *
 * <p>Wraps provider-specific errors (SMTP, API, etc.) into a common exception.
 * Implementations MUST NOT include sensitive data (passwords, credentials) in the message.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
