package io.kelta.runtime.messaging.nats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.time.Duration;

/**
 * Manages the NATS connection lifecycle including reconnection handling.
 *
 * @since 1.0.0
 */
public class NatsConnectionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NatsConnectionManager.class);

    private final Connection connection;

    public NatsConnectionManager(NatsProperties properties) throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(properties.getUrl())
                .connectionName(properties.getConnectionName())
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .connectionListener((conn, type) ->
                        log.info("NATS connection event: {}", type))
                .errorListener(new NatsErrorListener())
                .build();

        this.connection = Nats.connect(options);
        log.info("Connected to NATS at {}", properties.getUrl());
    }

    public Connection getConnection() {
        return connection;
    }

    public JetStream jetStream() throws IOException {
        return connection.jetStream();
    }

    public JetStreamManagement jetStreamManagement() throws IOException {
        return connection.jetStreamManagement();
    }

    @Override
    public void destroy() {
        try {
            if (connection != null && connection.getStatus() != Connection.Status.CLOSED) {
                connection.drain(Duration.ofSeconds(10));
                log.info("NATS connection drained and closed");
            }
        } catch (Exception e) {
            log.warn("Error closing NATS connection: {}", e.getMessage());
        }
    }
}
