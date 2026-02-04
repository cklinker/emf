package com.emf.gateway.error;

/**
 * Exception thrown when authorization fails.
 * Results in HTTP 403 Forbidden response.
 */
public class GatewayAuthorizationException extends RuntimeException {
    
    public GatewayAuthorizationException(String message) {
        super(message);
    }
    
    public GatewayAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
