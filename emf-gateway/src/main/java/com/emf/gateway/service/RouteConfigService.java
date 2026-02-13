package com.emf.gateway.service;

import com.emf.gateway.config.BootstrapConfig;
import com.emf.gateway.config.CollectionConfig;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Service for fetching and managing route configuration from the control plane.
 *
 * This service is responsible for:
 * - Fetching initial bootstrap configuration when the gateway starts
 * - Parsing bootstrap response into RouteDefinition objects
 * - Validating required fields before adding routes to the registry
 * - Refreshing routes on demand
 */
@Service
public class RouteConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RouteConfigService.class);

    private final WebClient webClient;
    private final RouteRegistry routeRegistry;
    private final String controlPlaneUrl;
    private final String bootstrapPath;
    private final String workerServiceUrl;

    /**
     * Creates a new RouteConfigService.
     *
     * @param webClientBuilder WebClient builder for making HTTP requests
     * @param routeRegistry Route registry to populate with routes
     * @param controlPlaneUrl Base URL of the control plane service
     * @param bootstrapPath Path to the bootstrap endpoint
     * @param workerServiceUrl Base URL of the worker service
     */
    public RouteConfigService(
            WebClient.Builder webClientBuilder,
            RouteRegistry routeRegistry,
            @Value("${emf.gateway.control-plane.url}") String controlPlaneUrl,
            @Value("${emf.gateway.control-plane.bootstrap-path}") String bootstrapPath,
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(controlPlaneUrl).build();
        this.routeRegistry = routeRegistry;
        this.controlPlaneUrl = controlPlaneUrl;
        this.bootstrapPath = bootstrapPath;
        this.workerServiceUrl = workerServiceUrl;

        logger.info("RouteConfigService initialized with control plane URL: {}", controlPlaneUrl);
    }

    /**
     * Fetches the complete bootstrap configuration from the control plane.
     *
     * This method calls the control plane's bootstrap endpoint and returns
     * the complete configuration including collections and authorization.
     *
     * @return Mono containing the bootstrap configuration
     * @throws RuntimeException if the bootstrap request fails
     */
    public Mono<BootstrapConfig> fetchBootstrapConfig() {
        String url = controlPlaneUrl + bootstrapPath;
        logger.info("Fetching bootstrap configuration from: {}", url);

        return webClient.get()
                .uri(bootstrapPath)
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
     *
     * This method:
     * 1. Fetches bootstrap configuration from control plane
     * 2. Parses collections into RouteDefinition objects
     * 3. Validates required fields
     * 4. Updates the route registry
     *
     * Invalid routes (missing required fields) are skipped with error logging.
     */
    public void refreshRoutes() {
        logger.info("Starting route refresh");

        fetchBootstrapConfig()
                .doOnNext(config -> {
                    // Process each collection and create routes
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
                })
                .doOnError(error -> {
                    logger.error("Route refresh failed: {}", error.getMessage(), error);
                })
                .block(); // Block to ensure routes are loaded before returning
    }

    /**
     * Parses a CollectionConfig into a RouteDefinition.
     *
     * @param collection The collection configuration
     * @return RouteDefinition or null if parsing fails
     */
    private RouteDefinition parseCollectionToRoute(CollectionConfig collection) {
        try {
            // Extract required fields
            String collectionId = collection.getId();
            String collectionName = collection.getName();
            String path = collection.getPath();

            // Add /** wildcard to path if not already present
            if (path != null && !path.endsWith("/**") && !path.endsWith("/*")) {
                path = path + "/**";
            }

            // Create route definition using worker service URL
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
     * Validates that a RouteDefinition has all required fields.
     *
     * Required fields:
     * - collectionId (id): Unique route identifier
     * - path: Path pattern for matching requests
     * - backendUrl: Backend service URL
     *
     * @param route The route to validate
     * @return true if valid, false otherwise
     */
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
