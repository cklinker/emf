package com.emf.controlplane.service;

import com.emf.controlplane.dto.ImportPackageRequest;
import com.emf.controlplane.dto.PackageDto;
import com.emf.controlplane.entity.OidcProvider;
import com.emf.controlplane.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PackageService claim configuration validation during import.
 *
 * Tests task 6.2: Update PackageService import validation
 * - Validate claim configurations during import
 * - Apply defaults for missing claim fields
 *
 * Requirements tested: 9.3, 9.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PackageService Claim Validation Tests")
class PackageServiceClaimValidationTest {

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private FieldRepository fieldRepository;

    @Mock
    private OidcProviderRepository oidcProviderRepository;

    @Mock
    private UiPageRepository uiPageRepository;

    @Mock
    private UiMenuRepository uiMenuRepository;

    private ObjectMapper objectMapper; // Use real instance instead of mock

    @InjectMocks
    private PackageService packageService;

    @BeforeEach
    void setUp() {
        // Initialize real ObjectMapper
        objectMapper = new ObjectMapper();
        // Manually create PackageService with real ObjectMapper
        packageService = new PackageService(
            packageRepository,
            collectionRepository,
            fieldRepository,
            oidcProviderRepository,
            uiPageRepository,
            uiMenuRepository,
            objectMapper
        );
    }

    @Nested
    @DisplayName("Claim Configuration Validation")
    class ClaimConfigurationValidation {

        @Test
        @DisplayName("should reject OIDC provider with invalid roles mapping JSON")
        void shouldRejectInvalidRolesMappingJson() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            provider.setRolesMapping("{invalid json");

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(error ->
                error.contains("test-provider") && error.contains("Invalid JSON format"));
        }

        @Test
        @DisplayName("should reject OIDC provider with claim path exceeding 200 characters")
        void shouldRejectClaimPathTooLong() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            String longPath = "a".repeat(201);
            provider.setRolesClaim(longPath);

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(error ->
                error.contains("test-provider") && error.contains("must not exceed 200 characters"));
        }

        @Test
        @DisplayName("should reject OIDC provider with invalid claim path format")
        void shouldRejectInvalidClaimPathFormat() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            provider.setEmailClaim("email@invalid");

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors).anyMatch(error ->
                error.contains("test-provider") &&
                error.contains("must contain only letters, numbers, dots, and underscores"));
        }

        @Test
        @DisplayName("should accept OIDC provider with valid nested claim paths")
        void shouldAcceptValidNestedClaimPaths() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            provider.setRolesClaim("realm_access.roles");
            provider.setEmailClaim("email");
            provider.setUsernameClaim("preferred_username");
            provider.setNameClaim("name");

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should accept OIDC provider with valid roles mapping JSON")
        void shouldAcceptValidRolesMappingJson() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            provider.setRolesMapping("{\"external-admin\": \"ADMIN\", \"external-user\": \"USER\"}");

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should accept OIDC provider with null claim fields")
        void shouldAcceptNullClaimFields() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            provider.setRolesClaim(null);
            provider.setRolesMapping(null);
            provider.setEmailClaim(null);
            provider.setUsernameClaim(null);
            provider.setNameClaim(null);

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should accept OIDC provider with empty claim fields")
        void shouldAcceptEmptyClaimFields() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto provider = packageData.getOidcProviders().get(0);
            provider.setRolesClaim("");
            provider.setRolesMapping("");
            provider.setEmailClaim("");
            provider.setUsernameClaim("");
            provider.setNameClaim("");

            // When
            List<String> errors = packageService.validatePackage(packageData);

            // Then
            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("Default Value Application")
    class DefaultValueApplication {

        @Test
        @DisplayName("should apply defaults when importing OIDC provider with null claim fields")
        void shouldApplyDefaultsForNullClaimFields() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto providerDto = packageData.getOidcProviders().get(0);
            providerDto.setEmailClaim(null);
            providerDto.setUsernameClaim(null);
            providerDto.setNameClaim(null);

            ImportPackageRequest request = new ImportPackageRequest();
            request.setPackageData(packageData);
            request.setConflictStrategy(ImportPackageRequest.ConflictStrategy.SKIP);

            when(oidcProviderRepository.findByNameAndActiveTrue(any())).thenReturn(Optional.empty());
            when(oidcProviderRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> {
                OidcProvider provider = invocation.getArgument(0);
                // Verify defaults were applied
                assertThat(provider.getEmailClaim()).isEqualTo("email");
                assertThat(provider.getUsernameClaim()).isEqualTo("preferred_username");
                assertThat(provider.getNameClaim()).isEqualTo("name");
                return provider;
            });
            when(packageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            packageService.importPackage(request, false);

            // Then
            verify(oidcProviderRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should apply defaults when importing OIDC provider with empty claim fields")
        void shouldApplyDefaultsForEmptyClaimFields() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto providerDto = packageData.getOidcProviders().get(0);
            providerDto.setEmailClaim("");
            providerDto.setUsernameClaim("");
            providerDto.setNameClaim("");

            ImportPackageRequest request = new ImportPackageRequest();
            request.setPackageData(packageData);
            request.setConflictStrategy(ImportPackageRequest.ConflictStrategy.SKIP);

            when(oidcProviderRepository.findByNameAndActiveTrue(any())).thenReturn(Optional.empty());
            when(oidcProviderRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> {
                OidcProvider provider = invocation.getArgument(0);
                // Verify defaults were applied
                assertThat(provider.getEmailClaim()).isEqualTo("email");
                assertThat(provider.getUsernameClaim()).isEqualTo("preferred_username");
                assertThat(provider.getNameClaim()).isEqualTo("name");
                return provider;
            });
            when(packageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            packageService.importPackage(request, false);

            // Then
            verify(oidcProviderRepository).save(any(OidcProvider.class));
        }

        @Test
        @DisplayName("should preserve custom claim values when importing OIDC provider")
        void shouldPreserveCustomClaimValues() {
            // Given
            PackageDto packageData = createPackageWithOidcProvider();
            PackageDto.PackageOidcProviderDto providerDto = packageData.getOidcProviders().get(0);
            providerDto.setRolesClaim("realm_access.roles");
            providerDto.setEmailClaim("mail");
            providerDto.setUsernameClaim("sub");
            providerDto.setNameClaim("full_name");

            ImportPackageRequest request = new ImportPackageRequest();
            request.setPackageData(packageData);
            request.setConflictStrategy(ImportPackageRequest.ConflictStrategy.SKIP);

            when(oidcProviderRepository.findByNameAndActiveTrue(any())).thenReturn(Optional.empty());
            when(oidcProviderRepository.save(any(OidcProvider.class))).thenAnswer(invocation -> {
                OidcProvider provider = invocation.getArgument(0);
                // Verify custom values were preserved
                assertThat(provider.getRolesClaim()).isEqualTo("realm_access.roles");
                assertThat(provider.getEmailClaim()).isEqualTo("mail");
                assertThat(provider.getUsernameClaim()).isEqualTo("sub");
                assertThat(provider.getNameClaim()).isEqualTo("full_name");
                return provider;
            });
            when(packageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            packageService.importPackage(request, false);

            // Then
            verify(oidcProviderRepository).save(any(OidcProvider.class));
        }
    }

    // Helper methods

    private PackageDto createPackageWithOidcProvider() {
        PackageDto packageData = new PackageDto(
            "test-id",
            "test-package",
            "1.0.0",
            "Test package",
            null
        );

        PackageDto.PackageOidcProviderDto provider = new PackageDto.PackageOidcProviderDto();
        provider.setId("provider-id");
        provider.setName("test-provider");
        provider.setIssuer("https://issuer.example.com");
        provider.setJwksUri("https://issuer.example.com/.well-known/jwks.json");
        provider.setClientId("test-client");

        List<PackageDto.PackageOidcProviderDto> providers = new ArrayList<>();
        providers.add(provider);
        packageData.setOidcProviders(providers);

        // Set empty lists for other types
        packageData.setCollections(new ArrayList<>());
        packageData.setRoles(new ArrayList<>());
        packageData.setPolicies(new ArrayList<>());
        packageData.setUiPages(new ArrayList<>());
        packageData.setUiMenus(new ArrayList<>());

        return packageData;
    }
}
