package io.kelta.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kelta.auth")
public class AuthProperties {

    private String issuerUri;
    private String workerUrl;
    private String cookieDomain = "localhost";
    private String jwkSet;
    private String uiBaseUrl;
    private String corsAllowedOrigins;

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
}
