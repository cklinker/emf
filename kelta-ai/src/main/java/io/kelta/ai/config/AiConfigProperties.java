package io.kelta.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kelta.ai")
public record AiConfigProperties(
        AnthropicProperties anthropic,
        String workerServiceUrl,
        long sseTimeoutMs,
        RateLimitProperties rateLimit
) {
    public record AnthropicProperties(
            String apiKey,
            String defaultModel,
            int defaultMaxTokens,
            double defaultTemperature
    ) {}

    /**
     * Per-tenant request-rate cap applied before invoking Anthropic.
     * Prevents a single tenant from exhausting the upstream model rate budget
     * shared across all tenants.
     */
    public record RateLimitProperties(
            boolean enabled,
            int requestsPerMinute
    ) {
        public RateLimitProperties {
            if (requestsPerMinute <= 0) {
                requestsPerMinute = 60;
            }
        }
    }
}
