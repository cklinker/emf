package com.emf.gateway.authz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthzConfigCache.
 */
class AuthzConfigCacheTest {
    
    private AuthzConfigCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new AuthzConfigCache();
    }
    
    @Test
    void shouldReturnEmptyWhenConfigNotFound() {
        Optional<AuthzConfig> result = cache.getConfig("non-existent");
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void shouldReturnEmptyWhenCollectionIdIsNull() {
        Optional<AuthzConfig> result = cache.getConfig(null);
        
        assertFalse(result.isPresent());
    }
    
    @Test
    void shouldStoreAndRetrieveConfig() {
        AuthzConfig config = new AuthzConfig(
            "users-collection",
            Collections.emptyList(),
            Collections.emptyList()
        );
        
        cache.updateConfig("users-collection", config);
        Optional<AuthzConfig> result = cache.getConfig("users-collection");
        
        assertTrue(result.isPresent());
        assertEquals(config, result.get());
    }
    
    @Test
    void shouldUpdateExistingConfig() {
        AuthzConfig config1 = new AuthzConfig(
            "users-collection",
            Collections.emptyList(),
            Collections.emptyList()
        );
        AuthzConfig config2 = new AuthzConfig(
            "users-collection",
            Arrays.asList(new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN"))),
            Collections.emptyList()
        );
        
        cache.updateConfig("users-collection", config1);
        cache.updateConfig("users-collection", config2);
        
        Optional<AuthzConfig> result = cache.getConfig("users-collection");
        
        assertTrue(result.isPresent());
        assertEquals(config2, result.get());
        assertEquals(1, result.get().getRoutePolicies().size());
    }
    
    @Test
    void shouldNotUpdateWhenCollectionIdIsNull() {
        AuthzConfig config = new AuthzConfig(
            "users-collection",
            Collections.emptyList(),
            Collections.emptyList()
        );
        
        cache.updateConfig(null, config);
        
        assertEquals(0, cache.size());
    }
    
    @Test
    void shouldNotUpdateWhenConfigIsNull() {
        cache.updateConfig("users-collection", null);
        
        assertEquals(0, cache.size());
    }
    
    @Test
    void shouldRemoveConfig() {
        AuthzConfig config = new AuthzConfig(
            "users-collection",
            Collections.emptyList(),
            Collections.emptyList()
        );
        
        cache.updateConfig("users-collection", config);
        assertEquals(1, cache.size());
        
        cache.removeConfig("users-collection");
        
        assertEquals(0, cache.size());
        assertFalse(cache.getConfig("users-collection").isPresent());
    }
    
    @Test
    void shouldHandleRemoveWhenConfigNotPresent() {
        cache.removeConfig("non-existent");
        
        assertEquals(0, cache.size());
    }
    
    @Test
    void shouldHandleRemoveWhenCollectionIdIsNull() {
        cache.removeConfig(null);
        
        assertEquals(0, cache.size());
    }
    
    @Test
    void shouldClearAllConfigs() {
        cache.updateConfig("users-collection", new AuthzConfig(
            "users-collection", Collections.emptyList(), Collections.emptyList()
        ));
        cache.updateConfig("posts-collection", new AuthzConfig(
            "posts-collection", Collections.emptyList(), Collections.emptyList()
        ));
        
        assertEquals(2, cache.size());
        
        cache.clear();
        
        assertEquals(0, cache.size());
        assertFalse(cache.getConfig("users-collection").isPresent());
        assertFalse(cache.getConfig("posts-collection").isPresent());
    }
    
    @Test
    void shouldReturnCorrectSize() {
        assertEquals(0, cache.size());
        
        cache.updateConfig("users-collection", new AuthzConfig(
            "users-collection", Collections.emptyList(), Collections.emptyList()
        ));
        assertEquals(1, cache.size());
        
        cache.updateConfig("posts-collection", new AuthzConfig(
            "posts-collection", Collections.emptyList(), Collections.emptyList()
        ));
        assertEquals(2, cache.size());
        
        cache.removeConfig("users-collection");
        assertEquals(1, cache.size());
    }
    
    @Test
    void shouldHandleMultipleCollections() {
        AuthzConfig usersConfig = new AuthzConfig(
            "users-collection",
            Arrays.asList(new RoutePolicy("GET", "policy-1", Arrays.asList("ADMIN"))),
            Collections.emptyList()
        );
        AuthzConfig postsConfig = new AuthzConfig(
            "posts-collection",
            Arrays.asList(new RoutePolicy("POST", "policy-2", Arrays.asList("USER"))),
            Collections.emptyList()
        );
        
        cache.updateConfig("users-collection", usersConfig);
        cache.updateConfig("posts-collection", postsConfig);
        
        Optional<AuthzConfig> users = cache.getConfig("users-collection");
        Optional<AuthzConfig> posts = cache.getConfig("posts-collection");
        
        assertTrue(users.isPresent());
        assertTrue(posts.isPresent());
        assertEquals(usersConfig, users.get());
        assertEquals(postsConfig, posts.get());
    }
    
    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String collectionId = "collection-" + (threadId % 5);
                        AuthzConfig config = new AuthzConfig(
                            collectionId,
                            Collections.emptyList(),
                            Collections.emptyList()
                        );
                        
                        // Perform various operations
                        cache.updateConfig(collectionId, config);
                        cache.getConfig(collectionId);
                        
                        if (j % 10 == 0) {
                            cache.removeConfig(collectionId);
                        }
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // All threads should complete successfully
        assertEquals(threadCount, successCount.get());
        
        // Cache should be in a valid state (no exceptions thrown)
        assertTrue(cache.size() >= 0);
    }
    
    @Test
    void shouldHandleConcurrentUpdatesToSameCollection() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    AuthzConfig config = new AuthzConfig(
                        "shared-collection",
                        Arrays.asList(new RoutePolicy("GET", "policy-" + threadId, Arrays.asList("ROLE-" + threadId))),
                        Collections.emptyList()
                    );
                    cache.updateConfig("shared-collection", config);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // One of the configs should be present
        Optional<AuthzConfig> result = cache.getConfig("shared-collection");
        assertTrue(result.isPresent());
        assertEquals("shared-collection", result.get().getCollectionId());
    }
}
