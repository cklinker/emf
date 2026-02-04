package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AddOidcProviderRequest;
import com.emf.controlplane.dto.OidcProviderDto;
import com.emf.controlplane.dto.UpdateOidcProviderRequest;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.service.OidcProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OidcProviderController.
 * Tests REST endpoint behavior, response codes, and DTO conversion.
 * 
 * Requirements tested:
 * - 4.1: Return list of configured OIDC providers (GET /control/oidc/providers)
 * - 4.2: Add OIDC provider with valid configuration (POST /control/oidc/providers)
 * - 4.4: Update OIDC provider and persist changes (PUT /control/oidc/providers/{id})
 * - 4.5: Delete OIDC provider by marking as inactive (DELETE /control/oidc/providers/{id})
 */
@ExtendWith(MockitoExtension.class)
class OidcProviderControllerTest {

    @Mock
    private OidcProviderService oidcProviderService;

    private OidcProviderController controller;

    @BeforeEach
    void setUp() {
        controller = new OidcProviderController(oidcProviderService);
    }

    @Nested
    @DisplayName("GET /control/oidc/providers - listProviders")
    class ListProvidersTests {

        @Test
        @DisplayName("should return 200 OK with list of providers")
        void shouldReturnListOfProviders() {
            // Given
            OidcProvider provider1 = createTestProvider("id-1", "Auth0", "https://auth0.example.com");
            OidcProvider provider2 = createTestProvider("id-2", "Okta", "https://okta.example.com");
            
            when(oidcProviderService.listProviders()).thenReturn(List.of(provider1, provider2));

            // When
            ResponseEntity<List<OidcProviderDto>> response = controller.listProviders();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getName()).isEqualTo("Auth0");
            assertThat(response.getBody().get(1).getName()).isEqualTo("Okta");
            verify(oidcProviderService).listProviders();
        }

        @Test
        @DisplayName("should return 200 OK with empty list when no providers exist")
        void shouldReturnEmptyList() {
            // Given
            when(oidcProviderService.listProviders()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<OidcProviderDto>> response = controller.listProviders();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should convert entities to DTOs correctly")
        void shouldConvertEntitiesToDtos() {
            // Given
            OidcProvider provider = createTestProvider("test-id", "Test Provider", "https://test.example.com");
            provider.setJwksUri("https://test.example.com/.well-known/jwks.json");
            provider.setClientId("client-123");
            provider.setAudience("api://test");
            
            when(oidcProviderService.listProviders()).thenReturn(List.of(provider));

            // When
            ResponseEntity<List<OidcProviderDto>> response = controller.listProviders();

            // Then
            OidcProviderDto dto = response.getBody().get(0);
            assertThat(dto.getId()).isEqualTo("test-id");
            assertThat(dto.getName()).isEqualTo("Test Provider");
            assertThat(dto.getIssuer()).isEqualTo("https://test.example.com");
            assertThat(dto.getJwksUri()).isEqualTo("https://test.example.com/.well-known/jwks.json");
            assertThat(dto.getClientId()).isEqualTo("client-123");
            assertThat(dto.getAudience()).isEqualTo("api://test");
            assertThat(dto.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /control/oidc/providers - addProvider")
    class AddProviderTests {

        @Test
        @DisplayName("should return 201 CREATED with created provider")
        void shouldReturnCreatedProvider() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Auth0",
                    "https://auth0.example.com",
                    "https://auth0.example.com/.well-known/jwks.json"
            );
            
            OidcProvider created = createTestProvider("new-id", "Auth0", "https://auth0.example.com");
            created.setJwksUri("https://auth0.example.com/.well-known/jwks.json");
            
            when(oidcProviderService.addProvider(request)).thenReturn(created);

            // When
            ResponseEntity<OidcProviderDto> response = controller.addProvider(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo("new-id");
            assertThat(response.getBody().getName()).isEqualTo("Auth0");
            verify(oidcProviderService).addProvider(request);
        }

        @Test
        @DisplayName("should return 201 CREATED with optional fields")
        void shouldReturnCreatedProviderWithOptionalFields() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Auth0",
                    "https://auth0.example.com",
                    "https://auth0.example.com/.well-known/jwks.json",
                    "client-123",
                    "api://my-api"
            );
            
            OidcProvider created = createTestProvider("new-id", "Auth0", "https://auth0.example.com");
            created.setJwksUri("https://auth0.example.com/.well-known/jwks.json");
            created.setClientId("client-123");
            created.setAudience("api://my-api");
            
            when(oidcProviderService.addProvider(request)).thenReturn(created);

            // When
            ResponseEntity<OidcProviderDto> response = controller.addProvider(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().getClientId()).isEqualTo("client-123");
            assertThat(response.getBody().getAudience()).isEqualTo("api://my-api");
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException from service")
        void shouldPropagateDuplicateResourceException() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Existing",
                    "https://existing.example.com",
                    "https://existing.example.com/.well-known/jwks.json"
            );
            
            when(oidcProviderService.addProvider(request))
                    .thenThrow(new DuplicateResourceException("OidcProvider", "name", "Existing"));

            // When/Then
            assertThatThrownBy(() -> controller.addProvider(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should propagate ValidationException from service")
        void shouldPropagateValidationException() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "invalid-url",
                    "https://valid.example.com/.well-known/jwks.json"
            );
            
            when(oidcProviderService.addProvider(request))
                    .thenThrow(new ValidationException("issuer", "Invalid URL format"));

            // When/Then
            assertThatThrownBy(() -> controller.addProvider(request))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("PUT /control/oidc/providers/{id} - updateProvider")
    class UpdateProviderTests {

        @Test
        @DisplayName("should return 200 OK with updated provider")
        void shouldReturnUpdatedProvider() {
            // Given
            String providerId = "test-id";
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("Updated Name");
            
            OidcProvider updated = createTestProvider(providerId, "Updated Name", "https://example.com");
            
            when(oidcProviderService.updateProvider(eq(providerId), eq(request))).thenReturn(updated);

            // When
            ResponseEntity<OidcProviderDto> response = controller.updateProvider(providerId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Updated Name");
            verify(oidcProviderService).updateProvider(providerId, request);
        }

        @Test
        @DisplayName("should return 200 OK when updating multiple fields")
        void shouldReturnUpdatedProviderWithMultipleFields() {
            // Given
            String providerId = "test-id";
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest(
                    "New Name",
                    "https://new.example.com",
                    "https://new.example.com/.well-known/jwks.json"
            );
            request.setClientId("new-client");
            request.setAudience("new-audience");
            
            OidcProvider updated = createTestProvider(providerId, "New Name", "https://new.example.com");
            updated.setJwksUri("https://new.example.com/.well-known/jwks.json");
            updated.setClientId("new-client");
            updated.setAudience("new-audience");
            
            when(oidcProviderService.updateProvider(eq(providerId), eq(request))).thenReturn(updated);

            // When
            ResponseEntity<OidcProviderDto> response = controller.updateProvider(providerId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getName()).isEqualTo("New Name");
            assertThat(response.getBody().getIssuer()).isEqualTo("https://new.example.com");
            assertThat(response.getBody().getClientId()).isEqualTo("new-client");
            assertThat(response.getBody().getAudience()).isEqualTo("new-audience");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException from service")
        void shouldPropagateResourceNotFoundException() {
            // Given
            String providerId = "nonexistent-id";
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("New Name");
            
            when(oidcProviderService.updateProvider(eq(providerId), any()))
                    .thenThrow(new ResourceNotFoundException("OidcProvider", providerId));

            // When/Then
            assertThatThrownBy(() -> controller.updateProvider(providerId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException from service")
        void shouldPropagateDuplicateResourceException() {
            // Given
            String providerId = "test-id";
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("Existing Name");
            
            when(oidcProviderService.updateProvider(eq(providerId), any()))
                    .thenThrow(new DuplicateResourceException("OidcProvider", "name", "Existing Name"));

            // When/Then
            assertThatThrownBy(() -> controller.updateProvider(providerId, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("DELETE /control/oidc/providers/{id} - deleteProvider")
    class DeleteProviderTests {

        @Test
        @DisplayName("should return 204 NO CONTENT on successful deletion")
        void shouldReturnNoContentOnDeletion() {
            // Given
            String providerId = "test-id";
            doNothing().when(oidcProviderService).deleteProvider(providerId);

            // When
            ResponseEntity<Void> response = controller.deleteProvider(providerId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
            verify(oidcProviderService).deleteProvider(providerId);
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException from service")
        void shouldPropagateResourceNotFoundException() {
            // Given
            String providerId = "nonexistent-id";
            doThrow(new ResourceNotFoundException("OidcProvider", providerId))
                    .when(oidcProviderService).deleteProvider(providerId);

            // When/Then
            assertThatThrownBy(() -> controller.deleteProvider(providerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // Helper method to create test providers
    private OidcProvider createTestProvider(String id, String name, String issuer) {
        OidcProvider provider = new OidcProvider(name, issuer, issuer + "/.well-known/jwks.json");
        provider.setId(id);
        provider.setActive(true);
        provider.setCreatedAt(Instant.now());
        provider.setUpdatedAt(Instant.now());
        return provider;
    }
}
