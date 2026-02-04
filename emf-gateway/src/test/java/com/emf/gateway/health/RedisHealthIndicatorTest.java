package com.emf.gateway.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisHealthIndicator.
 * 
 * Tests verify that the health indicator correctly reports Redis connectivity status.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisHealthIndicator Tests")
class RedisHealthIndicatorTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    private RedisHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new RedisHealthIndicator(redisTemplate);
    }
    
    @Test
    @DisplayName("Should report UP when Redis is reachable")
    void shouldReportUpWhenRedisIsReachable() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("connection", "active");
        
        verify(redisTemplate).hasKey(anyString());
    }
    
    @Test
    @DisplayName("Should report UP when Redis key exists")
    void shouldReportUpWhenRedisKeyExists() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("connection", "active");
    }
    
    @Test
    @DisplayName("Should report DOWN when Redis returns null")
    void shouldReportDownWhenRedisReturnsNull() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.empty());
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "degraded");
        assertThat(health.getDetails()).containsEntry("reason", "Null response from Redis");
    }
    
    @Test
    @DisplayName("Should report DOWN when Redis connection fails")
    void shouldReportDownWhenRedisConnectionFails() {
        // Given
        when(redisTemplate.hasKey(anyString()))
            .thenThrow(new RuntimeException("Connection refused"));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("error", "RuntimeException");
        assertThat(health.getDetails()).containsEntry("message", "Connection refused");
    }
    
    @Test
    @DisplayName("Should report DOWN when operation times out")
    void shouldReportDownWhenOperationTimesOut() {
        // Given
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.never()); // Never completes, will timeout
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    @DisplayName("Should report DOWN when operation throws exception")
    void shouldReportDownWhenOperationThrowsException() {
        // Given
        when(redisTemplate.hasKey(anyString()))
            .thenReturn(Mono.error(new RuntimeException("Operation failed")));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "failed");
        assertThat(health.getDetails()).containsEntry("error", "RuntimeException");
        assertThat(health.getDetails()).containsEntry("message", "Operation failed");
    }
}
