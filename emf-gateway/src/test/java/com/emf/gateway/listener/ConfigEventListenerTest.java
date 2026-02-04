package com.emf.gateway.listener;

import com.emf.gateway.authz.AuthzConfig;
import com.emf.gateway.authz.AuthzConfigCache;
import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import com.emf.runtime.event.ConfigEvent;
import com.emf.runtime.event.ServiceChangedPayload;
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
import java.util.List;
import java.util.Optional;
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
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ConfigEventListener(routeRegistry, authzConfigCache, objectMapper);
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
            payload.setServiceId("service-1");
            payload.setChangeType(ChangeType.CREATED);
            
            ConfigEvent<CollectionChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );
            
            // Need to cache service URL first
            ServiceChangedPayload servicePayload = new ServiceChangedPayload();
            servicePayload.setServiceId("service-1");
            servicePayload.setServiceName("User Service");
            servicePayload.setBasePath("http://user-service:8080");
            servicePayload.setChangeType(ChangeType.CREATED);
            
            ConfigEvent<ServiceChangedPayload> serviceEvent = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.service.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                servicePayload
            );
            
            // Act
            listener.handleServiceChanged(serviceEvent);
            listener.handleCollectionChanged(event);
            
            // Assert
            ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
            verify(routeRegistry).updateRoute(routeCaptor.capture());
            
            RouteDefinition capturedRoute = routeCaptor.getValue();
            assertEquals("collection-1", capturedRoute.getId());
            assertEquals("users", capturedRoute.getCollectionName());
            assertEquals("service-1", capturedRoute.getServiceId());
            assertEquals("/api/users/**", capturedRoute.getPath());
            assertEquals("http://user-service:8080", capturedRoute.getBackendUrl());
        }
        
        @Test
        @DisplayName("Should update route when collection is updated")
        void shouldUpdateRouteWhenCollectionUpdated() {
            // Arrange
            CollectionChangedPayload payload = new CollectionChangedPayload();
            payload.setId("collection-1");
            payload.setName("users");
            payload.setServiceId("service-1");
            payload.setChangeType(ChangeType.UPDATED);
            
            ConfigEvent<CollectionChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );
            
            // Cache service URL
            ServiceChangedPayload servicePayload = new ServiceChangedPayload();
            servicePayload.setServiceId("service-1");
            servicePayload.setBasePath("http://user-service:8080");
            servicePayload.setChangeType(ChangeType.CREATED);
            
            ConfigEvent<ServiceChangedPayload> serviceEvent = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.service.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                servicePayload
            );
            
            // Act
            listener.handleServiceChanged(serviceEvent);
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
        
        @Test
        @DisplayName("Should handle missing service URL gracefully")
        void shouldHandleMissingServiceUrlGracefully() {
            // Arrange
            CollectionChangedPayload payload = new CollectionChangedPayload();
            payload.setId("collection-1");
            payload.setName("users");
            payload.setServiceId("non-existent-service");
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
            
            // Assert - should not add route when service URL is missing
            verify(routeRegistry, never()).updateRoute(any());
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
    @DisplayName("Service Changed Event Tests")
    class ServiceChangedTests {
        
        @Test
        @DisplayName("Should cache service URL when service is created")
        void shouldCacheServiceUrlWhenServiceCreated() {
            // Arrange
            ServiceChangedPayload payload = new ServiceChangedPayload();
            payload.setServiceId("service-1");
            payload.setServiceName("User Service");
            payload.setBasePath("http://user-service:8080");
            payload.setChangeType(ChangeType.CREATED);
            
            ConfigEvent<ServiceChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.service.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );
            
            // Act
            listener.handleServiceChanged(event);
            
            // Assert - verify service URL is cached by creating a collection
            CollectionChangedPayload collectionPayload = new CollectionChangedPayload();
            collectionPayload.setId("collection-1");
            collectionPayload.setName("users");
            collectionPayload.setServiceId("service-1");
            collectionPayload.setChangeType(ChangeType.CREATED);
            
            ConfigEvent<CollectionChangedPayload> collectionEvent = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.collection.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                collectionPayload
            );
            
            listener.handleCollectionChanged(collectionEvent);
            
            // Should successfully create route with cached service URL
            verify(routeRegistry).updateRoute(any(RouteDefinition.class));
        }
        
        @Test
        @DisplayName("Should remove all routes when service is deleted")
        void shouldRemoveAllRoutesWhenServiceDeleted() {
            // Arrange
            // First create some routes for the service
            RouteDefinition route1 = new RouteDefinition("route-1", "service-1", "/api/users/**", 
                                                         "http://service:8080", "users");
            RouteDefinition route2 = new RouteDefinition("route-2", "service-1", "/api/posts/**", 
                                                         "http://service:8080", "posts");
            RouteDefinition route3 = new RouteDefinition("route-3", "service-2", "/api/comments/**", 
                                                         "http://other:8080", "comments");
            
            when(routeRegistry.getAllRoutes()).thenReturn(List.of(route1, route2, route3));
            
            ServiceChangedPayload payload = new ServiceChangedPayload();
            payload.setServiceId("service-1");
            payload.setServiceName("User Service");
            payload.setChangeType(ChangeType.DELETED);
            
            ConfigEvent<ServiceChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.service.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                payload
            );
            
            // Act
            listener.handleServiceChanged(event);
            
            // Assert - should remove only routes for service-1
            verify(routeRegistry).removeRoute("route-1");
            verify(routeRegistry).removeRoute("route-2");
            verify(routeRegistry, never()).removeRoute("route-3");
        }
        
        @Test
        @DisplayName("Should handle null payload gracefully")
        void shouldHandleNullPayloadGracefully() {
            // Arrange
            ConfigEvent<ServiceChangedPayload> event = new ConfigEvent<>(
                UUID.randomUUID().toString(),
                "config.service.changed",
                UUID.randomUUID().toString(),
                Instant.now(),
                null
            );
            
            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> listener.handleServiceChanged(event));
        }
    }
}
