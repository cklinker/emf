package io.kelta.worker.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
// @Primary: WebPushProvider registers as a SECOND PushProvider bean whenever VAPID
// keys are set, which would make the single-typed injection in DefaultPushService
// ambiguous and fail worker startup. The three kelta.push.provider transports are
// mutually exclusive, so exactly one is ever primary.
@Primary
@ConditionalOnProperty(name = "kelta.push.provider", havingValue = "log", matchIfMissing = true)
public class LogOnlyPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(LogOnlyPushProvider.class);

    @Override
    public void send(PushMessage message, TenantPushSettings tenantSettings) {
        log.info("PUSH [{}] to={}: {} — {}", message.platform(), message.deviceToken(),
                message.title(), message.body());
    }
}
