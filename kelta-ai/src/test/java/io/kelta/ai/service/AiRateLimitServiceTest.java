package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiRateLimitService")
class AiRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private AiRateLimitService service(boolean enabled, int rpm) {
        AiConfigProperties config = new AiConfigProperties(
                new AiConfigProperties.AnthropicProperties("k", "m", 4096, 0.7),
                "http://localhost", 30000L,
                new AiConfigProperties.RateLimitProperties(enabled, rpm));
        return new AiRateLimitService(redisTemplate, config);
    }

    @Nested
    @DisplayName("when disabled")
    class Disabled {
        @Test
        @DisplayName("allows without touching Redis")
        void allowsWithoutRedis() {
            AiRateLimitService s = service(false, 1);

            AiRateLimitService.Decision decision = s.check("tenant-1");

            assertThat(decision.allowed()).isTrue();
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("when enabled")
    class Enabled {
        @BeforeEach
        void setUp() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        @DisplayName("allows first request and sets TTL")
        void allowsFirstRequest() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            AiRateLimitService s = service(true, 10);

            AiRateLimitService.Decision decision = s.check("tenant-1");

            assertThat(decision.allowed()).isTrue();
            verify(redisTemplate).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("allows request at the limit (count == rpm)")
        void allowsRequestAtLimit() {
            when(valueOps.increment(anyString())).thenReturn(10L);
            AiRateLimitService s = service(true, 10);

            AiRateLimitService.Decision decision = s.check("tenant-1");

            assertThat(decision.allowed()).isTrue();
        }

        @Test
        @DisplayName("denies request over the limit with positive Retry-After")
        void deniesOverLimit() {
            when(valueOps.increment(anyString())).thenReturn(11L);
            AiRateLimitService s = service(true, 10);

            AiRateLimitService.Decision decision = s.check("tenant-1");

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.retryAfterSeconds()).isBetween(1L, 60L);
        }

        @Test
        @DisplayName("skips TTL set on subsequent requests in same window")
        void skipsTtlAfterFirst() {
            when(valueOps.increment(anyString())).thenReturn(5L);
            AiRateLimitService s = service(true, 10);

            s.check("tenant-1");

            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("fail-open when Redis throws")
        void failOpenOnRedisError() {
            when(valueOps.increment(anyString())).thenThrow(new RuntimeException("boom"));
            AiRateLimitService s = service(true, 10);

            AiRateLimitService.Decision decision = s.check("tenant-1");

            assertThat(decision.allowed()).isTrue();
        }

        @Test
        @DisplayName("fail-open when Redis returns null increment")
        void failOpenOnNullIncrement() {
            when(valueOps.increment(anyString())).thenReturn(null);
            AiRateLimitService s = service(true, 10);

            AiRateLimitService.Decision decision = s.check("tenant-1");

            assertThat(decision.allowed()).isTrue();
            verify(redisTemplate, times(0)).expire(anyString(), any(Duration.class));
        }
    }
}
