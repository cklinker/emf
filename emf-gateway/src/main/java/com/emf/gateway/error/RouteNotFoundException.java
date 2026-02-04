package com.emf.gateway.error;

/**
 * Exception thrown when no matching route is found.
 * Results in HTTP 404 Not Found response.
 */
public class RouteNotFoundException extends RuntimeException {
    
    public RouteNotFoundException(String message) {
        super(message);
    }
    
    public RouteNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
