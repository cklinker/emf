package com.emf.gateway.error;

/**
 * Exception thrown when authentication fails.
 * Results in HTTP 401 Unauthorized response.
 */
public class GatewayAuthenticationException extends RuntimeException {
    
    public GatewayAuthenticationException(String message) {
        super(message);
    }
    
    public GatewayAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
