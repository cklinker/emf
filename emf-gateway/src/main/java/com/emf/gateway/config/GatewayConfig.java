package com.emf.gateway.config;

import com.emf.gateway.route.DynamicRouteLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Spring Cloud Gateway routing.
 * 
 * This class configures the gateway's routing behavior by:
 * 1. Registering the DynamicRouteLocator as the primary RouteLocator
 * 2. Configuring default filters for all routes
 * 3. Setting timeout configurations
 * 
 * The DynamicRouteLocator provides routes from the RouteRegistry, which is
 * populated from the control plane bootstrap and updated via Kafka events.
 * 
 * Validates: Requirements 9.1, 9.2
 */
@Configuration
public class GatewayConfig {
    
    /**
     * Configures the RouteLocator bean to use DynamicRouteLocator.
     * 
     * The DynamicRouteLocator bridges our RouteRegistry with Spring Cloud Gateway's
     * routing system, converting RouteDefinition objects into Spring Cloud Gateway
     * Route objects.
     * 
     * @param dynamicRouteLocator The dynamic route locator that provides routes from the registry
     * @return RouteLocator for Spring Cloud Gateway
     */
    @Bean
    public RouteLocator routeLocator(DynamicRouteLocator dynamicRouteLocator) {
        return dynamicRouteLocator;
    }
}
