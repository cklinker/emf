package io.kelta.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "kelta.auth")
public class AuthProperties {

    private String issuerUri;
    private String workerUrl;
    private String cookieDomain = "localhost";
    private String jwkSet;
    private String uiBaseUrl;
    private String corsAllowedOrigins;
    private String supersetClientId;
    private String supersetClientSecret;
    private String supersetRedirectUri;

    /**
     * Internal service OAuth2 clients registered for the {@code client_credentials}
     * grant. Keyed by client_id (e.g. {@code gateway-internal},
     * {@code auth-internal}, {@code ai-internal}); each entry supplies the client
     * secret used by that caller. Clients whose secret is blank are skipped at
     * startup so the registrar stays opt-in per environment.
     */
    private Map<String, InternalClient> internalClients = new LinkedHashMap<>();

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getWorkerUrl() { return workerUrl; }
    public void setWorkerUrl(String workerUrl) { this.workerUrl = workerUrl; }

    public String getCookieDomain() { return cookieDomain; }
    public void setCookieDomain(String cookieDomain) { this.cookieDomain = cookieDomain; }

    public String getJwkSet() { return jwkSet; }
    public void setJwkSet(String jwkSet) { this.jwkSet = jwkSet; }

    public String getUiBaseUrl() { return uiBaseUrl; }
    public void setUiBaseUrl(String uiBaseUrl) { this.uiBaseUrl = uiBaseUrl; }

    public String getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(String corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }

    public String getSupersetClientId() { return supersetClientId; }
    public void setSupersetClientId(String supersetClientId) { this.supersetClientId = supersetClientId; }

    public String getSupersetClientSecret() { return supersetClientSecret; }
    public void setSupersetClientSecret(String supersetClientSecret) { this.supersetClientSecret = supersetClientSecret; }

    public String getSupersetRedirectUri() { return supersetRedirectUri; }
    public void setSupersetRedirectUri(String supersetRedirectUri) { this.supersetRedirectUri = supersetRedirectUri; }

    public Map<String, InternalClient> getInternalClients() { return internalClients; }
    public void setInternalClients(Map<String, InternalClient> internalClients) {
        this.internalClients = (internalClients != null) ? internalClients : new LinkedHashMap<>();
    }

    /**
     * Credential-carrying record for a single internal service caller. A blank
     * secret disables registration for that client, so the default (empty map)
     * leaves the behaviour unchanged for environments that haven't opted in yet.
     */
    public static class InternalClient {
        private String secret;
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }
}
