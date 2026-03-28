package io.kelta.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kelta.ai")
public record AiConfigProperties(
        AnthropicProperties anthropic,
        String workerServiceUrl,
        long sseTimeoutMs
) {
    public record AnthropicProperties(
            String apiKey,
            String defaultModel,
            int defaultMaxTokens,
            double defaultTemperature
    ) {}
}
