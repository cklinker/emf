package io.kelta.worker.service.sms;

/**
 * SPI for SMS delivery providers.
 *
 * <p>The platform ships with {@link LogOnlySmsProvider} as the default
 * (logs messages for development/testing). Users can implement this interface
 * to add production providers (e.g., Twilio, Vonage, Amazon SNS) and register
 * them as Spring beans.
 *
 * @since 1.0.0
 */
public interface SmsProvider {

    /**
     * Sends an SMS message.
     *
     * @param message the SMS content (recipient phone + body)
     * @throws SmsDeliveryException if delivery fails
     */
    void send(SmsMessage message) throws SmsDeliveryException;
}
