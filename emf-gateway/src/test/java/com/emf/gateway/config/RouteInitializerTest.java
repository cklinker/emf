package com.emf.gateway.config;

import com.emf.gateway.route.RouteRegistry;
import com.emf.gateway.service.RouteConfigService;
import com.emf.gateway.tenant.TenantSlugCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteInitializer.
 *
 * <p>Tests verify that on startup:
 * <ul>
 *   <li>Tenant slug cache is primed</li>
 *   <li>Dynamic routes are fetched from the worker service</li>
 *   <li>A RefreshRoutesEvent is published</li>
 * </ul>
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

    @BeforeEach
    void setUp() {
        routeInitializer = new RouteInitializer(
            routeRegistry,
            routeConfigService,
            eventPublisher,
            tenantSlugCache
        );
    }

    @Test
    void testRun_CallsRefreshRoutes() {
        routeInitializer.run(applicationArguments);

        verify(routeConfigService).refreshRoutes();
    }

    @Test
    void testRun_PrimesTenantSlugCache() {
        routeInitializer.run(applicationArguments);

        verify(tenantSlugCache).refresh();
    }

    @Test
    void testRun_PublishesRefreshRoutesEvent() {
        routeInitializer.run(applicationArguments);

        verify(eventPublisher).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    void testRun_ContinuesWhenSlugCacheFails() {
        doThrow(new RuntimeException("Redis down")).when(tenantSlugCache).refresh();

        routeInitializer.run(applicationArguments);

        // Should still attempt to refresh routes
        verify(routeConfigService).refreshRoutes();
        verify(eventPublisher).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    void testRun_ContinuesWhenRouteRefreshFails() {
        doThrow(new RuntimeException("Worker down")).when(routeConfigService).refreshRoutes();

        routeInitializer.run(applicationArguments);

        // Should still publish refresh event
        verify(eventPublisher).publishEvent(any(RefreshRoutesEvent.class));
    }
}
