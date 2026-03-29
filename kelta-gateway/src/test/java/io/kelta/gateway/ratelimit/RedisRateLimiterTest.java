package io.kelta.gateway.ratelimit;

import io.kelta.gateway.route.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Every request calls expire to ensure TTL is always set
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }

    @Test
    void testFirstRequestInWindow() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String expectedKey = "ratelimit:users-collection:user@example.com";

        when(valueOps.increment(expectedKey)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 9)
            .verifyComplete();

        verify(valueOps).increment(expectedKey);
        verify(redisTemplate).expire(expectedKey, Duration.ofMinutes(1));
    }

    @Test
    void testSubsequentRequestInWindow() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String expectedKey = "ratelimit:users-collection:user@example.com";

        when(valueOps.increment(expectedKey)).thenReturn(Mono.just(5L));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 5)
            .verifyComplete();

        verify(valueOps).increment(expectedKey);
        // TTL is always refreshed
        verify(redisTemplate).expire(expectedKey, Duration.ofMinutes(1));
    }

    @Test
    void testRateLimitExceeded() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String expectedKey = "ratelimit:users-collection:user@example.com";

        when(valueOps.increment(expectedKey)).thenReturn(Mono.just(11L));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result ->
                !result.isAllowed() &&
                result.getRemainingRequests() == 0 &&
                result.getRetryAfter().equals(Duration.ofMinutes(1)))
            .verifyComplete();

        verify(valueOps).increment(expectedKey);
    }

    @Test
    void testExactlyAtLimit() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String expectedKey = "ratelimit:users-collection:user@example.com";

        when(valueOps.increment(expectedKey)).thenReturn(Mono.just(10L));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 0)
            .verifyComplete();
    }

    @Test
    void testRedisUnavailable() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String expectedKey = "ratelimit:users-collection:user@example.com";

        when(valueOps.increment(expectedKey))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When & Then - should allow request and not throw exception
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 10)
            .verifyComplete();
    }

    @Test
    void testDifferentPrincipalsHaveSeparateLimits() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String key1 = "ratelimit:users-collection:user1@example.com";
        String key2 = "ratelimit:users-collection:user2@example.com";

        when(valueOps.increment(key1)).thenReturn(Mono.just(1L));
        when(valueOps.increment(key2)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user1@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user2@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        verify(valueOps).increment(key1);
        verify(valueOps).increment(key2);
    }

    @Test
    void testDifferentRoutesHaveSeparateLimits() {
        // Given
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        String key1 = "ratelimit:users-collection:user@example.com";
        String key2 = "ratelimit:posts-collection:user@example.com";

        when(valueOps.increment(key1)).thenReturn(Mono.just(1L));
        when(valueOps.increment(key2)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        StepVerifier.create(rateLimiter.checkRateLimit("posts-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        verify(valueOps).increment(key1);
        verify(valueOps).increment(key2);
    }

    @Test
    void testCustomWindowDuration() {
        // Given
        RateLimitConfig config = new RateLimitConfig(100, Duration.ofSeconds(30));
        String expectedKey = "ratelimit:users-collection:user@example.com";

        when(valueOps.increment(expectedKey)).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(expectedKey, Duration.ofSeconds(30))).thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(rateLimiter.checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        verify(redisTemplate).expire(expectedKey, Duration.ofSeconds(30));
    }
}
