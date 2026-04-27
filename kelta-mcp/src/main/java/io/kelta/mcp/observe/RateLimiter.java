package io.kelta.mcp.observe;

import io.kelta.mcp.config.McpProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory token-bucket rate limiter, keyed by PAT.
 *
 * <p>Each user (identified by their PAT — never logged) gets a bucket
 * with {@code bucketCapacity} tokens that refills at
 * {@code refillPerSecond}. Each tool call consumes one token; if the
 * bucket is empty the call is rejected with a rate-limit error result.
 *
 * <p>Buckets are created lazily on first use. They are not evicted —
 * a single bucket is ~64 bytes, and the worst-case footprint is one
 * per active PAT, which is bounded by user count.
 */
@Component
public class RateLimiter {

    private final int capacity;
    private final double refillPerSecond;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(McpProperties properties) {
        this.capacity = properties.rateLimit().bucketCapacity();
        this.refillPerSecond = properties.rateLimit().refillPerSecond();
    }

    /**
     * Try to consume one token from the bucket for {@code key}.
     * @return true if a token was consumed; false if the bucket was empty.
     */
    public boolean tryAcquire(String key) {
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity, refillPerSecond));
        return b.tryAcquire();
    }

    public int activeBucketCount() {
        return buckets.size();
    }

    private static final class Bucket {
        private final int capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
