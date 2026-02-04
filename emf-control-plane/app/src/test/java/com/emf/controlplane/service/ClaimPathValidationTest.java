package com.emf.controlplane.service;

import com.emf.controlplane.dto.AddOidcProviderRequest;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.OidcProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;

/**
 * Standalone test for claim path validation functionality.
 * Tests the validateClaimPath() method added in task 4.1.
 * 
 * Requirements tested: 4.1, 4.5
 */
@ExtendWith(MockitoExtension.class)
class ClaimPathValidationTest {

    @Mock
    private OidcProviderRepository providerRepository;

    private OidcProviderService oidcProviderService;

    @BeforeEach
    void setUp() {
        oidcProviderService = new OidcProviderService(providerRepository, null, null);
        
        // Setup default mocks with lenient stubbing since not all tests use these
        lenient().when(providerRepository.existsByName(anyString())).thenReturn(false);
        lenient().when(providerRepository.existsByIssuer(anyString())).thenReturn(false);
        lenient().when(providerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("should accept valid simple claim path")
    void shouldAcceptValidSimpleClaimPath() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("roles");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept nested claim path with dots")
    void shouldAcceptNestedClaimPathWithDots() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("realm_access.roles");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept claim path with underscores")
    void shouldAcceptClaimPathWithUnderscores() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setEmailClaim("user_email");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept complex nested claim path")
    void shouldAcceptComplexNestedClaimPath() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("resource_access.my_client.roles");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject claim path exceeding 200 characters")
    void shouldRejectClaimPathExceeding200Characters() {
        String longClaimPath = "a".repeat(201);
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim(longClaimPath);

        assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("rolesClaim")
                .hasMessageContaining("200 characters");
    }

    @Test
    @DisplayName("should accept claim path with exactly 200 characters")
    void shouldAcceptClaimPathWithExactly200Characters() {
        String claimPath = "a".repeat(200);
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim(claimPath);

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject claim path with special characters")
    void shouldRejectClaimPathWithSpecialCharacters() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("roles@admin");

        assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("rolesClaim")
                .hasMessageContaining("letters, numbers, dots, and underscores");
    }

    @Test
    @DisplayName("should reject claim path with spaces")
    void shouldRejectClaimPathWithSpaces() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("realm access.roles");

        assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("rolesClaim")
                .hasMessageContaining("letters, numbers, dots, and underscores");
    }

    @Test
    @DisplayName("should reject claim path with hyphens")
    void shouldRejectClaimPathWithHyphens() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("realm-access.roles");

        assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("rolesClaim")
                .hasMessageContaining("letters, numbers, dots, and underscores");
    }

    @Test
    @DisplayName("should accept null claim path")
    void shouldAcceptNullClaimPath() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim(null);

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept empty claim path")
    void shouldAcceptEmptyClaimPath() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should accept whitespace-only claim path")
    void shouldAcceptWhitespaceOnlyClaimPath() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("   ");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should validate all claim path fields")
    void shouldValidateAllClaimPathFields() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setRolesClaim("roles");
        request.setEmailClaim("user.email");
        request.setUsernameClaim("user_name");
        request.setNameClaim("display.name");

        assertThatCode(() -> oidcProviderService.addProvider(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject invalid emailClaim")
    void shouldRejectInvalidEmailClaim() {
        AddOidcProviderRequest request = new AddOidcProviderRequest(
                "Provider",
                "https://example.com",
                "https://example.com/.well-known/jwks.json"
        );
        request.setEmailClaim("email@invalid");

        assertThatThrownBy(() -> oidcProviderService.addProvider(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("emailClaim");
    }
}
