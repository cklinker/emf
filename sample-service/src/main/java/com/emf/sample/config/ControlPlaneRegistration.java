package com.emf.sample.config;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.registry.CollectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registers the sample service and its collections with the EMF Control Plane.
 * 
 * <p>This component runs after collection initialization and registers:
 * <ul>
 *   <li>The service itself with its base URL</li>
 *   <li>Each collection with its fields and relationships</li>
 * </ul>
 */
@Component
public class ControlPlaneRegistration {
    
    private static final Logger log = LoggerFactory.getLogger(ControlPlaneRegistration.class);
    
    @Autowired
    private CollectionRegistry registry;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${emf.control-plane.url}")
    private String controlPlaneUrl;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    @Value("${emf.keycloak.url}")
    private String keycloakUrl;
    
    @Value("${emf.keycloak.realm}")
    private String keycloakRealm;
    
    @Value("${emf.keycloak.client-id}")
    private String clientId;
    
    @Value("${emf.keycloak.client-secret}")
    private String clientSecret;
    
    private String cachedToken;
    private String serviceId;
    
    /**
     * Registers the service with the control plane after collections are initialized.
     * 
     * <p>This method runs after {@link CollectionInitializer} due to the @Order annotation.
     * Includes retry logic to handle control plane startup timing.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void registerWithControlPlane() {
        log.info("Registering sample service with control plane at {}", controlPlaneUrl);
        
        int maxRetries = 5;
        int retryDelayMs = 2000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Registration attempt {} of {}", attempt, maxRetries);
                
                // Acquire service account token
                acquireServiceAccountToken();
                
                // Register service (or get existing service ID)
                ensureServiceRegistered();
                
                // Register each collection
                for (String collectionName : registry.getAllCollectionNames()) {
                    CollectionDefinition definition = registry.get(collectionName);
                    if (definition != null) {
                        registerCollection(definition);
                    }
                }
                
                log.info("Successfully registered with control plane");
                return; // Success - exit retry loop
                
            } catch (Exception e) {
                log.warn("Registration attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    log.info("Retrying in {}ms...", retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Registration retry interrupted", ie);
                        return;
                    }
                } else {
                    log.error("Failed to register with control plane after {} attempts", maxRetries, e);
                    // Don't fail startup - service can still function locally
                }
            }
        }
    }
    
    /**
     * Acquires a service account token from Keycloak using client credentials flow.
     */
    private void acquireServiceAccountToken() {
        log.info("Acquiring service account token from Keycloak");
        
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
            keycloakUrl, keycloakRealm);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "client_credentials");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            cachedToken = (String) response.getBody().get("access_token");
            log.info("Successfully acquired service account token");
        } catch (Exception e) {
            log.error("Failed to acquire service account token", e);
            throw new RuntimeException("Cannot register with control plane without authentication", e);
        }
    }
    
    /**
     * Ensures the service is registered with the control plane.
     * If the service already exists, retrieves its ID. Otherwise, creates it.
     */
    private void ensureServiceRegistered() {
        String serviceName = "sample-service";
        
        // First, try to get the service by listing all services and finding ours
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(cachedToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            // Try to list services and find ours
            ResponseEntity<Map> response = restTemplate.exchange(
                controlPlaneUrl + "/control/services?size=100",
                org.springframework.http.HttpMethod.GET,
                request,
                Map.class
            );
            
            if (response.getBody() != null && response.getBody().containsKey("content")) {
                List<Map<String, Object>> services = (List<Map<String, Object>>) response.getBody().get("content");
                for (Map<String, Object> service : services) {
                    if (serviceName.equals(service.get("name"))) {
                        serviceId = (String) service.get("id");
                        log.info("Found existing service with id: {}", serviceId);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list services: {}", e.getMessage());
        }
        
        // Service doesn't exist, create it
        createService(serviceName);
    }
    
    /**
     * Creates a new service in the control plane.
     */
    private void createService(String serviceName) {
        String serviceUrl = String.format("http://sample-service:%d", serverPort);
        
        Map<String, Object> serviceRequest = new HashMap<>();
        serviceRequest.put("name", serviceName);
        serviceRequest.put("displayName", "Sample Service");
        serviceRequest.put("description", "Sample domain service for testing");
        serviceRequest.put("basePath", "/api");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cachedToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(serviceRequest, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                controlPlaneUrl + "/control/services",
                request,
                Map.class
            );
            
            if (response.getBody() != null) {
                log.debug("Service creation response: {}", response.getBody());
                
                if (response.getBody().containsKey("id")) {
                    serviceId = (String) response.getBody().get("id");
                    log.info("Created service with id: {}", serviceId);
                } else {
                    log.error("Service creation response did not contain ID. Response: {}", response.getBody());
                    throw new RuntimeException("Service creation response did not contain ID");
                }
            } else {
                log.error("Service creation response body was null");
                throw new RuntimeException("Service creation response body was null");
            }
        } catch (Exception e) {
            log.error("Failed to create service: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot register collections without service ID: " + e.getMessage(), e);
        }
    }
    
    /**
     * Registers a collection with the control plane.
     */
    private void registerCollection(CollectionDefinition definition) {
        log.info("Registering collection: {}", definition.name());
        
        if (serviceId == null) {
            log.error("Cannot register collection {} - service ID is null", definition.name());
            return;
        }
        
        Map<String, Object> collectionRequest = new HashMap<>();
        collectionRequest.put("name", definition.name());
        collectionRequest.put("serviceId", serviceId);
        collectionRequest.put("path", definition.apiConfig().basePath());
        collectionRequest.put("fields", extractFields(definition));
        collectionRequest.put("relationships", extractRelationships(definition));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cachedToken);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(collectionRequest, headers);
        
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                controlPlaneUrl + "/control/collections",
                request,
                Void.class
            );
            log.info("Collection {} registration response: {}", definition.name(), response.getStatusCode());
        } catch (Exception e) {
            log.warn("Collection {} registration failed (may already exist): {}", definition.name(), e.getMessage());
        }
    }
    
    /**
     * Extracts field definitions for control plane registration.
     */
    private List<Map<String, Object>> extractFields(CollectionDefinition definition) {
        return definition.fields().stream()
            .map(field -> {
                Map<String, Object> fieldMap = new HashMap<>();
                fieldMap.put("name", field.name());
                fieldMap.put("type", field.type().name());
                fieldMap.put("required", !field.nullable());
                return fieldMap;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Extracts relationship definitions for control plane registration.
     */
    private List<Map<String, Object>> extractRelationships(CollectionDefinition definition) {
        return definition.fields().stream()
            .filter(field -> field.referenceConfig() != null)
            .map(field -> {
                Map<String, Object> relationshipMap = new HashMap<>();
                relationshipMap.put("name", field.name());
                relationshipMap.put("targetCollection", field.referenceConfig().targetCollection());
                relationshipMap.put("type", "belongsTo");
                return relationshipMap;
            })
            .collect(Collectors.toList());
    }
}
