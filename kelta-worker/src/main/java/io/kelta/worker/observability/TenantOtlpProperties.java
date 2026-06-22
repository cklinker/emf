package io.kelta.worker.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-tenant OTLP export configuration (Rec 7), bound from
 * {@code kelta.observability.tenant-otlp.targets.<tenantId>.*}:
 *
 * <pre>
 * kelta:
 *   observability:
 *     tenant-otlp:
 *       targets:
 *         "5dc71a70-...":
 *           endpoint: https://otlp.acme.example/v1/traces
 *           enabled: true
 *           headers:
 *             authorization: "Bearer ..."
 * </pre>
 *
 * Operator-managed today; a later slice adds a DB-backed, admin-configurable source.
 */
@ConfigurationProperties("kelta.observability.tenant-otlp")
public class TenantOtlpProperties {

    private Map<String, Target> targets = new LinkedHashMap<>();

    public Map<String, Target> getTargets() {
        return targets;
    }

    public void setTargets(Map<String, Target> targets) {
        this.targets = targets != null ? targets : new LinkedHashMap<>();
    }

    public static class Target {
        private String endpoint;
        private Map<String, String> headers = new LinkedHashMap<>();
        /** Enabled by default — a configured target is active unless explicitly disabled. */
        private boolean enabled = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers != null ? headers : new LinkedHashMap<>();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
