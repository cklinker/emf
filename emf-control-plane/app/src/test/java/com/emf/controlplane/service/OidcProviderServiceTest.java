package com.emf.controlplane.service;

import com.emf.controlplane.dto.AddOidcProviderRequest;
import com.emf.controlplane.dto.UpdateOidcProviderRequest;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.OidcProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OidcProviderService.
 * Tests CRUD operations, validation, and error handling.
 * 
 * Requirements tested:
 * - 4.1: Return list of configured OIDC providers
 * - 4.2: Add OIDC provider with valid configuration
 * - 4.4: Update OIDC provider and persist changes
 * - 4.5: Delete OIDC provider by marking as inactive
 */
@ExtendWith(MockitoExtension.class)
class OidcProviderServiceTest {

    @Mock
    private OidcProviderRepository providerRepository;

    private OidcProviderService oidcProviderService;

    @BeforeEach
    void setUp() {
        oidcProviderService = new OidcProviderService(providerRepository, null, null);  // ConfigEventPublisher and JwksCache are optional in tests
    }

    @Nested
    @DisplayName("listProviders")
    class ListProvidersTests {

        @Test
        @DisplayName("should return list of active providers ordered by name")
        void shouldReturnActiveProvidersOrderedByName() {
            // Given
            OidcProvider provider1 = createTestProvider("id-1", "Auth0", "https://auth0.example.com");
            OidcProvider provider2 = createTestProvider("id-2", "Okta", "https://okta.example.com");
            
            when(providerRepository.findByActiveTrueOrderByNameAsc())
                    .thenReturn(List.of(provider1, provider2));

            // When
            List<OidcProvider> result = oidcProviderService.listProviders();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Auth0");
            assertThat(result.get(1).getName()).isEqualTo("Okta");
            verify(providerRepository).findByActiveTrueOrderByNameAsc();
        }

        @Test
        @DisplayName("should return empty list when no active providers exist")
        void shouldReturnEmptyListWhenNoProviders() {
            // Given
            when(providerRepository.findByActiveTrueOrderByNameAsc())
                    .thenReturn(Collections.emptyList());

            // When
            List<OidcProvider> result = oidcProviderService.listProviders();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("addProvider")
    class AddProviderTests {

        @Test
        @DisplayName("should create provider with valid configuration")
        void shouldCreateProviderWithValidConfiguration() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Auth0",
                    "https://auth0.example.com",
                    "https://auth0.example.com/.well-known/jwks.json"
            );
            
            when(providerRepository.existsByName("Auth0")).thenReturn(false);
            when(providerRepository.existsByIssuer("https://auth0.example.com")).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> {
                OidcProvider p = invocation.getArgument(0);
                return p;
            });

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("Auth0");
            assertThat(result.getIssuer()).isEqualTo("https://auth0.example.com");
            assertThat(result.getJwksUri()).isEqualTo("https://auth0.example.com/.well-known/jwks.json");
            assertThat(result.isActive()).isTrue();
            
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should create provider with optional fields")
        void shouldCreateProviderWithOptionalFields() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Auth0",
                    "https://auth0.example.com",
                    "https://auth0.example.com/.well-known/jwks.json",
                    "client-123",
                    "api://my-api"
            );
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result.getClientId()).isEqualTo("client-123");
            assertThat(result.getAudience()).isEqualTo("api://my-api");
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when name already exists")
        void shouldThrowExceptionWhenNameExists() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Existing Provider",
                    "https://new.example.com",
                    "https://new.example.com/.well-known/jwks.json"
            );
            
            when(providerRepository.existsByName("Existing Provider")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("OidcProvider")
                    .hasMessageContaining("name")
                    .hasMessageContaining("Existing Provider");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when issuer already exists")
        void shouldThrowExceptionWhenIssuerExists() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "New Provider",
                    "https://existing.example.com",
                    "https://existing.example.com/.well-known/jwks.json"
            );
            
            when(providerRepository.existsByName("New Provider")).thenReturn(false);
            when(providerRepository.existsByIssuer("https://existing.example.com")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("OidcProvider")
                    .hasMessageContaining("issuer");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException for invalid issuer URL")
        void shouldThrowExceptionForInvalidIssuerUrl() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "not-a-valid-url",
                    "https://valid.example.com/.well-known/jwks.json"
            );

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("issuer");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ValidationException for invalid JWKS URI")
        void shouldThrowExceptionForInvalidJwksUri() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://valid.example.com",
                    "not-a-valid-url"
            );

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("jwksUri");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should accept http URLs for development environments")
        void shouldAcceptHttpUrls() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Local Provider",
                    "http://localhost:8080",
                    "http://localhost:8080/.well-known/jwks.json"
            );
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result.getIssuer()).isEqualTo("http://localhost:8080");
            verify(providerRepository).save(any(OidcProvider.class));
        }
    }

    @Nested
    @DisplayName("updateProvider")
    class UpdateProviderTests {

        @Test
        @DisplayName("should update provider name")
        void shouldUpdateProviderName() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Old Name", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("New Name");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.existsByName("New Name")).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should update provider issuer and invalidate cache")
        void shouldUpdateProviderIssuer() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://old.example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setIssuer("https://new.example.com");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.existsByIssuer("https://new.example.com")).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getIssuer()).isEqualTo("https://new.example.com");
        }

        @Test
        @DisplayName("should update provider JWKS URI")
        void shouldUpdateProviderJwksUri() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://example.com");
            existingProvider.setJwksUri("https://example.com/old-jwks");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setJwksUri("https://example.com/new-jwks");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getJwksUri()).isEqualTo("https://example.com/new-jwks");
        }

        @Test
        @DisplayName("should update multiple fields at once")
        void shouldUpdateMultipleFields() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Old Name", "https://old.example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest("New Name", "https://new.example.com", "https://new.example.com/jwks");
            request.setClientId("new-client-id");
            request.setAudience("new-audience");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.existsByName("New Name")).thenReturn(false);
            when(providerRepository.existsByIssuer("https://new.example.com")).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getIssuer()).isEqualTo("https://new.example.com");
            assertThat(result.getJwksUri()).isEqualTo("https://new.example.com/jwks");
            assertThat(result.getClientId()).isEqualTo("new-client-id");
            assertThat(result.getAudience()).isEqualTo("new-audience");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when provider not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String providerId = "nonexistent-id";
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("New Name");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.updateProvider(providerId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("OidcProvider")
                    .hasMessageContaining(providerId);
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new name already exists")
        void shouldThrowExceptionWhenNewNameExists() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Old Name", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("Existing Name");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.existsByName("Existing Name")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.updateProvider(providerId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Existing Name");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when new issuer already exists")
        void shouldThrowExceptionWhenNewIssuerExists() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://old.example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setIssuer("https://existing.example.com");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.existsByIssuer("https://existing.example.com")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.updateProvider(providerId, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("issuer");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow update with same name")
        void shouldAllowUpdateWithSameName() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Same Name", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setName("Same Name");
            request.setClientId("updated-client-id");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getName()).isEqualTo("Same Name");
            assertThat(result.getClientId()).isEqualTo("updated-client-id");
            // Should not check for duplicate since name is unchanged
            verify(providerRepository, never()).existsByName(anyString());
        }

        @Test
        @DisplayName("should throw ValidationException for invalid issuer URL on update")
        void shouldThrowExceptionForInvalidIssuerOnUpdate() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setIssuer("not-a-valid-url");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.updateProvider(providerId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("issuer");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should only update provided fields")
        void shouldOnlyUpdateProvidedFields() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Original Name", "https://original.example.com");
            existingProvider.setJwksUri("https://original.example.com/jwks");
            existingProvider.setClientId("original-client");
            existingProvider.setAudience("original-audience");
            
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setClientId("new-client");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getName()).isEqualTo("Original Name");
            assertThat(result.getIssuer()).isEqualTo("https://original.example.com");
            assertThat(result.getJwksUri()).isEqualTo("https://original.example.com/jwks");
            assertThat(result.getClientId()).isEqualTo("new-client");
            assertThat(result.getAudience()).isEqualTo("original-audience");
        }
    }

    @Nested
    @DisplayName("deleteProvider")
    class DeleteProviderTests {

        @Test
        @DisplayName("should soft delete provider by marking as inactive")
        void shouldSoftDeleteProvider() {
            // Given
            String providerId = "test-id";
            OidcProvider provider = createTestProvider(providerId, "Provider", "https://example.com");
            provider.setActive(true);
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(provider));
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            oidcProviderService.deleteProvider(providerId);

            // Then
            ArgumentCaptor<OidcProvider> captor = ArgumentCaptor.forClass(OidcProvider.class);
            verify(providerRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when provider not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String providerId = "nonexistent-id";
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.deleteProvider(providerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("OidcProvider")
                    .hasMessageContaining(providerId);
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when provider already deleted")
        void shouldThrowExceptionWhenAlreadyDeleted() {
            // Given
            String providerId = "deleted-id";
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.deleteProvider(providerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getProvider")
    class GetProviderTests {

        @Test
        @DisplayName("should return provider when found and active")
        void shouldReturnProviderWhenFoundAndActive() {
            // Given
            String providerId = "test-id";
            OidcProvider provider = createTestProvider(providerId, "Provider", "https://example.com");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(provider));

            // When
            OidcProvider result = oidcProviderService.getProvider(providerId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(providerId);
            assertThat(result.getName()).isEqualTo("Provider");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when provider not found")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            String providerId = "nonexistent-id";
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.getProvider(providerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("OidcProvider")
                    .hasMessageContaining(providerId);
        }
    }

    @Nested
    @DisplayName("getProviderByIssuer")
    class GetProviderByIssuerTests {

        @Test
        @DisplayName("should return provider when found by issuer")
        void shouldReturnProviderWhenFoundByIssuer() {
            // Given
            String issuer = "https://example.com";
            OidcProvider provider = createTestProvider("test-id", "Provider", issuer);
            
            when(providerRepository.findByIssuerAndActiveTrue(issuer)).thenReturn(Optional.of(provider));

            // When
            OidcProvider result = oidcProviderService.getProviderByIssuer(issuer);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIssuer()).isEqualTo(issuer);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when issuer not found")
        void shouldThrowExceptionWhenIssuerNotFound() {
            // Given
            String issuer = "https://nonexistent.example.com";
            
            when(providerRepository.findByIssuerAndActiveTrue(issuer)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.getProviderByIssuer(issuer))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("issuer");
        }
    }

    @Nested
    @DisplayName("Claim Path Validation")
    class ClaimPathValidationTests {

        @Test
        @DisplayName("should accept valid simple claim path")
        void shouldAcceptValidSimpleClaimPath() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("roles");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept nested claim path with dots")
        void shouldAcceptNestedClaimPathWithDots() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("realm_access.roles");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept claim path with underscores")
        void shouldAcceptClaimPathWithUnderscores() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setEmailClaim("user_email");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept complex nested claim path")
        void shouldAcceptComplexNestedClaimPath() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("resource_access.my_client.roles");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept null claim path")
        void shouldAcceptNullClaimPath() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim(null);
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept empty claim path")
        void shouldAcceptEmptyClaimPath() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should reject claim path exceeding 200 characters")
        void shouldRejectClaimPathExceeding200Characters() {
            // Given
            String longClaimPath = "a".repeat(201);
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim(longClaimPath);

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesClaim")
                    .hasMessageContaining("200 characters");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should accept claim path with exactly 200 characters")
        void shouldAcceptClaimPathWithExactly200Characters() {
            // Given
            String claimPath = "a".repeat(200);
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim(claimPath);
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should reject claim path with special characters")
        void shouldRejectClaimPathWithSpecialCharacters() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("roles@admin");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesClaim")
                    .hasMessageContaining("letters, numbers, dots, and underscores");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject claim path with spaces")
        void shouldRejectClaimPathWithSpaces() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("realm access.roles");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesClaim")
                    .hasMessageContaining("letters, numbers, dots, and underscores");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject claim path with hyphens")
        void shouldRejectClaimPathWithHyphens() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("realm-access.roles");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesClaim")
                    .hasMessageContaining("letters, numbers, dots, and underscores");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should validate all claim path fields")
        void shouldValidateAllClaimPathFields() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("roles");
            request.setEmailClaim("user.email");
            request.setUsernameClaim("user_name");
            request.setNameClaim("display.name");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should reject invalid emailClaim")
        void shouldRejectInvalidEmailClaim() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setEmailClaim("email@invalid");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("emailClaim");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should accept whitespace-only claim path as empty")
        void shouldAcceptWhitespaceOnlyClaimPath() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesClaim("   ");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }
    }

    @Nested
    @DisplayName("Roles Mapping JSON Validation")
    class RolesMappingValidationTests {

        @Test
        @DisplayName("should accept valid JSON object for roles mapping")
        void shouldAcceptValidJsonObject() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("{\"external-admin\": \"ADMIN\", \"external-user\": \"USER\"}");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRolesMapping()).isEqualTo("{\"external-admin\": \"ADMIN\", \"external-user\": \"USER\"}");
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept empty JSON object")
        void shouldAcceptEmptyJsonObject() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("{}");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept null roles mapping")
        void shouldAcceptNullRolesMapping() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping(null);
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept empty string roles mapping")
        void shouldAcceptEmptyStringRolesMapping() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept whitespace-only roles mapping")
        void shouldAcceptWhitespaceOnlyRolesMapping() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("   ");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should accept JSON array")
        void shouldAcceptJsonArray() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("[\"ADMIN\", \"USER\"]");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should reject invalid JSON with missing closing brace")
        void shouldRejectInvalidJsonMissingClosingBrace() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("{\"admin\": \"ADMIN\"");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesMapping")
                    .hasMessageContaining("Invalid JSON format");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject invalid JSON with missing quotes")
        void shouldRejectInvalidJsonMissingQuotes() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("{admin: ADMIN}");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesMapping")
                    .hasMessageContaining("Invalid JSON format");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject invalid JSON with trailing comma")
        void shouldRejectInvalidJsonTrailingComma() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("{\"admin\": \"ADMIN\",}");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesMapping")
                    .hasMessageContaining("Invalid JSON format");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject plain text as invalid JSON")
        void shouldRejectPlainText() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("not valid json");

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesMapping")
                    .hasMessageContaining("Invalid JSON format");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should validate roles mapping on update")
        void shouldValidateRolesMappingOnUpdate() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setRolesMapping("{\"keycloak-admin\": \"ADMIN\"}");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getRolesMapping()).isEqualTo("{\"keycloak-admin\": \"ADMIN\"}");
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should reject invalid roles mapping on update")
        void shouldRejectInvalidRolesMappingOnUpdate() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setRolesMapping("invalid json");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.updateProvider(providerId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesMapping")
                    .hasMessageContaining("Invalid JSON format");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should update all claim fields")
        void shouldUpdateAllClaimFields() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setRolesClaim("realm_access.roles");
            request.setRolesMapping("{\"keycloak-admin\": \"ADMIN\", \"keycloak-user\": \"USER\"}");
            request.setEmailClaim("user_email");
            request.setUsernameClaim("preferred_username");
            request.setNameClaim("full_name");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.updateProvider(providerId, request);

            // Then
            assertThat(result.getRolesClaim()).isEqualTo("realm_access.roles");
            assertThat(result.getRolesMapping()).isEqualTo("{\"keycloak-admin\": \"ADMIN\", \"keycloak-user\": \"USER\"}");
            assertThat(result.getEmailClaim()).isEqualTo("user_email");
            assertThat(result.getUsernameClaim()).isEqualTo("preferred_username");
            assertThat(result.getNameClaim()).isEqualTo("full_name");
            verify(providerRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should validate claim paths on update")
        void shouldValidateClaimPathsOnUpdate() {
            // Given
            String providerId = "test-id";
            OidcProvider existingProvider = createTestProvider(providerId, "Provider", "https://example.com");
            UpdateOidcProviderRequest request = new UpdateOidcProviderRequest();
            request.setRolesClaim("invalid@claim");
            
            when(providerRepository.findByIdAndActiveTrue(providerId)).thenReturn(Optional.of(existingProvider));

            // When/Then
            assertThatThrownBy(() -> oidcProviderService.updateProvider(providerId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("rolesClaim");
            
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should accept complex nested JSON structure")
        void shouldAcceptComplexNestedJson() {
            // Given
            AddOidcProviderRequest request = new AddOidcProviderRequest(
                    "Provider",
                    "https://example.com",
                    "https://example.com/.well-known/jwks.json"
            );
            request.setRolesMapping("{\"roles\": {\"admin\": \"ADMIN\", \"user\": \"USER\"}, \"groups\": [\"group1\"]}");
            
            when(providerRepository.existsByName(anyString())).thenReturn(false);
            when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
            when(providerRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OidcProvider result = oidcProviderService.addProvider(request);

            // Then
            assertThat(result).isNotNull();
            verify(providerRepository).save(any(OidcProvider.class));
        }
    }

    // Helper method to create test providers
    private OidcProvider createTestProvider(String id, String name, String issuer) {
        OidcProvider provider = new OidcProvider(name, issuer, issuer + "/.well-known/jwks.json");
        provider.setId(id);
        provider.setActive(true);
        return provider;
    }
}
