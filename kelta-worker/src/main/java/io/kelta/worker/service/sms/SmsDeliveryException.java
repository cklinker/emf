package io.kelta.worker.service.sms;

/**
 * Thrown when SMS delivery fails.
 *
 * @since 1.0.0
 */
public class SmsDeliveryException extends RuntimeException {
    public SmsDeliveryException(String message) {
        super(message);
    }

    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
