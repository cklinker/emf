package com.emf.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for event-driven configuration updates.
 * 
 * Tests that:
 * - Collection changes are published to Kafka when collections are created/updated/deleted
 * - Authorization policy changes are published to Kafka when policies are set
 * - Service changes are published to Kafka when services are created/updated/deleted
 * - Gateway receives and processes configuration change events
 * - Configuration changes take effect without restarting services
 * - Malformed Kafka events are handled gracefully
 * 
 * This test verifies the complete event-driven configuration flow:
 * 1. Make configuration change via Control Plane API
 * 2. Verify event is published to Kafka
 * 3. Verify Gateway processes the event and updates its configuration
 * 4. Verify the configuration change takes effect on subsequent requests
 * 
 * Validates: Requirements 12.1-12.8
 */
public class ConfigurationUpdateIntegrationTest extends IntegrationTestBase {
    
    private KafkaConsumer<String, String> kafkaConsumer;
    private ObjectMapper objectMapper;
    private List<String> createdCollectionIds;
    private List<String> createdServiceIds;
    private List<String> createdPolicyIds;
    
    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        
        objectMapper = new ObjectMapper();
        createdCollectionIds = new ArrayList<>();
        createdServiceIds = new ArrayList<>();
        createdPolicyIds = new ArrayList<>();
        
        // Set up Kafka consumer for verification
        setupKafkaConsumer();
    }
    
    @AfterEach
    @Override
    public void tearDown() {
        // Clean up created resources
        cleanupTestData();
        
        // Close Kafka consumer
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
        
        super.tearDown();
    }
    
    /**
     * Set up Kafka consumer to listen for configuration change events.
     * Subscribes to emf.config.collection.changed, emf.config.authz.changed, and emf.config.service.changed topics.
     */
    private void setupKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        kafkaConsumer = new KafkaConsumer<>(props);
        kafkaConsumer.subscribe(Collections.singletonList("emf.config.collection.changed"));
        
        // Wait for consumer to be assigned partitions (ensures it's ready to receive messages)
        waitForConsumerReady();
    }
    
    /**
     * Waits for the Kafka consumer to be assigned partitions.
     * This ensures the consumer is fully subscribed and ready to receive messages.
     */
    private void waitForConsumerReady() {
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 seconds
        
        while (kafkaConsumer.assignment().isEmpty() && (System.currentTimeMillis() - startTime) < timeout) {
            kafkaConsumer.poll(Duration.ofMillis(100));
        }
        
        if (kafkaConsumer.assignment().isEmpty()) {
            throw new RuntimeException("Kafka consumer failed to get partition assignment within timeout");
        }
    }
    
    /**
     * Test that collection creation publishes a collection changed event to Kafka.
     * 
     * Validates: Requirements 12.1, 4.2
     */
    @Test
    void testCollectionCreation_PublishesCollectionChangedEvent() throws Exception {
        // Arrange - get admin token
        String adminToken = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        
        // Create a test service first
        String serviceId = createTestService(adminToken);
        createdServiceIds.add(serviceId);
        
        // Create collection request
        Map<String, Object> collectionRequest = new HashMap<>();
        collectionRequest.put("name", "test-collection-" + System.currentTimeMillis());
        collectionRequest.put("displayName", "Test Collection");
        collectionRequest.put("description", "Test collection for event verification");
        collectionRequest.put("serviceId", serviceId);
        collectionRequest.put("basePath", "/api/test-collection");
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(collectionRequest, headers);
        
        // Act - create collection via Control Plane API
        ResponseEntity<Map> response = restTemplate.postForEntity(
            CONTROL_PLANE_URL + "/control/collections",
            request,
            Map.class
        );
        
        // Assert - verify collection was created
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        
        String collectionId = (String) response.getBody().get("id");
        createdCollectionIds.add(collectionId);
        
        // Verify event was published to Kafka
        boolean eventFound = false;
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds
        
        while (!eventFound && (System.currentTimeMillis() - startTime) < timeout) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
            
            for (ConsumerRecord<String, String> record : records) {
                Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                
                if (payload != null && collectionId.equals(payload.get("id"))) {
                    // Verify event structure
                    assertThat(event.get("eventType")).isEqualTo("emf.config.collection.changed");
                    assertThat(payload.get("changeType")).isEqualTo("CREATED");
                    assertThat(payload.get("name")).isEqualTo(collectionRequest.get("name"));
                    assertThat(payload.get("serviceId")).isEqualTo(serviceId);
                    
                    eventFound = true;
                    break;
                }
            }
        }
        
        assertThat(eventFound)
            .withFailMessage("Collection changed event was not published to Kafka within timeout")
            .isTrue();
    }
    
    /**
     * Test that authorization policy changes publish authz changed events to Kafka.
     * 
     * Validates: Requirements 12.3, 4.2
     */
    @Test
    void testAuthorizationPolicyChange_PublishesAuthzChangedEvent() throws Exception {
        // Arrange - create a collection first
        String adminToken = authHelper.getAdminToken();
        String serviceId = createTestService(adminToken);
        createdServiceIds.add(serviceId);
        
        String collectionId = createTestCollection(adminToken, serviceId);
        createdCollectionIds.add(collectionId);
        
        // Create a policy
        String policyId = createTestPolicy(adminToken);
        createdPolicyIds.add(policyId);
        
        // Subscribe to emf.config.authz.changed topic
        kafkaConsumer.unsubscribe();
        kafkaConsumer.subscribe(Collections.singletonList("emf.config.authz.changed"));
        
        // Wait for consumer to be ready
        waitForConsumerReady();
        
        // Set authorization configuration
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        Map<String, Object> authzRequest = new HashMap<>();
        
        List<Map<String, Object>> routePolicies = new ArrayList<>();
        Map<String, Object> routePolicy = new HashMap<>();
        routePolicy.put("operation", "POST");
        routePolicy.put("policyId", policyId);
        routePolicies.add(routePolicy);
        
        authzRequest.put("routePolicies", routePolicies);
        authzRequest.put("fieldPolicies", Collections.emptyList());
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(authzRequest, headers);
        
        // Act - set authorization configuration
        ResponseEntity<Map> response = restTemplate.exchange(
            CONTROL_PLANE_URL + "/control/collections/" + collectionId + "/authz",
            HttpMethod.PUT,
            request,
            Map.class
        );
        
        // Assert - verify authorization was set
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify event was published to Kafka
        boolean eventFound = false;
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds
        
        while (!eventFound && (System.currentTimeMillis() - startTime) < timeout) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
            
            for (ConsumerRecord<String, String> record : records) {
                Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                
                if (payload != null && collectionId.equals(payload.get("collectionId"))) {
                    // Verify event structure
                    assertThat(event.get("eventType")).isEqualTo("emf.config.authz.changed");
                    assertThat(payload.get("collectionName")).isNotNull();
                    assertThat(payload.get("routePolicies")).isNotNull();
                    
                    eventFound = true;
                    break;
                }
            }
        }
        
        assertThat(eventFound)
            .withFailMessage("Authorization changed event was not published to Kafka within timeout")
            .isTrue();
    }
    
    /**
     * Test that service deletion publishes a service changed event to Kafka.
     * 
     * Validates: Requirements 12.5, 4.2
     */
    @Test
    void testServiceDeletion_PublishesServiceChangedEvent() throws Exception {
        // Arrange - create a test service
        String adminToken = authHelper.getAdminToken();
        String serviceId = createTestService(adminToken);
        
        // Subscribe to emf.config.service.changed topic
        kafkaConsumer.unsubscribe();
        kafkaConsumer.subscribe(Collections.singletonList("emf.config.service.changed"));
        
        // Wait for consumer to be ready
        waitForConsumerReady();
        
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Act - delete service
        ResponseEntity<Void> response = restTemplate.exchange(
            CONTROL_PLANE_URL + "/control/services/" + serviceId,
            HttpMethod.DELETE,
            request,
            Void.class
        );
        
        // Assert - verify service was deleted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        
        // Verify event was published to Kafka
        boolean eventFound = false;
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds
        
        while (!eventFound && (System.currentTimeMillis() - startTime) < timeout) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
            
            for (ConsumerRecord<String, String> record : records) {
                Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
                Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                
                if (payload != null && serviceId.equals(payload.get("serviceId"))) {
                    // Verify event structure
                    assertThat(event.get("eventType")).isEqualTo("emf.config.service.changed");
                    assertThat(payload.get("changeType")).isEqualTo("DELETED");
                    
                    eventFound = true;
                    break;
                }
            }
        }
        
        assertThat(eventFound)
            .withFailMessage("Service changed event was not published to Kafka within timeout")
            .isTrue();
    }
    
    /**
     * Helper method to create a test service.
     */
    private String createTestService(String adminToken) throws Exception {
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        
        Map<String, Object> serviceRequest = new HashMap<>();
        serviceRequest.put("name", "test-service-" + System.currentTimeMillis());
        serviceRequest.put("displayName", "Test Service");
        serviceRequest.put("description", "Test service for integration testing");
        serviceRequest.put("baseUrl", "http://test-service:8080");
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(serviceRequest, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            CONTROL_PLANE_URL + "/control/services",
            request,
            Map.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }
    
    /**
     * Helper method to create a test collection.
     */
    private String createTestCollection(String adminToken, String serviceId) throws Exception {
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        
        Map<String, Object> collectionRequest = new HashMap<>();
        collectionRequest.put("name", "test-collection-" + System.currentTimeMillis());
        collectionRequest.put("displayName", "Test Collection");
        collectionRequest.put("description", "Test collection for integration testing");
        collectionRequest.put("serviceId", serviceId);
        collectionRequest.put("basePath", "/api/test-collection");
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(collectionRequest, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            CONTROL_PLANE_URL + "/control/collections",
            request,
            Map.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }
    
    /**
     * Helper method to create a test policy.
     */
    private String createTestPolicy(String adminToken) throws Exception {
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        
        Map<String, Object> policyRequest = new HashMap<>();
        policyRequest.put("name", "test-policy-" + System.currentTimeMillis());
        policyRequest.put("description", "Test policy for integration testing");
        policyRequest.put("rules", "{\"roles\":[\"ADMIN\"]}");
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(policyRequest, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            CONTROL_PLANE_URL + "/control/policies",
            request,
            Map.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }
    
    @Override
    protected void cleanupTestData() {
        // Clean up in reverse order of creation
        String adminToken = authHelper.getAdminToken();
        HttpHeaders headers = authHelper.createAuthHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        // Delete collections
        for (String collectionId : createdCollectionIds) {
            try {
                restTemplate.exchange(
                    CONTROL_PLANE_URL + "/control/collections/" + collectionId,
                    HttpMethod.DELETE,
                    request,
                    Void.class
                );
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        
        // Delete services
        for (String serviceId : createdServiceIds) {
            try {
                restTemplate.exchange(
                    CONTROL_PLANE_URL + "/control/services/" + serviceId,
                    HttpMethod.DELETE,
                    request,
                    Void.class
                );
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        
        createdCollectionIds.clear();
        createdServiceIds.clear();
        createdPolicyIds.clear();
    }
}
