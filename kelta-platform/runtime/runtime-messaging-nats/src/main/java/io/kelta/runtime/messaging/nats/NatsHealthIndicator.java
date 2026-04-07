package io.kelta.runtime.messaging.nats;

import io.nats.client.Connection;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Spring Boot Actuator health indicator for the NATS connection.
 *
 * @since 1.0.0
 */
public class NatsHealthIndicator implements HealthIndicator {

    private final NatsConnectionManager connectionManager;

    public NatsHealthIndicator(NatsConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Health health() {
        Connection conn = connectionManager.getConnection();
        Connection.Status status = conn.getStatus();
        return switch (status) {
            case CONNECTED -> Health.up()
                    .withDetail("url", conn.getConnectedUrl())
                    .withDetail("status", status.name())
                    .build();
            case RECONNECTING -> Health.outOfService()
                    .withDetail("status", status.name())
                    .build();
            default -> Health.down()
                    .withDetail("status", status.name())
                    .build();
        };
    }
}
