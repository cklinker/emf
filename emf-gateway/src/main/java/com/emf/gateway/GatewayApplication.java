package com.emf.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the EMF API Gateway.
 *
 * The gateway serves as the main ingress point for all EMF platform applications,
 * providing centralized authentication, authorization, dynamic routing, JSON:API
 * processing, and rate limiting capabilities.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.emf.gateway", "com.emf.jsonapi"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
