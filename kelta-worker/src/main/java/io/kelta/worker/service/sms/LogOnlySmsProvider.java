package io.kelta.worker.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development/testing SMS provider that logs messages instead of sending.
 *
 * <p>This is the opinionated open source default. For production, register
 * a custom {@link SmsProvider} bean (e.g., Twilio, Vonage) and set
 * {@code kelta.sms.provider} to disable this fallback.
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "kelta.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogOnlySmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LogOnlySmsProvider.class);

    @Override
    public void send(SmsMessage message) {
        log.info("SMS [to={}]: {}", message.to(), message.body());
    }
}
