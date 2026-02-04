package com.emf.controlplane.event;

import com.emf.controlplane.entity.OidcProvider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Payload for OIDC configuration changed events.
 * Contains the full list of active OIDC providers.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>10.4: Publish OIDC configuration change events to Kafka</li>
 * </ul>
 */
public class OidcChangedPayload {

    private List<OidcProviderPayload> providers;
    private Instant timestamp;

    /**
     * Default constructor for deserialization.
     */
    public OidcChangedPayload() {
    }

    /**
     * Creates a payload with the full OIDC configuration.
     *
     * @param providers The list of active OIDC providers
     * @return The payload with full OIDC configuration
     */
    public static OidcChangedPayload create(List<OidcProvider> providers) {
        OidcChangedPayload payload = new OidcChangedPayload();
        payload.setTimestamp(Instant.now());

        if (providers != null) {
            payload.setProviders(providers.stream()
                    .map(OidcProviderPayload::fromEntity)
                    .collect(Collectors.toList()));
        }

        return payload;
    }

    public List<OidcProviderPayload> getProviders() {
        return providers;
    }

    public void setProviders(List<OidcProviderPayload> providers) {
        this.providers = providers;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OidcChangedPayload that = (OidcChangedPayload) o;
        return Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }

    @Override
    public String toString() {
        return "OidcChangedPayload{" +
                "providers=" + (providers != null ? providers.size() : 0) +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * Nested class for OIDC provider data in the payload.
     */
    public static class OidcProviderPayload {
        private String id;
        private String name;
        private String issuer;
        private String jwksUri;
        private String clientId;
        private String audience;
        private boolean active;

        public OidcProviderPayload() {
        }

        public static OidcProviderPayload fromEntity(OidcProvider provider) {
            OidcProviderPayload payload = new OidcProviderPayload();
            payload.setId(provider.getId());
            payload.setName(provider.getName());
            payload.setIssuer(provider.getIssuer());
            payload.setJwksUri(provider.getJwksUri());
            payload.setClientId(provider.getClientId());
            payload.setAudience(provider.getAudience());
            payload.setActive(provider.isActive());
            return payload;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
