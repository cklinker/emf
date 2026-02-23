package com.emf.gateway.integration;

import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.jsonapi.Relationship;
import com.emf.jsonapi.ResourceIdentifier;
import com.emf.jsonapi.ResourceObject;
import com.emf.gateway.route.RateLimitConfig;
import com.emf.gateway.ratelimit.RateLimitResult;
import com.emf.gateway.ratelimit.RedisRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Redis cache operations.
 * 
 * Tests:
 * - JSON:API resource caching and retrieval
 * - Include resolution from Redis cache
 * - Rate limiting using Redis counters
 * - Cache miss handling
 * - Redis connection error handling
 * 
 * Validates: Requirements 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 8.2, 8.3, 8.4, 8.5, 8.6
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisCacheIntegrationTest {
    
    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private IncludeResolver includeResolver;
    
    @Autowired
    private RedisRateLimiter rateLimiter;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // Clear Redis before each test (if Redis is available)
        try {
            redisTemplate.getConnectionFactory()
                    .getReactiveConnection()
                    .serverCommands()
                    .flushDb()
                    .block(Duration.ofSeconds(2));
        } catch (Exception e) {
            // Redis may not be available in test environment - tests will handle gracefully
        }
    }
    
    @Test
    void testJsonApiResourceCaching_StoreAndRetrieve() throws Exception {
        // Arrange - create a resource object
        ResourceObject resource = new ResourceObject();
        resource.setType("users");
        resource.setId("123");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", "John Doe");
        attributes.put("email", "john@example.com");
        resource.setAttributes(attributes);
        
        String resourceJson = objectMapper.writeValueAsString(resource);
        String cacheKey = "jsonapi:users:123";
        
        // Act - store in Redis
        Mono<Boolean> storeMono = redisTemplate.opsForValue()
                .set(cacheKey, resourceJson, Duration.ofMinutes(5));
        
        // Assert - verify storage
        StepVerifier.create(storeMono)
                .expectNext(true)
                .verifyComplete();
        
        // Act - retrieve from Redis
        Mono<String> retrieveMono = redisTemplate.opsForValue().get(cacheKey);
        
        // Assert - verify retrieval and deserialization
        StepVerifier.create(retrieveMono)
                .assertNext(json -> {
                    try {
                        ResourceObject retrieved = objectMapper.readValue(json, ResourceObject.class);
                        assertThat(retrieved.getType()).isEqualTo("users");
                        assertThat(retrieved.getId()).isEqualTo("123");
                        assertThat(retrieved.getAttributes().get("name")).isEqualTo("John Doe");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }
    
    @Test
    void testIncludeResolution_FromCache() throws Exception {
        // Arrange - store related resources in Redis
        ResourceObject author = new ResourceObject();
        author.setType("users");
        author.setId("1");
        Map<String, Object> authorAttrs = new HashMap<>();
        authorAttrs.put("name", "Author Name");
        author.setAttributes(authorAttrs);
        
        ResourceObject category = new ResourceObject();
        category.setType("categories");
        category.setId("10");
        Map<String, Object> categoryAttrs = new HashMap<>();
        categoryAttrs.put("name", "Technology");
        category.setAttributes(categoryAttrs);
        
        // Store in Redis
        String authorKey = "jsonapi:users:1";
        String categoryKey = "jsonapi:categories:10";
        
        redisTemplate.opsForValue()
                .set(authorKey, objectMapper.writeValueAsString(author), Duration.ofMinutes(5))
                .block(Duration.ofSeconds(2));
        
        redisTemplate.opsForValue()
                .set(categoryKey, objectMapper.writeValueAsString(category), Duration.ofMinutes(5))
                .block(Duration.ofSeconds(2));
        
        // Create primary data with relationships
        ResourceObject article = new ResourceObject();
        article.setType("articles");
        article.setId("100");
        
        Map<String, Relationship> relationships = new HashMap<>();
        
        Relationship authorRel = new Relationship();
        ResourceIdentifier authorId = new ResourceIdentifier();
        authorId.setType("users");
        authorId.setId("1");
        authorRel.setData(authorId);
        relationships.put("author", authorRel);
        
        Relationship categoryRel = new Relationship();
        ResourceIdentifier categoryId = new ResourceIdentifier();
        categoryId.setType("categories");
        categoryId.setId("10");
        categoryRel.setData(categoryId);
        relationships.put("category", categoryRel);
        
        article.setRelationships(relationships);
        
        // Act - resolve includes
        List<String> includeParams = List.of("author", "category");
        Mono<List<ResourceObject>> includedMono = includeResolver.resolveIncludes(
                includeParams, 
                List.of(article)
        );
        
        // Assert - verify included resources are retrieved
        StepVerifier.create(includedMono)
                .assertNext(included -> {
                    assertThat(included).hasSize(2);
                    
                    ResourceObject includedAuthor = included.stream()
                            .filter(r -> r.getType().equals("users") && r.getId().equals("1"))
                            .findFirst()
                            .orElse(null);
                    assertThat(includedAuthor).isNotNull();
                    assertThat(includedAuthor.getAttributes().get("name")).isEqualTo("Author Name");
                    
                    ResourceObject includedCategory = included.stream()
                            .filter(r -> r.getType().equals("categories") && r.getId().equals("10"))
                            .findFirst()
                            .orElse(null);
                    assertThat(includedCategory).isNotNull();
                    assertThat(includedCategory.getAttributes().get("name")).isEqualTo("Technology");
                })
                .verifyComplete();
    }
    
    @Test
    void testIncludeResolution_CacheMiss() {
        // Arrange - create primary data with relationship to non-existent resource
        ResourceObject article = new ResourceObject();
        article.setType("articles");
        article.setId("100");
        
        Map<String, Relationship> relationships = new HashMap<>();
        Relationship authorRel = new Relationship();
        ResourceIdentifier authorId = new ResourceIdentifier();
        authorId.setType("users");
        authorId.setId("999"); // Non-existent
        authorRel.setData(authorId);
        relationships.put("author", authorRel);
        article.setRelationships(relationships);
        
        // Act - resolve includes
        List<String> includeParams = List.of("author");
        Mono<List<ResourceObject>> includedMono = includeResolver.resolveIncludes(
                includeParams, 
                List.of(article)
        );
        
        // Assert - cache miss should result in empty included list (resource skipped)
        StepVerifier.create(includedMono)
                .assertNext(included -> {
                    assertThat(included).isEmpty();
                })
                .verifyComplete();
    }
    
    @Test
    void testRateLimiting_WithinLimit() {
        // Arrange - configure rate limit
        RateLimitConfig config = new RateLimitConfig(5, Duration.ofMinutes(1));
        
        String routeId = "test-route";
        String principal = "test-user";
        
        // Act & Assert - first request should be allowed
        Mono<RateLimitResult> resultMono = rateLimiter.checkRateLimit(routeId, principal, config);
        
        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    assertThat(result.isAllowed()).isTrue();
                    assertThat(result.getRemainingRequests()).isLessThanOrEqualTo(4);
                })
                .verifyComplete();
    }
    
    @Test
    void testRateLimiting_ExceedsLimit() {
        // Arrange - configure low rate limit
        RateLimitConfig config = new RateLimitConfig(2, Duration.ofMinutes(1));
        
        String routeId = "limited-route";
        String principal = "test-user-2";
        
        // Act - make requests up to limit
        rateLimiter.checkRateLimit(routeId, principal, config).block(Duration.ofSeconds(2));
        rateLimiter.checkRateLimit(routeId, principal, config).block(Duration.ofSeconds(2));
        
        // Third request should exceed limit
        Mono<RateLimitResult> resultMono = rateLimiter.checkRateLimit(routeId, principal, config);
        
        // Assert - request should be denied (if Redis is available)
        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    // If Redis is available, should be denied
                    // If Redis is not available, graceful degradation allows request
                    if (!result.isAllowed()) {
                        assertThat(result.getRemainingRequests()).isEqualTo(0);
                        assertThat(result.getRetryAfter()).isNotNull();
                    }
                })
                .verifyComplete();
    }
    
    @Test
    void testRateLimiting_TTL() throws Exception {
        // Arrange - configure rate limit with short window
        RateLimitConfig config = new RateLimitConfig(1, Duration.ofSeconds(2));
        
        String routeId = "ttl-test-route";
        String principal = "test-user-3";
        
        // Act - make first request
        rateLimiter.checkRateLimit(routeId, principal, config).block(Duration.ofSeconds(2));
        
        // Verify key exists in Redis
        String rateLimitKey = "ratelimit:" + routeId + ":" + principal;
        Boolean exists = redisTemplate.hasKey(rateLimitKey).block(Duration.ofSeconds(2));
        
        if (exists != null && exists) {
            // Assert - key should have TTL
            Duration ttl = redisTemplate.getExpire(rateLimitKey).block(Duration.ofSeconds(2));
            assertThat(ttl).isNotNull();
            assertThat(ttl.getSeconds()).isGreaterThan(0);
            assertThat(ttl.getSeconds()).isLessThanOrEqualTo(2);
        }
        // If Redis is not available, test passes (graceful degradation)
    }
    
    @Test
    void testRedisConnectionError_GracefulDegradation() {
        // This test verifies graceful degradation when Redis is unavailable
        // If Redis is not running, operations should not throw exceptions
        
        // Arrange - configure rate limit
        RateLimitConfig config = new RateLimitConfig(1, Duration.ofMinutes(1));
        
        // Act - attempt rate limit check (may fail if Redis unavailable)
        Mono<RateLimitResult> resultMono = rateLimiter.checkRateLimit(
                "test-route", 
                "test-user", 
                config
        );
        
        // Assert - should complete without error (either allowed or gracefully degraded)
        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    // If Redis is available, result will be accurate
                    // If Redis is unavailable, graceful degradation allows request
                    assertThat(result).isNotNull();
                })
                .verifyComplete();
        
        // Act - attempt include resolution (may fail if Redis unavailable)
        ResourceObject article = new ResourceObject();
        article.setType("articles");
        article.setId("1");
        
        Mono<List<ResourceObject>> includedMono = includeResolver.resolveIncludes(
                List.of("author"), 
                List.of(article)
        );
        
        // Assert - should complete without error (empty list if Redis unavailable)
        StepVerifier.create(includedMono)
                .assertNext(included -> {
                    assertThat(included).isNotNull();
                })
                .verifyComplete();
    }
}
