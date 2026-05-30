package io.kelta.runtime.messaging.nats;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the NATS connection.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "kelta.nats")
public class NatsProperties {

    private String url = "nats://localhost:4222";
    private String connectionName = "kelta";

    /**
     * Maximum number of in-flight {@code publishAsync} calls allowed concurrently.
     * Excess publishes are dropped (logged + counted) rather than queuing
     * unbounded inside the client. Prevents memory blowup under broker
     * backpressure or network partitions.
     */
    private int maxInflightPublishes = 1000;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public int getMaxInflightPublishes() {
        return maxInflightPublishes;
    }

    public void setMaxInflightPublishes(int maxInflightPublishes) {
        this.maxInflightPublishes = maxInflightPublishes;
    }
}
