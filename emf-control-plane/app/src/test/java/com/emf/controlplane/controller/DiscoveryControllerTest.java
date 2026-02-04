package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AuthorizationHintsDto;
import com.emf.controlplane.dto.FieldAuthorizationHintsDto;
import com.emf.controlplane.dto.FieldMetadataDto;
import com.emf.controlplane.dto.ResourceDiscoveryDto;
import com.emf.controlplane.dto.ResourceMetadataDto;
import com.emf.controlplane.service.DiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DiscoveryController.
 * Tests the REST endpoint for resource discovery.
 * 
 * Requirements validated:
 * - 8.1: Return all active collections with their schemas via GET /api/_meta/resources
 * - 8.2: Include field definitions, types, and constraints
 * - 8.3: Include available operations
 * - 8.4: Include authorization hints
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryControllerTest {

    @Mock
    private DiscoveryService discoveryService;

    private DiscoveryController discoveryController;

    @BeforeEach
    void setUp() {
        discoveryController = new DiscoveryController(discoveryService);
    }

    @Nested
    @DisplayName("GET /api/_meta/resources")
    class DiscoverResourcesTests {

        @Test
        @DisplayName("should return empty list when no resources exist")
        void shouldReturnEmptyListWhenNoResources() {
            // Given
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(Collections.emptyList()));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getResources()).isEmpty();
        }

        @Test
        @DisplayName("should return all active resources with metadata")
        void shouldReturnAllActiveResources() {
            // Given
            ResourceMetadataDto usersResource = new ResourceMetadataDto(
                    "col-1",
                    "Users",
                    "User collection",
                    1,
                    Collections.emptyList(),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    new AuthorizationHintsDto(Collections.emptyMap())
            );
            
            ResourceMetadataDto productsResource = new ResourceMetadataDto(
                    "col-2",
                    "Products",
                    "Product collection",
                    2,
                    Collections.emptyList(),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    new AuthorizationHintsDto(Collections.emptyMap())
            );
            
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(Arrays.asList(usersResource, productsResource)));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getResources()).hasSize(2);
            
            ResourceMetadataDto users = response.getBody().getResources().get(0);
            assertThat(users.getId()).isEqualTo("col-1");
            assertThat(users.getName()).isEqualTo("Users");
            assertThat(users.getDescription()).isEqualTo("User collection");
            assertThat(users.getCurrentVersion()).isEqualTo(1);
            
            ResourceMetadataDto products = response.getBody().getResources().get(1);
            assertThat(products.getId()).isEqualTo("col-2");
            assertThat(products.getName()).isEqualTo("Products");
        }

        @Test
        @DisplayName("should return resources with field definitions")
        void shouldReturnResourcesWithFields() {
            // Given
            FieldMetadataDto nameField = new FieldMetadataDto(
                    "field-1",
                    "name",
                    "string",
                    true,
                    "User's full name",
                    Map.of("minLength", 1, "maxLength", 100),
                    new FieldAuthorizationHintsDto(Collections.emptyMap())
            );
            
            FieldMetadataDto ageField = new FieldMetadataDto(
                    "field-2",
                    "age",
                    "number",
                    false,
                    "User's age",
                    Map.of("min", 0, "max", 150),
                    new FieldAuthorizationHintsDto(Collections.emptyMap())
            );
            
            ResourceMetadataDto usersResource = new ResourceMetadataDto(
                    "col-1",
                    "Users",
                    "User collection",
                    1,
                    Arrays.asList(nameField, ageField),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    new AuthorizationHintsDto(Collections.emptyMap())
            );
            
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(List.of(usersResource)));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            List<FieldMetadataDto> fields = response.getBody().getResources().get(0).getFields();
            assertThat(fields).hasSize(2);
            
            FieldMetadataDto name = fields.get(0);
            assertThat(name.getId()).isEqualTo("field-1");
            assertThat(name.getName()).isEqualTo("name");
            assertThat(name.getType()).isEqualTo("string");
            assertThat(name.isRequired()).isTrue();
            assertThat(name.getDescription()).isEqualTo("User's full name");
            assertThat(name.getConstraints()).containsEntry("minLength", 1);
            assertThat(name.getConstraints()).containsEntry("maxLength", 100);
            
            FieldMetadataDto age = fields.get(1);
            assertThat(age.getId()).isEqualTo("field-2");
            assertThat(age.getType()).isEqualTo("number");
            assertThat(age.isRequired()).isFalse();
        }

        @Test
        @DisplayName("should return resources with available operations")
        void shouldReturnResourcesWithOperations() {
            // Given
            ResourceMetadataDto usersResource = new ResourceMetadataDto(
                    "col-1",
                    "Users",
                    "User collection",
                    1,
                    Collections.emptyList(),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    new AuthorizationHintsDto(Collections.emptyMap())
            );
            
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(List.of(usersResource)));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            List<String> operations = response.getBody().getResources().get(0).getAvailableOperations();
            assertThat(operations).hasSize(5);
            assertThat(operations).containsExactlyInAnyOrder("CREATE", "READ", "UPDATE", "DELETE", "LIST");
        }

        @Test
        @DisplayName("should return resources with authorization hints")
        void shouldReturnResourcesWithAuthorizationHints() {
            // Given
            AuthorizationHintsDto authHints = new AuthorizationHintsDto(Map.of(
                    "CREATE", List.of("admin-only"),
                    "READ", List.of("authenticated"),
                    "UPDATE", List.of("admin-only"),
                    "DELETE", List.of("admin-only")
            ));
            
            ResourceMetadataDto usersResource = new ResourceMetadataDto(
                    "col-1",
                    "Users",
                    "User collection",
                    1,
                    Collections.emptyList(),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    authHints
            );
            
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(List.of(usersResource)));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            AuthorizationHintsDto hints = response.getBody().getResources().get(0).getAuthorizationHints();
            assertThat(hints.getOperationPolicies()).containsKey("CREATE");
            assertThat(hints.getOperationPolicies().get("CREATE")).contains("admin-only");
            assertThat(hints.getOperationPolicies().get("READ")).contains("authenticated");
            assertThat(hints.getOperationPolicies().get("UPDATE")).contains("admin-only");
            assertThat(hints.getOperationPolicies().get("DELETE")).contains("admin-only");
        }

        @Test
        @DisplayName("should return resources with field-level authorization hints")
        void shouldReturnResourcesWithFieldAuthorizationHints() {
            // Given
            FieldAuthorizationHintsDto fieldAuthHints = new FieldAuthorizationHintsDto(Map.of(
                    "READ", List.of("admin-only"),
                    "WRITE", List.of("admin-only")
            ));
            
            FieldMetadataDto ssnField = new FieldMetadataDto(
                    "field-1",
                    "ssn",
                    "string",
                    false,
                    "Social Security Number",
                    null,
                    fieldAuthHints
            );
            
            ResourceMetadataDto usersResource = new ResourceMetadataDto(
                    "col-1",
                    "Users",
                    "User collection",
                    1,
                    List.of(ssnField),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    new AuthorizationHintsDto(Collections.emptyMap())
            );
            
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(List.of(usersResource)));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            FieldMetadataDto field = response.getBody().getResources().get(0).getFields().get(0);
            FieldAuthorizationHintsDto hints = field.getAuthorizationHints();
            assertThat(hints.getOperationPolicies()).containsKey("READ");
            assertThat(hints.getOperationPolicies().get("READ")).contains("admin-only");
            assertThat(hints.getOperationPolicies().get("WRITE")).contains("admin-only");
        }

        @Test
        @DisplayName("should return resources with empty authorization hints when no policies exist")
        void shouldReturnResourcesWithEmptyAuthHintsWhenNoPolicies() {
            // Given
            ResourceMetadataDto usersResource = new ResourceMetadataDto(
                    "col-1",
                    "Users",
                    "User collection",
                    1,
                    Collections.emptyList(),
                    Arrays.asList("CREATE", "READ", "UPDATE", "DELETE", "LIST"),
                    new AuthorizationHintsDto(Collections.emptyMap())
            );
            
            when(discoveryService.discoverResources())
                    .thenReturn(new ResourceDiscoveryDto(List.of(usersResource)));

            // When
            ResponseEntity<ResourceDiscoveryDto> response = discoveryController.discoverResources();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            AuthorizationHintsDto hints = response.getBody().getResources().get(0).getAuthorizationHints();
            assertThat(hints).isNotNull();
            assertThat(hints.getOperationPolicies()).isEmpty();
        }
    }
}
