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
