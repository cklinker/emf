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
}
