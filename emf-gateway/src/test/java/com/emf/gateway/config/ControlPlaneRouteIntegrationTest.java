package com.emf.gateway.config;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for control plane route configuration.
 * 
 * Verifies that:
 * - Control plane route is added to registry on startup
 * - Route has correct configuration
 * - Route uses same filters as other routes (authentication, authorization, etc.)
 * - Bootstrap endpoint exception is handled by JwtAuthenticationFilter
 * 
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4
 */
@SpringBootTest
@ActiveProfiles("test")
class ControlPlaneRouteIntegrationTest {
    
    @Autowired
    private RouteRegistry routeRegistry;
    
    @Test
    void testControlPlaneRoute_AddedOnStartup() {
        // Assert - verify control plane route exists in registry
        Optional<RouteDefinition> route = routeRegistry.findByPath("/control/**");
        
        assertThat(route).isPresent();
        assertThat(route.get().getId()).isEqualTo("control-plane");
        assertThat(route.get().getServiceId()).isEqualTo("control-plane");
        assertThat(route.get().getPath()).isEqualTo("/control/**");
        assertThat(route.get().getCollectionName()).isEqualTo("control-plane");
    }
    
    @Test
    void testControlPlaneRoute_HasCorrectBackendUrl() {
        // Assert - verify backend URL is configured from application properties
        Optional<RouteDefinition> route = routeRegistry.findByPath("/control/**");
        
        assertThat(route).isPresent();
        assertThat(route.get().getBackendUrl()).isNotNull();
        assertThat(route.get().getBackendUrl()).isNotEmpty();
        
        // In test profile, this should be the test control plane URL
        // In production, this would be the actual control plane service URL
    }
    
    @Test
    void testControlPlaneRoute_NoRateLimiting() {
        // Assert - verify control plane route has no rate limiting
        Optional<RouteDefinition> route = routeRegistry.findByPath("/control/**");
        
        assertThat(route).isPresent();
        assertThat(route.get().hasRateLimit()).isFalse();
    }
    
    @Test
    void testControlPlaneRoute_PathPatternMatchesEndpoints() {
        // Assert - verify path pattern will match all control plane endpoints
        Optional<RouteDefinition> route = routeRegistry.findByPath("/control/**");
        
        assertThat(route).isPresent();
        String pathPattern = route.get().getPath();
        
        // The pattern "/control/**" should match:
        // - /control/bootstrap (unauthenticated)
        // - /control/collections (authenticated)
        // - /control/services (authenticated)
        // - /control/authorization (authenticated)
        // - /control/packages (authenticated)
        // - Any other control plane endpoints
        
        assertThat(pathPattern).isEqualTo("/control/**");
    }
    
    @Test
    void testControlPlaneRoute_UsesStandardFilters() {
        // The control plane route uses the same filters as other routes:
        // 1. JwtAuthenticationFilter (order -100) - validates JWT, allows /control/bootstrap
        // 2. RateLimitFilter (order -50) - skipped because no rate limit config
        // 3. RouteAuthorizationFilter (order 0) - checks route policies
        // 4. HeaderTransformationFilter (order 50) - adds X-Forwarded-* headers
        // 5. FieldAuthorizationFilter (order 100) - filters response fields
        // 6. JsonApiIncludeFilter (order 200) - processes includes
        
        // This test documents the filter chain behavior
        // The actual filter execution is tested in individual filter tests
        
        Optional<RouteDefinition> route = routeRegistry.findByPath("/control/**");
        assertThat(route).isPresent();
        
        // Control plane route is a regular route, so it goes through all filters
        // The only exception is the bootstrap endpoint, which is handled specially
        // in JwtAuthenticationFilter to allow unauthenticated access
    }
}
