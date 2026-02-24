package com.emf.gateway.config;

import com.emf.gateway.route.RouteRegistry;
import com.emf.gateway.service.RouteConfigService;
import com.emf.gateway.tenant.TenantSlugCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Initializes routes on application startup.
 *
 * <p>This component:
 * <ol>
 *   <li>Primes the tenant slug cache from the worker service</li>
 *   <li>Fetches dynamic routes from the worker's internal bootstrap endpoint</li>
 *   <li>Publishes a {@link RefreshRoutesEvent} to update Spring Cloud Gateway</li>
 * </ol>
 *
 * <p>All collections are routed to the worker service.
 */
@Component
public class RouteInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RouteInitializer.class);

    private final RouteRegistry routeRegistry;
    private final RouteConfigService routeConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantSlugCache tenantSlugCache;

    /**
     * Creates a new RouteInitializer.
     *
     * @param routeRegistry The route registry to populate
     * @param routeConfigService Service for fetching routes from the worker
     * @param eventPublisher Event publisher for triggering route refresh
     * @param tenantSlugCache Tenant slug cache to prime on startup
     */
    public RouteInitializer(
            RouteRegistry routeRegistry,
            RouteConfigService routeConfigService,
            ApplicationEventPublisher eventPublisher,
            TenantSlugCache tenantSlugCache) {
        this.routeRegistry = routeRegistry;
        this.routeConfigService = routeConfigService;
        this.eventPublisher = eventPublisher;
        this.tenantSlugCache = tenantSlugCache;
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

        // Fetch and add dynamic routes from the worker service
        try {
            routeConfigService.refreshRoutes();
        } catch (Exception e) {
            logger.error("Failed to load routes from worker on startup: {}", e.getMessage(), e);
        }

        // Trigger Spring Cloud Gateway to refresh its route cache
        logger.info("Publishing RefreshRoutesEvent to update Gateway route cache");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));

        logger.info("Route initialization completed with {} routes", routeRegistry.size());
    }
}
