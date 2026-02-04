package com.emf.controlplane.service;

import com.emf.controlplane.dto.AuthorizationConfigDto;
import com.emf.controlplane.dto.CreatePolicyRequest;
import com.emf.controlplane.dto.CreateRoleRequest;
import com.emf.controlplane.dto.SetAuthorizationRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.FieldPolicy;
import com.emf.controlplane.entity.Policy;
import com.emf.controlplane.entity.Role;
import com.emf.controlplane.entity.RoutePolicy;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldPolicyRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.PolicyRepository;
import com.emf.controlplane.repository.RoleRepository;
import com.emf.controlplane.repository.RoutePolicyRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthorizationService.
 * Tests role, policy, route policy, and field policy operations.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RoutePolicyRepository routePolicyRepository;

    @Mock
    private FieldPolicyRepository fieldPolicyRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private FieldRepository fieldRepository;

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService(
                roleRepository,
                policyRepository,
                routePolicyRepository,
                fieldPolicyRepository,
                collectionRepository,
                fieldRepository,
                null  // ConfigEventPublisher is optional in tests
        );
    }

    @Nested
    @DisplayName("listRoles")
    class ListRolesTests {

        @Test
        @DisplayName("should return all roles ordered by name")
        void shouldReturnAllRolesOrderedByName() {
            // Given
            Role role1 = createTestRole("role-1", "Admin");
            Role role2 = createTestRole("role-2", "Editor");
            Role role3 = createTestRole("role-3", "Viewer");

            when(roleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(role1, role2, role3));

            // When
            List<Role> result = authorizationService.listRoles();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getName()).isEqualTo("Admin");
            assertThat(result.get(1).getName()).isEqualTo("Editor");
            assertThat(result.get(2).getName()).isEqualTo("Viewer");
            verify(roleRepository).findAllByOrderByNameAsc();
        }

        @Test
        @DisplayName("should return empty list when no roles exist")
        void shouldReturnEmptyListWhenNoRoles() {
            // Given
            when(roleRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

            // When
            List<Role> result = authorizationService.listRoles();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createRole")
    class CreateRoleTests {

        @Test
        @DisplayName("should create role with generated ID")
        void shouldCreateRoleWithGeneratedId() {
            // Given
            CreateRoleRequest request = new CreateRoleRequest("Admin", "Administrator role");

            when(roleRepository.existsByName("Admin")).thenReturn(false);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = authorizationService.createRole(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("Admin");
            assertThat(result.getDescription()).isEqualTo("Administrator role");

            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when role name already exists")
        void shouldThrowExceptionWhenRoleNameExists() {
            // Given
            CreateRoleRequest request = new CreateRoleRequest("Admin", "Administrator role");

            when(roleRepository.existsByName("Admin")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> authorizationService.createRole(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Role")
                    .hasMessageContaining("name")
                    .hasMessageContaining("Admin");

            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create role with null description")
        void shouldCreateRoleWithNullDescription() {
            // Given
            CreateRoleRequest request = new CreateRoleRequest("Viewer", null);

            when(roleRepository.existsByName("Viewer")).thenReturn(false);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Role result = authorizationService.createRole(request);

            // Then
            assertThat(result.getName()).isEqualTo("Viewer");
            assertThat(result.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("listPolicies")
    class ListPoliciesTests {

        @Test
        @DisplayName("should return all policies ordered by name")
        void shouldReturnAllPoliciesOrderedByName() {
            // Given
            Policy policy1 = createTestPolicy("policy-1", "Admin Policy");
            Policy policy2 = createTestPolicy("policy-2", "Read Only");

            when(policyRepository.findAllByOrderByNameAsc()).thenReturn(List.of(policy1, policy2));

            // When
            List<Policy> result = authorizationService.listPolicies();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Admin Policy");
            assertThat(result.get(1).getName()).isEqualTo("Read Only");
            verify(policyRepository).findAllByOrderByNameAsc();
        }

        @Test
        @DisplayName("should return empty list when no policies exist")
        void shouldReturnEmptyListWhenNoPolicies() {
            // Given
            when(policyRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

            // When
            List<Policy> result = authorizationService.listPolicies();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createPolicy")
    class CreatePolicyTests {

        @Test
        @DisplayName("should create policy with generated ID")
        void shouldCreatePolicyWithGeneratedId() {
            // Given
            String rules = "{\"roles\": [\"ADMIN\"]}";
            CreatePolicyRequest request = new CreatePolicyRequest("Admin Policy", "Full access policy", null, rules);

            when(policyRepository.existsByName("Admin Policy")).thenReturn(false);
            when(policyRepository.save(any(Policy.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Policy result = authorizationService.createPolicy(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("Admin Policy");
            assertThat(result.getDescription()).isEqualTo("Full access policy");
            assertThat(result.getRules()).isEqualTo(rules);

            verify(policyRepository).save(any(Policy.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when policy name already exists")
        void shouldThrowExceptionWhenPolicyNameExists() {
            // Given
            CreatePolicyRequest request = new CreatePolicyRequest("Admin Policy", "Description", null, null);

            when(policyRepository.existsByName("Admin Policy")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> authorizationService.createPolicy(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Policy")
                    .hasMessageContaining("name")
                    .hasMessageContaining("Admin Policy");

            verify(policyRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create policy with null rules")
        void shouldCreatePolicyWithNullRules() {
            // Given
            CreatePolicyRequest request = new CreatePolicyRequest("Simple Policy", "Description", null, null);

            when(policyRepository.existsByName("Simple Policy")).thenReturn(false);
            when(policyRepository.save(any(Policy.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Policy result = authorizationService.createPolicy(request);

            // Then
            assertThat(result.getName()).isEqualTo("Simple Policy");
            assertThat(result.getRules()).isNull();
        }
    }

    @Nested
    @DisplayName("setCollectionAuthorization")
    class SetCollectionAuthorizationTests {

        @Test
        @DisplayName("should set route policies for collection")
        void shouldSetRoutePoliciesForCollection() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Policy policy = createTestPolicy("policy-1", "Read Policy");

            SetAuthorizationRequest.RoutePolicyRequest routePolicyRequest =
                    new SetAuthorizationRequest.RoutePolicyRequest("READ", "policy-1");
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    List.of(routePolicyRequest),
                    Collections.emptyList()
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(policyRepository.findById("policy-1")).thenReturn(Optional.of(policy));
            when(routePolicyRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<RoutePolicy> policies = invocation.getArgument(0);
                return policies;
            });
            when(fieldPolicyRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // When
            AuthorizationConfigDto result = authorizationService.setCollectionAuthorization(collectionId, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCollectionId()).isEqualTo(collectionId);
            assertThat(result.getRoutePolicies()).hasSize(1);
            assertThat(result.getRoutePolicies().get(0).getOperation()).isEqualTo("READ");
            assertThat(result.getFieldPolicies()).isEmpty();

            verify(routePolicyRepository).deleteByCollectionId(collectionId);
            verify(routePolicyRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("should set field policies for collection")
        void shouldSetFieldPoliciesForCollection() {
            // Given
            String collectionId = "collection-1";
            String fieldId = "field-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Field field = createTestField(fieldId, "name", collection);
            Policy policy = createTestPolicy("policy-1", "Read Policy");

            SetAuthorizationRequest.FieldPolicyRequest fieldPolicyRequest =
                    new SetAuthorizationRequest.FieldPolicyRequest(fieldId, "READ", "policy-1");
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    Collections.emptyList(),
                    List.of(fieldPolicyRequest)
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId))
                    .thenReturn(Optional.of(field));
            when(policyRepository.findById("policy-1")).thenReturn(Optional.of(policy));
            when(routePolicyRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
            when(fieldPolicyRepository.saveAll(anyList())).thenAnswer(invocation -> {
                List<FieldPolicy> policies = invocation.getArgument(0);
                return policies;
            });

            // When
            AuthorizationConfigDto result = authorizationService.setCollectionAuthorization(collectionId, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCollectionId()).isEqualTo(collectionId);
            assertThat(result.getRoutePolicies()).isEmpty();
            assertThat(result.getFieldPolicies()).hasSize(1);
            assertThat(result.getFieldPolicies().get(0).getFieldId()).isEqualTo(fieldId);
            assertThat(result.getFieldPolicies().get(0).getOperation()).isEqualTo("READ");

            verify(fieldPolicyRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("should set both route and field policies")
        void shouldSetBothRouteAndFieldPolicies() {
            // Given
            String collectionId = "collection-1";
            String fieldId = "field-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Field field = createTestField(fieldId, "name", collection);
            Policy readPolicy = createTestPolicy("policy-1", "Read Policy");
            Policy writePolicy = createTestPolicy("policy-2", "Write Policy");

            SetAuthorizationRequest.RoutePolicyRequest routePolicyRequest =
                    new SetAuthorizationRequest.RoutePolicyRequest("READ", "policy-1");
            SetAuthorizationRequest.FieldPolicyRequest fieldPolicyRequest =
                    new SetAuthorizationRequest.FieldPolicyRequest(fieldId, "WRITE", "policy-2");
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    List.of(routePolicyRequest),
                    List.of(fieldPolicyRequest)
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(policyRepository.findById("policy-1")).thenReturn(Optional.of(readPolicy));
            when(policyRepository.findById("policy-2")).thenReturn(Optional.of(writePolicy));
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue(fieldId, collectionId))
                    .thenReturn(Optional.of(field));
            when(routePolicyRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldPolicyRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            AuthorizationConfigDto result = authorizationService.setCollectionAuthorization(collectionId, request);

            // Then
            assertThat(result.getRoutePolicies()).hasSize(1);
            assertThat(result.getFieldPolicies()).hasSize(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenCollectionNotFound() {
            // Given
            String collectionId = "nonexistent";
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> authorizationService.setCollectionAuthorization(collectionId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Collection")
                    .hasMessageContaining(collectionId);

            verify(routePolicyRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when policy not found for route policy")
        void shouldThrowExceptionWhenPolicyNotFoundForRoutePolicy() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");

            SetAuthorizationRequest.RoutePolicyRequest routePolicyRequest =
                    new SetAuthorizationRequest.RoutePolicyRequest("READ", "nonexistent-policy");
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    List.of(routePolicyRequest),
                    Collections.emptyList()
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(policyRepository.findById("nonexistent-policy")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> authorizationService.setCollectionAuthorization(collectionId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Policy")
                    .hasMessageContaining("nonexistent-policy");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when field not found for field policy")
        void shouldThrowExceptionWhenFieldNotFound() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");

            SetAuthorizationRequest.FieldPolicyRequest fieldPolicyRequest =
                    new SetAuthorizationRequest.FieldPolicyRequest("nonexistent-field", "READ", "policy-1");
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    Collections.emptyList(),
                    List.of(fieldPolicyRequest)
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(fieldRepository.findByIdAndCollectionIdAndActiveTrue("nonexistent-field", collectionId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> authorizationService.setCollectionAuthorization(collectionId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Field")
                    .hasMessageContaining("nonexistent-field");
        }

        @Test
        @DisplayName("should delete existing policies before setting new ones")
        void shouldDeleteExistingPoliciesBeforeSettingNewOnes() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Policy policy = createTestPolicy("policy-1", "Read Policy");

            // Existing field policy to be deleted
            FieldPolicy existingFieldPolicy = new FieldPolicy();
            existingFieldPolicy.setId("existing-fp");

            SetAuthorizationRequest.RoutePolicyRequest routePolicyRequest =
                    new SetAuthorizationRequest.RoutePolicyRequest("READ", "policy-1");
            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    List.of(routePolicyRequest),
                    Collections.emptyList()
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(List.of(existingFieldPolicy));
            when(policyRepository.findById("policy-1")).thenReturn(Optional.of(policy));
            when(routePolicyRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            when(fieldPolicyRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // When
            authorizationService.setCollectionAuthorization(collectionId, request);

            // Then
            verify(routePolicyRepository).deleteByCollectionId(collectionId);
            verify(fieldPolicyRepository).deleteAll(List.of(existingFieldPolicy));
        }

        @Test
        @DisplayName("should handle empty authorization request")
        void shouldHandleEmptyAuthorizationRequest() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");

            SetAuthorizationRequest request = new SetAuthorizationRequest(
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(routePolicyRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
            when(fieldPolicyRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // When
            AuthorizationConfigDto result = authorizationService.setCollectionAuthorization(collectionId, request);

            // Then
            assertThat(result.getRoutePolicies()).isEmpty();
            assertThat(result.getFieldPolicies()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCollectionAuthorization")
    class GetCollectionAuthorizationTests {

        @Test
        @DisplayName("should return authorization config for collection")
        void shouldReturnAuthorizationConfigForCollection() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");
            Policy policy = createTestPolicy("policy-1", "Read Policy");
            Field field = createTestField("field-1", "name", collection);

            RoutePolicy routePolicy = new RoutePolicy(collection, "READ", policy);
            FieldPolicy fieldPolicy = new FieldPolicy(field, "WRITE", policy);

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(routePolicyRepository.findByCollectionId(collectionId)).thenReturn(List.of(routePolicy));
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(List.of(fieldPolicy));

            // When
            AuthorizationConfigDto result = authorizationService.getCollectionAuthorization(collectionId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCollectionId()).isEqualTo(collectionId);
            assertThat(result.getCollectionName()).isEqualTo("Test Collection");
            assertThat(result.getRoutePolicies()).hasSize(1);
            assertThat(result.getFieldPolicies()).hasSize(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when collection not found")
        void shouldThrowExceptionWhenCollectionNotFound() {
            // Given
            String collectionId = "nonexistent";

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> authorizationService.getCollectionAuthorization(collectionId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Collection")
                    .hasMessageContaining(collectionId);
        }

        @Test
        @DisplayName("should return empty policies when none configured")
        void shouldReturnEmptyPoliciesWhenNoneConfigured() {
            // Given
            String collectionId = "collection-1";
            Collection collection = createTestCollection(collectionId, "Test Collection");

            when(collectionRepository.findByIdAndActiveTrue(collectionId)).thenReturn(Optional.of(collection));
            when(routePolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());
            when(fieldPolicyRepository.findByCollectionId(collectionId)).thenReturn(Collections.emptyList());

            // When
            AuthorizationConfigDto result = authorizationService.getCollectionAuthorization(collectionId);

            // Then
            assertThat(result.getRoutePolicies()).isEmpty();
            assertThat(result.getFieldPolicies()).isEmpty();
        }
    }

    // Helper methods to create test entities

    private Role createTestRole(String id, String name) {
        Role role = new Role(name, "Test description for " + name);
        role.setId(id);
        return role;
    }

    private Policy createTestPolicy(String id, String name) {
        Policy policy = new Policy(name, "Test description for " + name, null, null);
        policy.setId(id);
        return policy;
    }

    private Collection createTestCollection(String id, String name) {
        com.emf.controlplane.entity.Service service = new com.emf.controlplane.entity.Service("test-service", "Test Service");
        service.setId("service-1");
        Collection collection = new Collection(service, name, "Test description");
        collection.setId(id);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        return collection;
    }

    private Field createTestField(String id, String name, Collection collection) {
        Field field = new Field(name, "string");
        field.setId(id);
        field.setCollection(collection);
        field.setActive(true);
        return field;
    }
}
