package com.emf.gateway.jsonapi;

import com.emf.gateway.route.RouteRegistry;
import com.emf.jsonapi.JsonApiParser;
import com.emf.jsonapi.Relationship;
import com.emf.jsonapi.ResourceIdentifier;
import com.emf.jsonapi.ResourceObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IncludeResolver.
 * Tests Redis integration for resolving JSON:API included resources.
 */
@ExtendWith(MockitoExtension.class)
class IncludeResolverTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    private RouteRegistry routeRegistry;
    private WebClient.Builder webClientBuilder;
    private JsonApiParser jsonApiParser;

    private IncludeResolver includeResolver;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        routeRegistry = mock(RouteRegistry.class);
        webClientBuilder = mock(WebClient.Builder.class);
        jsonApiParser = mock(JsonApiParser.class);
        includeResolver = new IncludeResolver(redisTemplate, routeRegistry, webClientBuilder, jsonApiParser);
    }

    @Test
    void resolveIncludes_withNoIncludeParams_shouldReturnEmptyList() {
        // Given
        List<String> includeParams = Collections.emptyList();
        List<ResourceObject> primaryData = createPrimaryDataWithRelationships();

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void resolveIncludes_withNullIncludeParams_shouldReturnEmptyList() {
        // Given
        List<ResourceObject> primaryData = createPrimaryDataWithRelationships();

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(null, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void resolveIncludes_withNoPrimaryData_shouldReturnEmptyList() {
        // Given
        List<String> includeParams = Arrays.asList("author", "comments");
        List<ResourceObject> primaryData = Collections.emptyList();

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void resolveIncludes_withSingleRelationship_shouldLookupInRedis() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        List<ResourceObject> primaryData = createPrimaryDataWithAuthorRelationship();

        String authorJson = createAuthorResourceJson();
        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.just(authorJson));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    assertThat(resources.get(0).getType()).isEqualTo("users");
                    assertThat(resources.get(0).getId()).isEqualTo("123");
                    assertThat(resources.get(0).getAttributes().get("name")).isEqualTo("John Doe");
                })
                .verifyComplete();

        verify(valueOperations).get("jsonapi:users:123");
    }

    @Test
    void resolveIncludes_withMultipleRelationships_shouldLookupAllInRedis() {
        // Given
        List<String> includeParams = Arrays.asList("author", "comments");
        List<ResourceObject> primaryData = createPrimaryDataWithMultipleRelationships();

        String authorJson = createAuthorResourceJson();
        String comment1Json = createCommentResourceJson("1", "Great post!");
        String comment2Json = createCommentResourceJson("2", "Thanks for sharing!");

        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.just(authorJson));
        when(valueOperations.get("jsonapi:comments:1"))
                .thenReturn(Mono.just(comment1Json));
        when(valueOperations.get("jsonapi:comments:2"))
                .thenReturn(Mono.just(comment2Json));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(3);
                    
                    // Verify author is included
                    assertThat(resources).anyMatch(r -> 
                        r.getType().equals("users") && r.getId().equals("123"));
                    
                    // Verify comments are included
                    assertThat(resources).anyMatch(r -> 
                        r.getType().equals("comments") && r.getId().equals("1"));
                    assertThat(resources).anyMatch(r -> 
                        r.getType().equals("comments") && r.getId().equals("2"));
                })
                .verifyComplete();

        verify(valueOperations).get("jsonapi:users:123");
        verify(valueOperations).get("jsonapi:comments:1");
        verify(valueOperations).get("jsonapi:comments:2");
    }

    @Test
    void resolveIncludes_withCacheMiss_shouldSkipMissingResource() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        List<ResourceObject> primaryData = createPrimaryDataWithAuthorRelationship();

        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.empty());

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(valueOperations).get("jsonapi:users:123");
    }

    @Test
    void resolveIncludes_withPartialCacheMiss_shouldReturnFoundResources() {
        // Given
        List<String> includeParams = Arrays.asList("author", "comments");
        List<ResourceObject> primaryData = createPrimaryDataWithMultipleRelationships();

        String authorJson = createAuthorResourceJson();
        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.just(authorJson));
        when(valueOperations.get("jsonapi:comments:1"))
                .thenReturn(Mono.empty()); // Cache miss
        when(valueOperations.get("jsonapi:comments:2"))
                .thenReturn(Mono.empty()); // Cache miss

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    assertThat(resources.get(0).getType()).isEqualTo("users");
                    assertThat(resources.get(0).getId()).isEqualTo("123");
                })
                .verifyComplete();
    }

    @Test
    void resolveIncludes_withRedisError_shouldContinueGracefully() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        List<ResourceObject> primaryData = createPrimaryDataWithAuthorRelationship();

        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(valueOperations).get("jsonapi:users:123");
    }

    @Test
    void resolveIncludes_withInvalidJson_shouldSkipResource() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        List<ResourceObject> primaryData = createPrimaryDataWithAuthorRelationship();

        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.just("invalid json {{{"));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();
    }

    @Test
    void resolveIncludes_withNonRequestedRelationship_shouldNotLookupInRedis() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        List<ResourceObject> primaryData = createPrimaryDataWithMultipleRelationships();

        String authorJson = createAuthorResourceJson();
        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.just(authorJson));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    assertThat(resources.get(0).getType()).isEqualTo("users");
                })
                .verifyComplete();

        // Should only lookup author, not comments
        verify(valueOperations).get("jsonapi:users:123");
        verify(valueOperations, never()).get("jsonapi:comments:1");
        verify(valueOperations, never()).get("jsonapi:comments:2");
    }

    @Test
    void resolveIncludes_withDuplicateRelationships_shouldDeduplicateLookups() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        
        // Create two posts with the same author
        ResourceObject post1 = new ResourceObject("posts", "1");
        post1.addRelationship("author", new Relationship(new ResourceIdentifier("users", "123")));
        
        ResourceObject post2 = new ResourceObject("posts", "2");
        post2.addRelationship("author", new Relationship(new ResourceIdentifier("users", "123")));
        
        List<ResourceObject> primaryData = Arrays.asList(post1, post2);

        String authorJson = createAuthorResourceJson();
        when(valueOperations.get("jsonapi:users:123"))
                .thenReturn(Mono.just(authorJson));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    assertThat(resources.get(0).getType()).isEqualTo("users");
                    assertThat(resources.get(0).getId()).isEqualTo("123");
                })
                .verifyComplete();

        // Should only lookup once due to deduplication
        verify(valueOperations, times(1)).get("jsonapi:users:123");
    }

    @Test
    void resolveIncludes_withNullRelationshipData_shouldSkipGracefully() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        
        ResourceObject post = new ResourceObject("posts", "1");
        post.addRelationship("author", new Relationship(null)); // Null relationship data
        
        List<ResourceObject> primaryData = Collections.singletonList(post);

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void resolveIncludes_withResourceIdentifierMissingTypeOrId_shouldSkip() {
        // Given
        List<String> includeParams = Collections.singletonList("author");
        
        ResourceObject post = new ResourceObject("posts", "1");
        post.addRelationship("author", new Relationship(new ResourceIdentifier(null, "123"))); // Missing type
        
        List<ResourceObject> primaryData = Collections.singletonList(post);

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> assertThat(resources).isEmpty())
                .verifyComplete();

        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void resolveIncludes_withCollectionTypeAsIncludeName_shouldMatchByTargetType() {
        // Given — include=categories should match a relationship keyed as "category_id"
        // whose data.type is "categories"
        List<String> includeParams = Collections.singletonList("categories");

        ResourceObject product = new ResourceObject("products", "1");
        product.addAttribute("name", "T-Shirt");
        product.addRelationship("category_id",
                new Relationship(new ResourceIdentifier("categories", "cat-1")));

        List<ResourceObject> primaryData = Collections.singletonList(product);

        String categoryJson = """
            {
                "type": "categories",
                "id": "cat-1",
                "attributes": {
                    "name": "Clothing"
                }
            }
            """;
        when(valueOperations.get("jsonapi:categories:cat-1"))
                .thenReturn(Mono.just(categoryJson));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    assertThat(resources.get(0).getType()).isEqualTo("categories");
                    assertThat(resources.get(0).getId()).isEqualTo("cat-1");
                })
                .verifyComplete();

        verify(valueOperations).get("jsonapi:categories:cat-1");
    }

    @Test
    void resolveIncludes_exactKeyMatchTakesPriorityOverTypeMatch() {
        // Given — include=author should match by key even if another relationship
        // has data.type "author"
        List<String> includeParams = Collections.singletonList("author");

        ResourceObject post = new ResourceObject("posts", "1");
        post.addRelationship("author",
                new Relationship(new ResourceIdentifier("users", "user-1")));
        // Another relationship whose type happens to be "author" (unlikely but tests priority)
        post.addRelationship("author_ref",
                new Relationship(new ResourceIdentifier("author", "author-1")));

        List<ResourceObject> primaryData = Collections.singletonList(post);

        String userJson = """
            {
                "type": "users",
                "id": "user-1",
                "attributes": {
                    "name": "John Doe",
                    "email": "john@example.com"
                }
            }
            """;
        when(valueOperations.get("jsonapi:users:user-1"))
                .thenReturn(Mono.just(userJson));

        // When
        Mono<List<ResourceObject>> result = includeResolver.resolveIncludes(includeParams, primaryData);

        // Then
        StepVerifier.create(result)
                .assertNext(resources -> {
                    assertThat(resources).hasSize(1);
                    // Should match by key "author" → users:user-1, NOT by type "author" → author:author-1
                    assertThat(resources.get(0).getType()).isEqualTo("users");
                    assertThat(resources.get(0).getId()).isEqualTo("user-1");
                })
                .verifyComplete();

        verify(valueOperations).get("jsonapi:users:user-1");
        verify(valueOperations, never()).get("jsonapi:author:author-1");
    }

    // Helper methods to create test data

    private List<ResourceObject> createPrimaryDataWithRelationships() {
        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "Test Post");
        post.addRelationship("author", new Relationship(new ResourceIdentifier("users", "123")));
        return Collections.singletonList(post);
    }

    private List<ResourceObject> createPrimaryDataWithAuthorRelationship() {
        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "Test Post");
        post.addRelationship("author", new Relationship(new ResourceIdentifier("users", "123")));
        return Collections.singletonList(post);
    }

    private List<ResourceObject> createPrimaryDataWithMultipleRelationships() {
        ResourceObject post = new ResourceObject("posts", "1");
        post.addAttribute("title", "Test Post");
        post.addRelationship("author", new Relationship(new ResourceIdentifier("users", "123")));
        
        List<ResourceIdentifier> commentIds = Arrays.asList(
            new ResourceIdentifier("comments", "1"),
            new ResourceIdentifier("comments", "2")
        );
        post.addRelationship("comments", new Relationship(commentIds));
        
        return Collections.singletonList(post);
    }

    private String createAuthorResourceJson() {
        return """
            {
                "type": "users",
                "id": "123",
                "attributes": {
                    "name": "John Doe",
                    "email": "john@example.com"
                }
            }
            """;
    }

    private String createCommentResourceJson(String id, String text) {
        return String.format("""
            {
                "type": "comments",
                "id": "%s",
                "attributes": {
                    "text": "%s"
                }
            }
            """, id, text);
    }
}
