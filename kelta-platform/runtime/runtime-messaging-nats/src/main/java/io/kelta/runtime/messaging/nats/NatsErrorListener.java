package io.kelta.runtime.messaging.nats;

import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Error listener for NATS connection and subscription errors.
 *
 * @since 1.0.0
 */
class NatsErrorListener implements ErrorListener {

    private static final Logger log = LoggerFactory.getLogger(NatsErrorListener.class);

    @Override
    public void errorOccurred(io.nats.client.Connection conn, String error) {
        log.error("NATS error: {}", error);
    }

    @Override
    public void exceptionOccurred(io.nats.client.Connection conn, Exception exp) {
        log.error("NATS exception: {}", exp.getMessage(), exp);
    }

    @Override
    public void slowConsumerDetected(io.nats.client.Connection conn, Consumer consumer) {
        String name = (consumer instanceof JetStreamSubscription js) ? js.getConsumerName() : "unknown";
        log.warn("NATS slow consumer detected: {}", name);
    }
}
