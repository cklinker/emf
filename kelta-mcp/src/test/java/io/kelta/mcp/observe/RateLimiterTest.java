package io.kelta.mcp.observe;

import io.kelta.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void allowsUpToBucketCapacityImmediately() {
        RateLimiter limiter = new RateLimiter(new McpProperties(
                "http://gw", 30, 60_000,
                new McpProperties.RateLimit(5, 1.0)));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("klt_a")).as("call %d", i).isTrue();
        }
        assertThat(limiter.tryAcquire("klt_a")).isFalse();
    }

    @Test
    void bucketsAreIsolatedPerKey() {
        RateLimiter limiter = new RateLimiter(new McpProperties(
                "http://gw", 30, 60_000,
                new McpProperties.RateLimit(2, 0.0)));

        assertThat(limiter.tryAcquire("klt_a")).isTrue();
        assertThat(limiter.tryAcquire("klt_a")).isTrue();
        assertThat(limiter.tryAcquire("klt_a")).isFalse();
        // Different key gets its own fresh bucket.
        assertThat(limiter.tryAcquire("klt_b")).isTrue();
        assertThat(limiter.tryAcquire("klt_b")).isTrue();
        assertThat(limiter.tryAcquire("klt_b")).isFalse();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(new McpProperties(
                "http://gw", 30, 60_000,
                new McpProperties.RateLimit(2, 100.0))); // 100/sec → ~10ms per token

        assertThat(limiter.tryAcquire("klt_a")).isTrue();
        assertThat(limiter.tryAcquire("klt_a")).isTrue();
        assertThat(limiter.tryAcquire("klt_a")).isFalse();

        Thread.sleep(60); // > 50ms → at least 5 tokens refilled (capped at capacity 2)

        assertThat(limiter.tryAcquire("klt_a")).isTrue();
        assertThat(limiter.tryAcquire("klt_a")).isTrue();
    }

    @Test
    void activeBucketCountReflectsDistinctKeys() {
        RateLimiter limiter = new RateLimiter(new McpProperties(
                "http://gw", 30, 60_000,
                new McpProperties.RateLimit(1, 1.0)));

        limiter.tryAcquire("klt_a");
        limiter.tryAcquire("klt_b");
        limiter.tryAcquire("klt_a");
        assertThat(limiter.activeBucketCount()).isEqualTo(2);
    }
}
