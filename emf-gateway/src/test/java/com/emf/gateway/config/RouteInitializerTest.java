package com.emf.gateway.config;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.gateway.service.RouteConfigService;
import com.emf.gateway.tenant.TenantSlugCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteInitializer.
 * 
 * Tests verify that:
 * - Control plane route is added on startup
 * - Control plane route has correct configuration
 * - Dynamic routes are fetched after control plane route is added
 * - Bootstrap endpoint exception is documented
 */
@ExtendWith(MockitoExtension.class)
class RouteInitializerTest {
    
    @Mock
    private RouteRegistry routeRegistry;
    
    @Mock
    private RouteConfigService routeConfigService;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TenantSlugCache tenantSlugCache;

    @Mock
    private ApplicationArguments applicationArguments;
    
    private RouteInitializer routeInitializer;
    
    private static final String CONTROL_PLANE_URL = "http://control-plane:8080";
    
    @BeforeEach
    void setUp() {
        routeInitializer = new RouteInitializer(
            routeRegistry,
            routeConfigService,
            eventPublisher,
            tenantSlugCache,
            CONTROL_PLANE_URL
        );
    }
    
    @Test
    void testRun_AddsControlPlaneRoute() {
        // Act
        routeInitializer.run(applicationArguments);
        
        // Assert - verify control plane route was added
        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry).addRoute(routeCaptor.capture());
        
        RouteDefinition capturedRoute = routeCaptor.getValue();
        assertThat(capturedRoute).isNotNull();
        assertThat(capturedRoute.getId()).isEqualTo("00000000-0000-0000-0000-000000000100");
        assertThat(capturedRoute.getPath()).isEqualTo("/control/**");
        assertThat(capturedRoute.getBackendUrl()).isEqualTo(CONTROL_PLANE_URL);
        assertThat(capturedRoute.getCollectionName()).isEqualTo("__control-plane");
    }
    
    @Test
    void testRun_CallsRefreshRoutes() {
        // Act
        routeInitializer.run(applicationArguments);
        
        // Assert - verify dynamic routes are fetched
        verify(routeConfigService).refreshRoutes();
    }
    
    @Test
    void testRun_ControlPlaneRouteAddedBeforeRefresh() {
        // Act
        routeInitializer.run(applicationArguments);
        
        // Assert - verify order of operations
        var inOrder = inOrder(routeRegistry, routeConfigService);
        inOrder.verify(routeRegistry).addRoute(any(RouteDefinition.class));
        inOrder.verify(routeConfigService).refreshRoutes();
    }
    
    @Test
    void testControlPlaneRoute_UsesCorrectPath() {
        // Act
        routeInitializer.run(applicationArguments);
        
        // Assert - verify path pattern matches control plane endpoints
        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry).addRoute(routeCaptor.capture());
        
        RouteDefinition route = routeCaptor.getValue();
        assertThat(route.getPath()).isEqualTo("/control/**");
        
        // This path pattern will match:
        // - /control/bootstrap (unauthenticated, handled by JwtAuthenticationFilter)
        // - /control/collections (authenticated)
        // - /control/services (authenticated)
        // - /control/authorization (authenticated)
        // - Any other control plane endpoints
    }
    
    @Test
    void testControlPlaneRoute_NoRateLimiting() {
        // Act
        routeInitializer.run(applicationArguments);
        
        // Assert - verify no rate limiting on control plane route
        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry).addRoute(routeCaptor.capture());
        
        RouteDefinition route = routeCaptor.getValue();
        assertThat(route.hasRateLimit()).isFalse();
    }
    
    @Test
    void testControlPlaneRoute_UsesConfiguredUrl() {
        // Arrange - create initializer with different URL
        String customUrl = "http://custom-control-plane:9090";
        RouteInitializer customInitializer = new RouteInitializer(
            routeRegistry,
            routeConfigService,
            eventPublisher,
            tenantSlugCache,
            customUrl
        );
        
        // Act
        customInitializer.run(applicationArguments);
        
        // Assert - verify custom URL is used
        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry).addRoute(routeCaptor.capture());
        
        RouteDefinition route = routeCaptor.getValue();
        assertThat(route.getBackendUrl()).isEqualTo(customUrl);
    }
}
