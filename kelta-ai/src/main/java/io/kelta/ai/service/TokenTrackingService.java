package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.repository.AiConfigRepository;
import io.kelta.ai.repository.TokenUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.YearMonth;

@Service
public class TokenTrackingService {

    private static final Logger log = LoggerFactory.getLogger(TokenTrackingService.class);
    private static final String TOKEN_KEY_PREFIX = "ai-tokens-monthly:";
    private static final String AI_ENABLED_KEY = "aiEnabled";
    private static final String AI_TOKENS_PER_MONTH_KEY = "aiTokensPerMonth";
    private static final long DEFAULT_TOKENS_PER_MONTH = 1_000_000L;
    private static final Duration REDIS_TTL = Duration.ofDays(45);

    private final StringRedisTemplate redisTemplate;
    private final TokenUsageRepository tokenUsageRepository;
    private final AiConfigRepository aiConfigRepository;
    private final AiConfigProperties config;

    public TokenTrackingService(StringRedisTemplate redisTemplate, TokenUsageRepository tokenUsageRepository,
                                 AiConfigRepository aiConfigRepository, AiConfigProperties config) {
        this.redisTemplate = redisTemplate;
        this.tokenUsageRepository = tokenUsageRepository;
        this.aiConfigRepository = aiConfigRepository;
        this.config = config;
    }

    public void recordUsage(long tenantId, int inputTokens, int outputTokens) {
        String yearMonth = YearMonth.now().toString();
        int totalTokens = inputTokens + outputTokens;

        // Increment Redis counter
        try {
            String key = TOKEN_KEY_PREFIX + tenantId + ":" + yearMonth;
            redisTemplate.opsForValue().increment(key, totalTokens);
            redisTemplate.expire(key, REDIS_TTL);
        } catch (Exception e) {
            log.warn("Failed to increment Redis token counter for tenant {}: {}", tenantId, e.getMessage());
        }

        // Persist to database
        try {
            tokenUsageRepository.incrementUsage(tenantId, yearMonth, inputTokens, outputTokens);
        } catch (Exception e) {
            log.error("Failed to persist token usage for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    public boolean isTokenLimitExceeded(long tenantId) {
        try {
            long used = getCurrentMonthUsage(tenantId);
            long limit = getTokenLimit(tenantId);
            return used >= limit;
        } catch (Exception e) {
            log.warn("Failed to check token limit for tenant {}, allowing request: {}", tenantId, e.getMessage());
            return false;
        }
    }

    public boolean isAiEnabled(long tenantId) {
        try {
            return aiConfigRepository.getConfig(tenantId, AI_ENABLED_KEY)
                    .map(Boolean::parseBoolean)
                    .orElse(true);
        } catch (Exception e) {
            log.warn("Failed to check AI enabled status for tenant {}, defaulting to enabled: {}", tenantId, e.getMessage());
            return true;
        }
    }

    public long getCurrentMonthUsage(long tenantId) {
        String yearMonth = YearMonth.now().toString();
        try {
            String key = TOKEN_KEY_PREFIX + tenantId + ":" + yearMonth;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("Failed to read Redis token counter, falling back to DB: {}", e.getMessage());
        }
        // Fallback to database
        return tokenUsageRepository.getTotalTokens(tenantId, yearMonth);
    }

    public long getTokenLimit(long tenantId) {
        return aiConfigRepository.getConfig(tenantId, AI_TOKENS_PER_MONTH_KEY)
                .map(Long::parseLong)
                .orElse(DEFAULT_TOKENS_PER_MONTH);
    }
}
