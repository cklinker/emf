package com.emf.gateway.service;

import com.emf.gateway.config.BootstrapConfig;
import com.emf.gateway.config.CollectionConfig;
import com.emf.gateway.ratelimit.TenantGovernorLimitCache;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Service for fetching and managing route configuration from the worker service.
 *
 * <p>This service is responsible for:
 * <ul>
 *   <li>Fetching initial bootstrap configuration from the worker's internal API</li>
 *   <li>Parsing bootstrap response into RouteDefinition objects</li>
 *   <li>Validating required fields before adding routes to the registry</li>
 *   <li>Loading per-tenant governor limits for rate limiting</li>
 *   <li>Refreshing routes on demand</li>
 * </ul>
 *
 * <p>The worker exposes {@code /internal/bootstrap} which returns collections
 * and governor limits. This replaces the previous dependency on the control
 * plane's bootstrap endpoint, now replaced by the worker's
 * {@code /internal/bootstrap} endpoint.
 */
@Service
public class RouteConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RouteConfigService.class);

    private static final String BOOTSTRAP_PATH = "/internal/bootstrap";

    private final WebClient webClient;
    private final RouteRegistry routeRegistry;
    private final TenantGovernorLimitCache governorLimitCache;
    private final String workerServiceUrl;

    public RouteConfigService(
            WebClient.Builder webClientBuilder,
            RouteRegistry routeRegistry,
            TenantGovernorLimitCache governorLimitCache,
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();
        this.routeRegistry = routeRegistry;
        this.governorLimitCache = governorLimitCache;
        this.workerServiceUrl = workerServiceUrl;

        logger.info("RouteConfigService initialized with worker URL: {}", workerServiceUrl);
    }

    /**
     * Fetches the complete bootstrap configuration from the worker service.
     */
    public Mono<BootstrapConfig> fetchBootstrapConfig() {
        String url = workerServiceUrl + BOOTSTRAP_PATH;
        logger.info("Fetching bootstrap configuration from: {}", url);

        return webClient.get()
                .uri(BOOTSTRAP_PATH)
                .retrieve()
                .bodyToMono(BootstrapConfig.class)
                .doOnSuccess(config -> {
                    logger.info("Successfully fetched bootstrap configuration: {}", config);
                })
                .doOnError(error -> {
                    logger.error("Failed to fetch bootstrap configuration from {}: {}",
                               url, error.getMessage(), error);
                });
    }

    /**
     * Refreshes routes by fetching the latest bootstrap configuration
     * and updating the route registry.
     */
    public void refreshRoutes() {
        logger.info("Starting route refresh");

        fetchBootstrapConfig()
                .doOnNext(config -> {
                    if (config.getCollections() != null) {
                        int validRoutes = 0;
                        int invalidRoutes = 0;

                        for (CollectionConfig collection : config.getCollections()) {
                            RouteDefinition route = parseCollectionToRoute(collection);

                            if (route != null && validateRoute(route)) {
                                routeRegistry.addRoute(route);
                                validRoutes++;
                            } else {
                                invalidRoutes++;
                            }
                        }

                        logger.info("Route refresh completed: {} valid routes added, {} invalid routes skipped",
                                  validRoutes, invalidRoutes);
                    } else {
                        logger.warn("No collections found in bootstrap configuration");
                    }

                    // Register static routes for non-collection API endpoints
                    // that are served by the worker but not returned in the
                    // bootstrap collection list (e.g., computed/aggregate endpoints).
                    registerStaticRoutes();

                    // Load per-tenant governor limits for rate limiting
                    if (config.getGovernorLimits() != null) {
                        governorLimitCache.loadFromBootstrap(config.getGovernorLimits());
                        logger.info("Loaded governor limits for {} tenants",
                                config.getGovernorLimits().size());
                    } else {
                        logger.warn("No governor limits found in bootstrap configuration");
                    }
                })
                .doOnError(error -> {
                    logger.error("Route refresh failed: {}", error.getMessage(), error);
                })
                .block();
    }

    private RouteDefinition parseCollectionToRoute(CollectionConfig collection) {
        try {
            String collectionId = collection.getId();
            String collectionName = collection.getName();
            String path = collection.getPath();

            if (path != null && !path.endsWith("/**") && !path.endsWith("/*")) {
                path = path + "/**";
            }

            // Always use the configured worker service URL (K8s Service DNS) instead
            // of the pod-specific IP from the bootstrap response. Pod IPs are ephemeral
            // and become stale when pods restart, causing routing failures. The K8s
            // Service URL (e.g., http://emf-worker:80) is stable and load-balances
            // across all worker pods.
            RouteDefinition route = new RouteDefinition(
                collectionId,
                path,
                workerServiceUrl,
                collectionName
            );

            logger.debug("Parsed collection '{}' to route: {}", collectionId, route);
            return route;

        } catch (Exception e) {
            logger.error("Failed to parse collection to route: {}", collection, e);
            return null;
        }
    }

    /**
     * Registers static routes for non-collection API endpoints served by the worker.
     *
     * <p>These endpoints are not returned in the bootstrap collection list because
     * they are not standard CRUD collections. They still need gateway routes so
     * requests are proxied to the worker instead of returning 404.
     *
     * <p>The {@link com.emf.gateway.filter.CollectionPathRewriteFilter} will rewrite
     * these paths (e.g., {@code /api/governor-limits} → {@code /api/collections/governor-limits})
     * before they reach the worker.
     */
    private void registerStaticRoutes() {
        String[][] staticRoutes = {
                {"governor-limits", "/api/governor-limits/**", "governor-limits"},
                {"modules", "/api/modules/**", "modules"},
        };

        for (String[] routeDef : staticRoutes) {
            RouteDefinition route = new RouteDefinition(
                    "static-" + routeDef[0],
                    routeDef[1],
                    workerServiceUrl,
                    routeDef[2]
            );
            routeRegistry.addRoute(route);
            logger.debug("Registered static route: {}", route);
        }

        logger.info("Registered {} static routes", staticRoutes.length);
    }

    private boolean validateRoute(RouteDefinition route) {
        if (route == null) {
            logger.error("Cannot validate null route");
            return false;
        }

        boolean valid = true;
        StringBuilder errors = new StringBuilder();

        if (route.getId() == null || route.getId().isEmpty()) {
            errors.append("Missing collectionId (id); ");
            valid = false;
        }

        if (route.getPath() == null || route.getPath().isEmpty()) {
            errors.append("Missing path; ");
            valid = false;
        }

        if (route.getBackendUrl() == null || route.getBackendUrl().isEmpty()) {
            errors.append("Missing backendUrl; ");
            valid = false;
        }

        if (!valid) {
            logger.error("Route validation failed for route '{}': {}", route.getId(), errors.toString());
            logger.error("Invalid route details: {}", route);
        }

        return valid;
    }
}
