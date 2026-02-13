package com.emf.gateway.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RouteRegistry class.
 * Tests basic functionality and thread-safety.
 */
class RouteRegistryTest {
    
    private RouteRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new RouteRegistry();
    }
    
    @Test
    void testAddRoute() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        
        registry.addRoute(route);
        
        assertEquals(1, registry.size());
        Optional<RouteDefinition> found = registry.findByPath("/api/users/**");
        assertTrue(found.isPresent());
        assertEquals(route, found.get());
    }
    
    @Test
    void testAddRouteReplacesExisting() {
        RouteDefinition route1 = createRoute("users-collection-v1", "/api/users/**");
        RouteDefinition route2 = createRoute("users-collection-v2", "/api/users/**");
        
        registry.addRoute(route1);
        registry.addRoute(route2);
        
        assertEquals(1, registry.size());
        Optional<RouteDefinition> found = registry.findByPath("/api/users/**");
        assertTrue(found.isPresent());
        assertEquals("users-collection-v2", found.get().getId());
    }
    
    @Test
    void testAddNullRoute() {
        registry.addRoute(null);
        
        assertEquals(0, registry.size());
        assertTrue(registry.isEmpty());
    }
    
    @Test
    void testAddRouteWithNullPath() {
        RouteDefinition route = new RouteDefinition(
            "invalid-route",
            null,
            "http://backend:8080",
            "collection"
        );
        
        registry.addRoute(route);
        
        assertEquals(0, registry.size());
    }
    
    @Test
    void testAddRouteWithEmptyPath() {
        RouteDefinition route = new RouteDefinition(
            "invalid-route",
            "",
            "http://backend:8080",
            "collection"
        );
        
        registry.addRoute(route);
        
        assertEquals(0, registry.size());
    }
    
    @Test
    void testRemoveRoute() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        
        registry.addRoute(route);
        assertEquals(1, registry.size());
        
        registry.removeRoute("users-collection");
        
        assertEquals(0, registry.size());
        assertFalse(registry.findByPath("/api/users/**").isPresent());
    }
    
    @Test
    void testRemoveNonExistentRoute() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        registry.addRoute(route);
        
        registry.removeRoute("non-existent-id");
        
        assertEquals(1, registry.size());
    }
    
    @Test
    void testRemoveNullRouteId() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        registry.addRoute(route);
        
        registry.removeRoute(null);
        
        assertEquals(1, registry.size());
    }
    
    @Test
    void testUpdateRoute() {
        RouteDefinition route1 = createRoute("users-collection", "/api/users/**");
        registry.addRoute(route1);
        
        // Update with same ID but different path
        RouteDefinition route2 = new RouteDefinition(
            "users-collection",
            "/api/v2/users/**",
            "http://user-service-v2:8080",
            "users"
        );
        
        registry.updateRoute(route2);
        
        assertEquals(1, registry.size());
        assertFalse(registry.findByPath("/api/users/**").isPresent());
        assertTrue(registry.findByPath("/api/v2/users/**").isPresent());
    }
    
    @Test
    void testUpdateNullRoute() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        registry.addRoute(route);
        
        registry.updateRoute(null);
        
        assertEquals(1, registry.size());
    }
    
    @Test
    void testFindByPath() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        registry.addRoute(route);
        
        Optional<RouteDefinition> found = registry.findByPath("/api/users/**");
        
        assertTrue(found.isPresent());
        assertEquals(route, found.get());
    }
    
    @Test
    void testFindByPathNotFound() {
        Optional<RouteDefinition> found = registry.findByPath("/api/nonexistent/**");
        
        assertFalse(found.isPresent());
    }
    
    @Test
    void testFindByNullPath() {
        Optional<RouteDefinition> found = registry.findByPath(null);
        
        assertFalse(found.isPresent());
    }
    
    @Test
    void testFindByEmptyPath() {
        Optional<RouteDefinition> found = registry.findByPath("");
        
        assertFalse(found.isPresent());
    }
    
    @Test
    void testGetAllRoutes() {
        RouteDefinition route1 = createRoute("users-collection", "/api/users/**");
        RouteDefinition route2 = createRoute("posts-collection", "/api/posts/**");
        RouteDefinition route3 = createRoute("comments-collection", "/api/comments/**");
        
        registry.addRoute(route1);
        registry.addRoute(route2);
        registry.addRoute(route3);
        
        List<RouteDefinition> allRoutes = registry.getAllRoutes();
        
        assertEquals(3, allRoutes.size());
        assertTrue(allRoutes.contains(route1));
        assertTrue(allRoutes.contains(route2));
        assertTrue(allRoutes.contains(route3));
    }
    
    @Test
    void testGetAllRoutesReturnsDefensiveCopy() {
        RouteDefinition route = createRoute("users-collection", "/api/users/**");
        registry.addRoute(route);
        
        List<RouteDefinition> allRoutes = registry.getAllRoutes();
        allRoutes.clear();
        
        // Original registry should still have the route
        assertEquals(1, registry.size());
    }
    
    @Test
    void testClear() {
        registry.addRoute(createRoute("users-collection", "/api/users/**"));
        registry.addRoute(createRoute("posts-collection", "/api/posts/**"));
        registry.addRoute(createRoute("comments-collection", "/api/comments/**"));
        
        assertEquals(3, registry.size());
        
        registry.clear();
        
        assertEquals(0, registry.size());
        assertTrue(registry.isEmpty());
    }
    
    @Test
    void testConcurrentAddOperations() throws InterruptedException {
        int threadCount = 10;
        int routesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < routesPerThread; j++) {
                        String id = "route-" + threadId + "-" + j;
                        String path = "/api/thread" + threadId + "/resource" + j + "/**";
                        registry.addRoute(createRoute(id, path));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(threadCount * routesPerThread, registry.size());
    }
    
    @Test
    void testConcurrentMixedOperations() throws InterruptedException {
        // Pre-populate with some routes
        for (int i = 0; i < 50; i++) {
            registry.addRoute(createRoute("route-" + i, "/api/resource" + i + "/**"));
        }
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // Thread 1: Add routes
        executor.submit(() -> {
            try {
                for (int i = 50; i < 100; i++) {
                    registry.addRoute(createRoute("route-" + i, "/api/resource" + i + "/**"));
                }
            } finally {
                latch.countDown();
            }
        });
        
        // Thread 2: Remove routes
        executor.submit(() -> {
            try {
                for (int i = 0; i < 25; i++) {
                    registry.removeRoute("route-" + i);
                }
            } finally {
                latch.countDown();
            }
        });
        
        // Thread 3: Update routes
        executor.submit(() -> {
            try {
                for (int i = 25; i < 50; i++) {
                    registry.updateRoute(createRoute("route-" + i, "/api/v2/resource" + i + "/**"));
                }
            } finally {
                latch.countDown();
            }
        });
        
        // Thread 4: Read routes
        executor.submit(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    registry.findByPath("/api/resource" + i + "/**");
                    registry.getAllRoutes();
                }
            } finally {
                latch.countDown();
            }
        });
        
        // Thread 5: Read routes
        executor.submit(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    registry.getAllRoutes();
                    registry.size();
                }
            } finally {
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Verify registry is in a consistent state
        int finalSize = registry.size();
        assertEquals(finalSize, registry.getAllRoutes().size());
        assertTrue(finalSize > 0); // Should have some routes remaining
    }
    
    private RouteDefinition createRoute(String id, String path) {
        return new RouteDefinition(
            id,
            path,
            "http://backend-" + id + ":8080",
            "collection-" + id
        );
    }
}
