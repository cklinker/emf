package io.kelta.gateway.ratelimit;

import io.kelta.gateway.route.RateLimitConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private RedisRateLimiter newLimiter() {
        return new RedisRateLimiter(redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void givenWindowCount(long count) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
            .thenReturn(Flux.just(count));
    }

    @Test
    void testFirstRequestInWindow() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(1L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 9)
            .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testWindowKeyAndTtlPassedToScript() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(5));
        givenWindowCount(1L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactly("ratelimit:users-collection:user@example.com");
        assertThat(argsCaptor.getValue()).containsExactly("300");
    }

    @Test
    void testSubsequentRequestDoesNotRefreshTtl() {
        // The window TTL is managed inside the atomic script (set only when the
        // window is new or its TTL was lost). Refreshing it per request would
        // turn the fixed window into an idle-expiry that never resets under
        // continuous traffic — the root cause of a tenant-wide 429 lockout.
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(5L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 5)
            .verifyComplete();

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void testRateLimitExceededUsesRemainingWindowTtlAsRetryAfter() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(5));
        givenWindowCount(11L);
        when(redisTemplate.getExpire("ratelimit:users-collection:user@example.com"))
            .thenReturn(Mono.just(Duration.ofSeconds(37)));

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result ->
                !result.isAllowed() &&
                result.getRemainingRequests() == 0 &&
                result.getRetryAfter().equals(Duration.ofSeconds(37)))
            .verifyComplete();
    }

    @Test
    void testRateLimitExceededFallsBackToWindowDurationWhenTtlMissing() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(11L);
        when(redisTemplate.getExpire("ratelimit:users-collection:user@example.com"))
            .thenReturn(Mono.empty());

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result ->
                !result.isAllowed() && result.getRetryAfter().equals(Duration.ofMinutes(1)))
            .verifyComplete();
    }

    @Test
    void testRateLimitExceededFallsBackToWindowDurationWhenTtlNonPositive() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(11L);
        when(redisTemplate.getExpire("ratelimit:users-collection:user@example.com"))
            .thenReturn(Mono.just(Duration.ofSeconds(-1)));

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result ->
                !result.isAllowed() && result.getRetryAfter().equals(Duration.ofMinutes(1)))
            .verifyComplete();
    }

    @Test
    void testExactlyAtLimit() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(10L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 0)
            .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRedisUnavailable() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
            .thenReturn(Flux.error(new RuntimeException("Redis connection failed")));

        // Should allow request and not throw exception
        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(result -> result.isAllowed() && result.getRemainingRequests() == 10)
            .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDifferentPrincipalsHaveSeparateKeys() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(1L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user1@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();
        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user2@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), keysCaptor.capture(), anyList());
        assertThat(keysCaptor.getAllValues()).containsExactly(
            List.of("ratelimit:users-collection:user1@example.com"),
            List.of("ratelimit:users-collection:user2@example.com"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDifferentRoutesHaveSeparateKeys() {
        RateLimitConfig config = new RateLimitConfig(10, Duration.ofMinutes(1));
        givenWindowCount(1L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();
        StepVerifier.create(newLimiter().checkRateLimit("posts-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), keysCaptor.capture(), anyList());
        assertThat(keysCaptor.getAllValues()).containsExactly(
            List.of("ratelimit:users-collection:user@example.com"),
            List.of("ratelimit:posts-collection:user@example.com"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCustomWindowDurationPassedAsSeconds() {
        RateLimitConfig config = new RateLimitConfig(100, Duration.ofSeconds(30));
        givenWindowCount(1L);

        StepVerifier.create(newLimiter().checkRateLimit("users-collection", "user@example.com", config))
            .expectNextMatches(RateLimitResult::isAllowed)
            .verifyComplete();

        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).containsExactly("30");
    }
}
