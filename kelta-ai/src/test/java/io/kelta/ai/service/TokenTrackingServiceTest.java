package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.repository.AiConfigRepository;
import io.kelta.ai.repository.TokenUsageRepository;
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
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenTrackingService")
class TokenTrackingServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    @Mock
    private AiConfigRepository aiConfigRepository;

    private TokenTrackingService service;

    @BeforeEach
    void setUp() {
        AiConfigProperties config = new AiConfigProperties(
                new AiConfigProperties.AnthropicProperties("key", "model", 4096, 0.7),
                "http://localhost:8080", 30000L);
        service = new TokenTrackingService(redisTemplate, tokenUsageRepository, aiConfigRepository, config);
    }

    @Nested
    @DisplayName("recordUsage")
    class RecordUsage {

        @Test
        @DisplayName("increments Redis and persists to database")
        void incrementsRedisAndDb() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            service.recordUsage("tenant-1", 100, 50);

            String yearMonth = YearMonth.now().toString();
            String expectedKey = "ai-tokens-monthly:tenant-1:" + yearMonth;
            verify(valueOps).increment(expectedKey, 150L);
            verify(redisTemplate).expire(eq(expectedKey), eq(Duration.ofDays(45)));
            verify(tokenUsageRepository).incrementUsage("tenant-1", yearMonth, 100, 50);
        }

        @Test
        @DisplayName("still persists to DB when Redis fails")
        void persistsToDbWhenRedisFails() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            service.recordUsage("tenant-1", 100, 50);

            verify(tokenUsageRepository).incrementUsage(eq("tenant-1"), anyString(), eq(100), eq(50));
        }

        @Test
        @DisplayName("still increments Redis when DB fails")
        void incrementsRedisWhenDbFails() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            doThrow(new RuntimeException("DB down"))
                    .when(tokenUsageRepository).incrementUsage(anyString(), anyString(), anyInt(), anyInt());

            service.recordUsage("tenant-1", 100, 50);

            verify(valueOps).increment(anyString(), eq(150L));
        }
    }

    @Nested
    @DisplayName("isTokenLimitExceeded")
    class IsTokenLimitExceeded {

        @Test
        @DisplayName("returns true when usage exceeds limit")
        void returnsTrueWhenExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn("2000000");
            when(aiConfigRepository.getConfig("tenant-1", "aiTokensPerMonth"))
                    .thenReturn(Optional.of("1000000"));

            assertThat(service.isTokenLimitExceeded("tenant-1")).isTrue();
        }

        @Test
        @DisplayName("returns false when under limit")
        void returnsFalseWhenUnderLimit() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn("500000");
            when(aiConfigRepository.getConfig("tenant-1", "aiTokensPerMonth"))
                    .thenReturn(Optional.of("1000000"));

            assertThat(service.isTokenLimitExceeded("tenant-1")).isFalse();
        }

        @Test
        @DisplayName("returns false on error (fail-open)")
        void returnsFalseOnError() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            assertThat(service.isTokenLimitExceeded("tenant-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("isAiEnabled")
    class IsAiEnabled {

        @Test
        @DisplayName("returns config value when set")
        void returnsConfigValue() {
            when(aiConfigRepository.getConfig("tenant-1", "aiEnabled"))
                    .thenReturn(Optional.of("false"));

            assertThat(service.isAiEnabled("tenant-1")).isFalse();
        }

        @Test
        @DisplayName("defaults to true when not configured")
        void defaultsToTrue() {
            when(aiConfigRepository.getConfig("tenant-1", "aiEnabled"))
                    .thenReturn(Optional.empty());

            assertThat(service.isAiEnabled("tenant-1")).isTrue();
        }

        @Test
        @DisplayName("defaults to true on error")
        void defaultsToTrueOnError() {
            when(aiConfigRepository.getConfig("tenant-1", "aiEnabled"))
                    .thenThrow(new RuntimeException("DB error"));

            assertThat(service.isAiEnabled("tenant-1")).isTrue();
        }
    }

    @Nested
    @DisplayName("getCurrentMonthUsage")
    class GetCurrentMonthUsage {

        @Test
        @DisplayName("returns Redis value when available")
        void returnsRedisValue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn("75000");

            assertThat(service.getCurrentMonthUsage("tenant-1")).isEqualTo(75000L);
        }

        @Test
        @DisplayName("falls back to DB when Redis returns null")
        void fallsBackToDbWhenRedisNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null);
            when(tokenUsageRepository.getTotalTokens(eq("tenant-1"), anyString())).thenReturn(42000L);

            assertThat(service.getCurrentMonthUsage("tenant-1")).isEqualTo(42000L);
        }

        @Test
        @DisplayName("falls back to DB when Redis throws")
        void fallsBackToDbOnRedisError() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));
            when(tokenUsageRepository.getTotalTokens(eq("tenant-1"), anyString())).thenReturn(10000L);

            assertThat(service.getCurrentMonthUsage("tenant-1")).isEqualTo(10000L);
        }
    }

    @Nested
    @DisplayName("getTokenLimit")
    class GetTokenLimit {

        @Test
        @DisplayName("returns configured limit")
        void returnsConfiguredLimit() {
            when(aiConfigRepository.getConfig("tenant-1", "aiTokensPerMonth"))
                    .thenReturn(Optional.of("500000"));

            assertThat(service.getTokenLimit("tenant-1")).isEqualTo(500000L);
        }

        @Test
        @DisplayName("returns default when not configured")
        void returnsDefaultLimit() {
            when(aiConfigRepository.getConfig("tenant-1", "aiTokensPerMonth"))
                    .thenReturn(Optional.empty());

            assertThat(service.getTokenLimit("tenant-1")).isEqualTo(1_000_000L);
        }
    }
}
