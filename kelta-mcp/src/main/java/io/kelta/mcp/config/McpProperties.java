package io.kelta.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kelta.mcp")
public record McpProperties(
        String gatewayUrl,
        int sessionTtlMinutes,
        int toolTimeoutMs
) {
    public McpProperties {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            gatewayUrl = "http://emf-gateway:80";
        }
        if (sessionTtlMinutes <= 0) {
            sessionTtlMinutes = 30;
        }
        if (toolTimeoutMs <= 0) {
            toolTimeoutMs = 60_000;
        }
    }
}
