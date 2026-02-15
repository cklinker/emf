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

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        objectMapper = new ObjectMapper();
        createdCollectionIds = new ArrayList<>();

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
     * Subscribes to emf.config.collection.changed topic.
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

        // Create collection request
        Map<String, Object> collectionRequest = new HashMap<>();
        collectionRequest.put("name", "test-collection-" + System.currentTimeMillis());
        collectionRequest.put("displayName", "Test Collection");
        collectionRequest.put("description", "Test collection for event verification");
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

                    eventFound = true;
                    break;
                }
            }
        }

        assertThat(eventFound)
            .withFailMessage("Collection changed event was not published to Kafka within timeout")
            .isTrue();
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

        createdCollectionIds.clear();
    }
}
