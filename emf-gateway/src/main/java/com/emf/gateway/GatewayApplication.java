package com.emf.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the EMF API Gateway.
 * 
 * The gateway serves as the main ingress point for all EMF platform applications,
 * providing centralized authentication, authorization, dynamic routing, JSON:API
 * processing, and rate limiting capabilities.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
