package com.emf.gateway.config;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.gateway.service.RouteConfigService;
import com.emf.gateway.tenant.TenantSlugCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Initializes routes on application startup.
 * 
 * This component:
 * 1. Adds a static route for the control plane service at "/control/**"
 * 2. Fetches dynamic routes from the control plane bootstrap endpoint
 * 
 * The control plane route is added first to ensure the control plane itself
 * is accessible through the gateway. The bootstrap endpoint "/control/bootstrap"
 * is configured to skip authentication in JwtAuthenticationFilter.
 * 
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4
 */
@Component
public class RouteInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(RouteInitializer.class);
    
    /** Well-known UUID for the __control-plane system collection (see V43 migration) */
    private static final String CONTROL_PLANE_ROUTE_ID = "00000000-0000-0000-0000-000000000100";
    private static final String CONTROL_PLANE_PATH = "/control/**";
    private static final String CONTROL_PLANE_COLLECTION_NAME = "__control-plane";
    
    private final RouteRegistry routeRegistry;
    private final RouteConfigService routeConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantSlugCache tenantSlugCache;
    private final String controlPlaneUrl;

    /**
     * Creates a new RouteInitializer.
     *
     * @param routeRegistry The route registry to populate
     * @param routeConfigService Service for fetching routes from control plane
     * @param eventPublisher Event publisher for triggering route refresh
     * @param tenantSlugCache Tenant slug cache to prime on startup
     * @param controlPlaneUrl Base URL of the control plane service
     */
    public RouteInitializer(
            RouteRegistry routeRegistry,
            RouteConfigService routeConfigService,
            ApplicationEventPublisher eventPublisher,
            TenantSlugCache tenantSlugCache,
            @Value("${emf.gateway.control-plane.url}") String controlPlaneUrl) {
        this.routeRegistry = routeRegistry;
        this.routeConfigService = routeConfigService;
        this.eventPublisher = eventPublisher;
        this.tenantSlugCache = tenantSlugCache;
        this.controlPlaneUrl = controlPlaneUrl;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        logger.info("Initializing gateway routes");

        // Prime the tenant slug cache before route loading
        try {
            tenantSlugCache.refresh();
        } catch (Exception e) {
            logger.warn("Failed to prime tenant slug cache on startup; will retry on next cache refresh: {}", e.getMessage());
        }

        // Add static control plane route first
        addControlPlaneRoute();

        // Fetch and add dynamic routes from control plane.
        // Wrapped in try-catch so the RefreshRoutesEvent always fires — the static
        // control-plane route must be available even if dynamic route loading fails.
        try {
            routeConfigService.refreshRoutes();
        } catch (Exception e) {
            logger.error("Failed to load dynamic routes from control plane on startup: {}", e.getMessage(), e);
        }

        // Trigger Spring Cloud Gateway to refresh its route cache.
        // This MUST run even if dynamic route loading failed, so that the static
        // control-plane route is picked up by Spring Cloud Gateway.
        logger.info("Publishing RefreshRoutesEvent to update Gateway route cache");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));

        logger.info("Route initialization completed with {} routes", routeRegistry.size());
    }
    
    /**
     * Adds a static route for the control plane service.
     * 
     * This route allows the control plane itself to be accessed through the gateway
     * at the path "/control/**". The route uses the same filters as other routes,
     * including authentication and authorization.
     * 
     * The bootstrap endpoint "/control/bootstrap" is exempted from authentication
     * in the JwtAuthenticationFilter to allow initial configuration fetching.
     * 
     * Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    private void addControlPlaneRoute() {
        logger.info("Adding static control plane route: path={}, url={}", 
                   CONTROL_PLANE_PATH, controlPlaneUrl);
        
        RouteDefinition controlPlaneRoute = new RouteDefinition(
            CONTROL_PLANE_ROUTE_ID,
            CONTROL_PLANE_PATH,
            controlPlaneUrl,
            CONTROL_PLANE_COLLECTION_NAME
        );
        
        routeRegistry.addRoute(controlPlaneRoute);
        
        logger.info("Control plane route added successfully. " +
                   "Note: /control/bootstrap endpoint allows unauthenticated access.");
    }
}
