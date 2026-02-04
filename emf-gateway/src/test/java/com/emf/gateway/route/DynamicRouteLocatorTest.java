package com.emf.gateway.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DynamicRouteLocator.
 * 
 * Tests the conversion of RouteDefinition objects to Spring Cloud Gateway Route objects
 * and the path matching logic.
 */
class DynamicRouteLocatorTest {
    
    private RouteRegistry routeRegistry;
    private DynamicRouteLocator routeLocator;
    
    @BeforeEach
    void setUp() {
        routeRegistry = new RouteRegistry();
        routeLocator = new DynamicRouteLocator(routeRegistry);
    }
    
    @Test
    void getRoutes_withEmptyRegistry_returnsEmptyFlux() {
        // When
        Flux<Route> routes = routeLocator.getRoutes();
        
        // Then
        StepVerifier.create(routes)
                .expectComplete()
                .verify();
    }
    
    @Test
    void getRoutes_withSingleRoute_returnsOneRoute() {
        // Given
        RouteDefinition routeDef = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        routeRegistry.addRoute(routeDef);
        
        // When
        Flux<Route> routes = routeLocator.getRoutes();
        
        // Then
        StepVerifier.create(routes)
                .assertNext(route -> {
                    assertThat(route.getId()).isEqualTo("users-collection");
                    assertThat(route.getUri().toString()).isEqualTo("http://user-service:8080");
                })
                .expectComplete()
                .verify();
    }
    
    @Test
    void getRoutes_withMultipleRoutes_returnsAllRoutes() {
        // Given
        RouteDefinition route1 = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        RouteDefinition route2 = new RouteDefinition(
                "posts-collection",
                "post-service",
                "/api/posts/**",
                "http://post-service:8080",
                "posts"
        );
        routeRegistry.addRoute(route1);
        routeRegistry.addRoute(route2);
        
        // When
        Flux<Route> routes = routeLocator.getRoutes();
        
        // Then
        StepVerifier.create(routes)
                .recordWith(() -> new java.util.ArrayList<Route>())
                .thenConsumeWhile(route -> true)
                .consumeRecordedWith(routeList -> {
                    assertThat(routeList).hasSize(2);
                    assertThat(routeList).extracting(Route::getId)
                            .containsExactlyInAnyOrder("users-collection", "posts-collection");
                })
                .expectComplete()
                .verify();
    }
    
    @Test
    void convertToRoute_withInvalidBackendUrl_throwsException() {
        // Given
        RouteDefinition routeDef = new RouteDefinition(
                "invalid-route",
                "invalid-service",
                "/api/invalid/**",
                "not-a-valid-url",
                "invalid"
        );
        routeRegistry.addRoute(routeDef);
        
        // When/Then
        StepVerifier.create(routeLocator.getRoutes())
                .expectError(IllegalStateException.class)
                .verify();
    }
    
    @Test
    void getRoutes_afterRegistryUpdate_returnsUpdatedRoutes() {
        // Given - initial route
        RouteDefinition route1 = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        routeRegistry.addRoute(route1);
        
        // When - get initial routes
        List<Route> initialRoutes = routeLocator.getRoutes().collectList().block();
        assertThat(initialRoutes).hasSize(1);
        
        // Given - add another route
        RouteDefinition route2 = new RouteDefinition(
                "posts-collection",
                "post-service",
                "/api/posts/**",
                "http://post-service:8080",
                "posts"
        );
        routeRegistry.addRoute(route2);
        
        // When - get updated routes
        List<Route> updatedRoutes = routeLocator.getRoutes().collectList().block();
        
        // Then - should reflect the update
        assertThat(updatedRoutes).hasSize(2);
    }
    
    @Test
    void convertToRoute_preservesRouteId() {
        // Given
        RouteDefinition routeDef = new RouteDefinition(
                "my-custom-id",
                "my-service",
                "/api/custom/**",
                "http://custom-service:8080",
                "custom"
        );
        routeRegistry.addRoute(routeDef);
        
        // When
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        
        // Then
        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).getId()).isEqualTo("my-custom-id");
    }
    
    @Test
    void convertToRoute_preservesBackendUrl() {
        // Given
        String backendUrl = "http://backend-service:9090";
        RouteDefinition routeDef = new RouteDefinition(
                "test-route",
                "test-service",
                "/api/test/**",
                backendUrl,
                "test"
        );
        routeRegistry.addRoute(routeDef);
        
        // When
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        
        // Then
        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).getUri().toString()).isEqualTo(backendUrl);
    }
    
    @Test
    void convertToRoute_withExactPath_matchesExactly() {
        // Given
        RouteDefinition routeDef = new RouteDefinition(
                "health-check",
                "health-service",
                "/health",
                "http://health-service:8080",
                "health"
        );
        routeRegistry.addRoute(routeDef);
        
        // When
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        Route route = routes.get(0);
        
        // Then - exact match should work
        MockServerHttpRequest exactRequest = MockServerHttpRequest.get("/health").build();
        MockServerWebExchange exactExchange = MockServerWebExchange.from(exactRequest);
        assertThat(Mono.from(route.getPredicate().apply(exactExchange)).block()).isTrue();
        
        // Then - non-matching path should fail
        MockServerHttpRequest nonMatchRequest = MockServerHttpRequest.get("/health/status").build();
        MockServerWebExchange nonMatchExchange = MockServerWebExchange.from(nonMatchRequest);
        assertThat(Mono.from(route.getPredicate().apply(nonMatchExchange)).block()).isFalse();
    }
    
    @Test
    void convertToRoute_withDoubleWildcard_matchesMultipleSegments() {
        // Given
        RouteDefinition routeDef = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/**",
                "http://user-service:8080",
                "users"
        );
        routeRegistry.addRoute(routeDef);
        
        // When
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        Route route = routes.get(0);
        
        // Then - should match single segment
        MockServerHttpRequest singleSegment = MockServerHttpRequest.get("/api/users/123").build();
        MockServerWebExchange singleExchange = MockServerWebExchange.from(singleSegment);
        assertThat(Mono.from(route.getPredicate().apply(singleExchange)).block()).isTrue();
        
        // Then - should match multiple segments
        MockServerHttpRequest multiSegment = MockServerHttpRequest.get("/api/users/123/posts/456").build();
        MockServerWebExchange multiExchange = MockServerWebExchange.from(multiSegment);
        assertThat(Mono.from(route.getPredicate().apply(multiExchange)).block()).isTrue();
        
        // Then - should not match different prefix
        MockServerHttpRequest differentPrefix = MockServerHttpRequest.get("/api/posts/123").build();
        MockServerWebExchange differentExchange = MockServerWebExchange.from(differentPrefix);
        assertThat(Mono.from(route.getPredicate().apply(differentExchange)).block()).isFalse();
    }
    
    @Test
    void convertToRoute_withSingleWildcard_matchesSingleSegmentOnly() {
        // Given
        RouteDefinition routeDef = new RouteDefinition(
                "users-collection",
                "user-service",
                "/api/users/*",
                "http://user-service:8080",
                "users"
        );
        routeRegistry.addRoute(routeDef);
        
        // When
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        Route route = routes.get(0);
        
        // Then - should match single segment
        MockServerHttpRequest singleSegment = MockServerHttpRequest.get("/api/users/123").build();
        MockServerWebExchange singleExchange = MockServerWebExchange.from(singleSegment);
        assertThat(Mono.from(route.getPredicate().apply(singleExchange)).block()).isTrue();
        
        // Then - should NOT match multiple segments
        MockServerHttpRequest multiSegment = MockServerHttpRequest.get("/api/users/123/posts").build();
        MockServerWebExchange multiExchange = MockServerWebExchange.from(multiSegment);
        assertThat(Mono.from(route.getPredicate().apply(multiExchange)).block()).isFalse();
    }
}
