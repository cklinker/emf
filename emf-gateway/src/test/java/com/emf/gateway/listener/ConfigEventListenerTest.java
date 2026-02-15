package com.emf.gateway.listener;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigEventListener.
 *
 * Tests the Kafka event listener that updates gateway configuration in real-time.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigEventListener Tests")
class ConfigEventListenerTest {

    @Mock
    private RouteRegistry routeRegistry;

    private ObjectMapper objectMapper;
    private ConfigEventListener listener;

    private static final String WORKER_SERVICE_URL = "http://emf-worker:80";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ConfigEventListener(routeRegistry, objectMapper, event -> {}, WORKER_SERVICE_URL);
    }

    @Nested
    @DisplayName("Collection Changed Event Tests")
    class CollectionChangedTests {

        @Test
        @DisplayName("Should add route when collection is created")
        void shouldAddRouteWhenCollectionCreated() {
            // Arrange
            CollectionChangedPayload payload = new CollectionChangedPayload();
            payload.setId("collection-1");
            payload.setName("users");
            payload.setChangeType(ChangeType.CREATED);

            ConfigEvent<CollectionChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleCollectionChanged(event);

            // Assert
            ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
            verify(routeRegistry).updateRoute(routeCaptor.capture());

            RouteDefinition capturedRoute = routeCaptor.getValue();
            assertEquals("collection-1", capturedRoute.getId());
            assertEquals("users", capturedRoute.getCollectionName());
            assertEquals("/api/users/**", capturedRoute.getPath());
            assertEquals(WORKER_SERVICE_URL, capturedRoute.getBackendUrl());
        }

        @Test
        @DisplayName("Should update route when collection is updated")
        void shouldUpdateRouteWhenCollectionUpdated() {
            // Arrange
            CollectionChangedPayload payload = new CollectionChangedPayload();
            payload.setId("collection-1");
            payload.setName("users");
            payload.setChangeType(ChangeType.UPDATED);

            ConfigEvent<CollectionChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleCollectionChanged(event);

            // Assert
            verify(routeRegistry).updateRoute(any(RouteDefinition.class));
        }

        @Test
        @DisplayName("Should remove route when collection is deleted")
        void shouldRemoveRouteWhenCollectionDeleted() {
            // Arrange
            CollectionChangedPayload payload = new CollectionChangedPayload();
            payload.setId("collection-1");
            payload.setName("users");
            payload.setChangeType(ChangeType.DELETED);

            ConfigEvent<CollectionChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleCollectionChanged(event);

            // Assert
            verify(routeRegistry).removeRoute("collection-1");
            verify(routeRegistry, never()).updateRoute(any());
        }

        @Test
        @DisplayName("Should handle null payload gracefully")
        void shouldHandleNullPayloadGracefully() {
            // Arrange
            ConfigEvent<CollectionChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                null
            );

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> listener.handleCollectionChanged(event));
            verify(routeRegistry, never()).updateRoute(any());
            verify(routeRegistry, never()).removeRoute(any());
        }
    }

    @Nested
    @DisplayName("Worker Assignment Changed Event Tests")
    class WorkerAssignmentChangedTests {

        @Test
        @DisplayName("Should add route when collection is assigned to worker")
        void shouldAddRouteWhenCollectionAssignedToWorker() {
            // Arrange
            Map<String, Object> payload = new HashMap<>();
            payload.put("workerId", "worker-1");
            payload.put("collectionId", "collection-1");
            payload.put("workerBaseUrl", "http://worker-1:8080");
            payload.put("collectionName", "accounts");
            payload.put("changeType", "CREATED");

            ConfigEvent<Object> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "emf.worker.assignment.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleWorkerAssignmentChanged(event);

            // Assert
            ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
            verify(routeRegistry).updateRoute(routeCaptor.capture());

            RouteDefinition capturedRoute = routeCaptor.getValue();
            assertEquals("collection-1", capturedRoute.getId());
            assertEquals("/api/accounts/**", capturedRoute.getPath());
            assertEquals("http://worker-1:8080", capturedRoute.getBackendUrl());
            assertEquals("accounts", capturedRoute.getCollectionName());
        }

        @Test
        @DisplayName("Should remove route when collection is unassigned from worker")
        void shouldRemoveRouteWhenCollectionUnassigned() {
            // Arrange
            Map<String, Object> payload = new HashMap<>();
            payload.put("workerId", "worker-1");
            payload.put("collectionId", "collection-1");
            payload.put("collectionName", "accounts");
            payload.put("changeType", "DELETED");

            ConfigEvent<Object> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "emf.worker.assignment.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleWorkerAssignmentChanged(event);

            // Assert
            verify(routeRegistry).removeRoute("collection-1");
            verify(routeRegistry, never()).updateRoute(any());
        }

        @Test
        @DisplayName("Should handle null payload gracefully")
        void shouldHandleNullPayloadGracefully() {
            // Arrange
            ConfigEvent<Object> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "emf.worker.assignment.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                null
            );

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> listener.handleWorkerAssignmentChanged(event));
            verify(routeRegistry, never()).updateRoute(any());
            verify(routeRegistry, never()).removeRoute(any());
        }

        @Test
        @DisplayName("Should handle missing required fields gracefully")
        void shouldHandleMissingRequiredFieldsGracefully() {
            // Arrange - missing workerBaseUrl and collectionName
            Map<String, Object> payload = new HashMap<>();
            payload.put("workerId", "worker-1");
            payload.put("collectionId", "collection-1");
            payload.put("changeType", "CREATED");
            // workerBaseUrl and collectionName are missing

            ConfigEvent<Object> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "emf.worker.assignment.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleWorkerAssignmentChanged(event);

            // Assert - should not add route when required fields are missing
            verify(routeRegistry, never()).updateRoute(any());
        }
    }
}
