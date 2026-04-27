package io.kelta.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kelta.mcp")
public record McpProperties(
        String gatewayUrl,
        int sessionTtlMinutes,
        int toolTimeoutMs,
        RateLimit rateLimit
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
        if (rateLimit == null) {
            rateLimit = new RateLimit(60, 30.0);
        }
    }

    /**
     * Per-PAT token bucket.
     *
     * @param bucketCapacity   max tokens (defines burst tolerance)
     * @param refillPerSecond  steady-state refill rate (tokens / second)
     */
    public record RateLimit(int bucketCapacity, double refillPerSecond) {
        public RateLimit {
            if (bucketCapacity <= 0) bucketCapacity = 60;
            if (refillPerSecond <= 0) refillPerSecond = 30.0;
        }
    }
}
