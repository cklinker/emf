package com.emf.gateway.listener;

import com.emf.gateway.authz.AuthzConfig;
import com.emf.gateway.authz.AuthzConfigCache;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.AuthzChangedPayload;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private AuthzConfigCache authzConfigCache;

    private ObjectMapper objectMapper;
    private ConfigEventListener listener;

    private static final String WORKER_SERVICE_URL = "http://emf-worker:80";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ConfigEventListener(routeRegistry, authzConfigCache, objectMapper, event -> {}, WORKER_SERVICE_URL);
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
    @DisplayName("Authorization Changed Event Tests")
    class AuthzChangedTests {

        @Test
        @DisplayName("Should update authz config when authorization changes")
        void shouldUpdateAuthzConfigWhenAuthorizationChanges() {
            // Arrange
            AuthzChangedPayload payload = new AuthzChangedPayload();
            payload.setCollectionId("collection-1");
            payload.setCollectionName("users");

            // Add route policies
            List<AuthzChangedPayload.RoutePolicyPayload> routePolicies = new ArrayList<>();
            AuthzChangedPayload.RoutePolicyPayload routePolicy = new AuthzChangedPayload.RoutePolicyPayload();
            routePolicy.setId("rp-1");
            routePolicy.setOperation("POST");
            routePolicy.setPolicyId("policy-1");
            routePolicy.setPolicyRules("{\"roles\": [\"ADMIN\"]}");
            routePolicies.add(routePolicy);
            payload.setRoutePolicies(routePolicies);

            // Add field policies
            List<AuthzChangedPayload.FieldPolicyPayload> fieldPolicies = new ArrayList<>();
            AuthzChangedPayload.FieldPolicyPayload fieldPolicy = new AuthzChangedPayload.FieldPolicyPayload();
            fieldPolicy.setId("fp-1");
            fieldPolicy.setFieldName("email");
            fieldPolicy.setPolicyId("policy-1");
            fieldPolicy.setPolicyRules("{\"roles\": [\"ADMIN\"]}");
            fieldPolicies.add(fieldPolicy);
            payload.setFieldPolicies(fieldPolicies);

            ConfigEvent<AuthzChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.authz.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleAuthzChanged(event);

            // Assert
            ArgumentCaptor<AuthzConfig> configCaptor = ArgumentCaptor.forClass(AuthzConfig.class);
            verify(authzConfigCache).updateConfig(eq("collection-1"), configCaptor.capture());

            AuthzConfig capturedConfig = configCaptor.getValue();
            assertEquals("collection-1", capturedConfig.getCollectionId());
            assertEquals(1, capturedConfig.getRoutePolicies().size());
            assertEquals(1, capturedConfig.getFieldPolicies().size());

            // Verify route policy
            assertEquals("POST", capturedConfig.getRoutePolicies().get(0).getMethod());
            assertEquals("policy-1", capturedConfig.getRoutePolicies().get(0).getPolicyId());
            assertTrue(capturedConfig.getRoutePolicies().get(0).getRoles().contains("ADMIN"));

            // Verify field policy
            assertEquals("email", capturedConfig.getFieldPolicies().get(0).getFieldName());
            assertEquals("policy-1", capturedConfig.getFieldPolicies().get(0).getPolicyId());
            assertTrue(capturedConfig.getFieldPolicies().get(0).getRoles().contains("ADMIN"));
        }

        @Test
        @DisplayName("Should handle empty policies")
        void shouldHandleEmptyPolicies() {
            // Arrange
            AuthzChangedPayload payload = new AuthzChangedPayload();
            payload.setCollectionId("collection-1");
            payload.setCollectionName("users");
            payload.setRoutePolicies(new ArrayList<>());
            payload.setFieldPolicies(new ArrayList<>());

            ConfigEvent<AuthzChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.authz.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act
            listener.handleAuthzChanged(event);

            // Assert
            ArgumentCaptor<AuthzConfig> configCaptor = ArgumentCaptor.forClass(AuthzConfig.class);
            verify(authzConfigCache).updateConfig(eq("collection-1"), configCaptor.capture());

            AuthzConfig capturedConfig = configCaptor.getValue();
            assertEquals(0, capturedConfig.getRoutePolicies().size());
            assertEquals(0, capturedConfig.getFieldPolicies().size());
        }

        @Test
        @DisplayName("Should handle null payload gracefully")
        void shouldHandleNullPayloadGracefully() {
            // Arrange
            ConfigEvent<AuthzChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.authz.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                null
            );

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> listener.handleAuthzChanged(event));
            verify(authzConfigCache, never()).updateConfig(any(), any());
        }

        @Test
        @DisplayName("Should handle malformed policy rules JSON gracefully")
        void shouldHandleMalformedPolicyRulesGracefully() {
            // Arrange
            AuthzChangedPayload payload = new AuthzChangedPayload();
            payload.setCollectionId("collection-1");
            payload.setCollectionName("users");

            List<AuthzChangedPayload.RoutePolicyPayload> routePolicies = new ArrayList<>();
            AuthzChangedPayload.RoutePolicyPayload routePolicy = new AuthzChangedPayload.RoutePolicyPayload();
            routePolicy.setId("rp-1");
            routePolicy.setOperation("POST");
            routePolicy.setPolicyId("policy-1");
            routePolicy.setPolicyRules("invalid json");
            routePolicies.add(routePolicy);
            payload.setRoutePolicies(routePolicies);

            ConfigEvent<AuthzChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.authz.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> listener.handleAuthzChanged(event));

            // Should still update cache with empty roles
            verify(authzConfigCache).updateConfig(eq("collection-1"), any(AuthzConfig.class));
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
