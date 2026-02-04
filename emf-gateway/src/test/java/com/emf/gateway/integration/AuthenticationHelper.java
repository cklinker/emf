package com.emf.gateway.integration;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Helper class for authentication operations in integration tests.
 * 
 * Provides methods to:
 * - Acquire JWT tokens from Keycloak for different users
 * - Create HTTP headers with authentication tokens
 * 
 * Validates: Requirements 5.7, 5.8
 */
public class AuthenticationHelper {
    
    private static final String KEYCLOAK_TOKEN_URL = 
        "http://localhost:8180/realms/emf/protocol/openid-connect/token";
    
    private final RestTemplate restTemplate;
    
    public AuthenticationHelper() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Get an admin token from Keycloak.
     * The admin user has both ADMIN and USER roles.
     * 
     * @return JWT access token for admin user
     */
    public String getAdminToken() {
        return getToken("admin", "admin");
    }
    
    /**
     * Get a regular user token from Keycloak.
     * The user has only the USER role.
     * 
     * @return JWT access token for regular user
     */
    public String getUserToken() {
        return getToken("user", "user");
    }
    
    /**
     * Get a JWT token from Keycloak using password grant flow.
     * 
     * @param username The username
     * @param password The password
     * @return JWT access token
     */
    public String getToken(String username, String password) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", "emf-client");
        params.add("client_secret", "emf-client-secret");
        params.add("username", username);
        params.add("password", password);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = 
            new HttpEntity<>(params, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            KEYCLOAK_TOKEN_URL,
            request,
            Map.class
        );
        
        return (String) response.getBody().get("access_token");
    }
    
    /**
     * Create HTTP headers with Bearer authentication token.
     * 
     * @param token The JWT access token
     * @return HTTP headers with Authorization and Content-Type set
     */
    public HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
