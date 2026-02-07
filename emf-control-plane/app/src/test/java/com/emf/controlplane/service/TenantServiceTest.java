package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateTenantRequest;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.dto.UpdateTenantRequest;
import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantService.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private ObjectMapper objectMapper;
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tenantService = new TenantService(tenantRepository, objectMapper, null);
    }

    @Nested
    @DisplayName("createTenant")
    class CreateTenantTests {

        @Test
        @DisplayName("should create tenant with slug and name")
        void shouldCreateTenantWithSlugAndName() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("acme-corp", "Acme Corporation");
            when(tenantRepository.existsBySlug("acme-corp")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Tenant result = tenantService.createTenant(request);

            // Then
            assertThat(result.getSlug()).isEqualTo("acme-corp");
            assertThat(result.getName()).isEqualTo("Acme Corporation");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getEdition()).isEqualTo("PROFESSIONAL");
            verify(tenantRepository, times(2)).save(any(Tenant.class));
        }

        @Test
        @DisplayName("should create tenant with custom edition")
        void shouldCreateTenantWithCustomEdition() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("acme", "Acme", "ENTERPRISE", null, null);
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Tenant result = tenantService.createTenant(request);

            // Then
            assertThat(result.getEdition()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should create tenant with settings and limits")
        void shouldCreateTenantWithSettingsAndLimits() {
            // Given
            Map<String, Object> settings = Map.of("timezone", "UTC");
            Map<String, Object> limits = Map.of("max_users", 50);
            CreateTenantRequest request = new CreateTenantRequest("acme", "Acme", null, settings, limits);
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Tenant result = tenantService.createTenant(request);

            // Then
            assertThat(result.getSettings()).contains("timezone");
            assertThat(result.getLimits()).contains("max_users");
        }

        @Test
        @DisplayName("should throw DuplicateResourceException for existing slug")
        void shouldThrowDuplicateResourceExceptionForExistingSlug() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("existing-slug", "Duplicate");
            when(tenantRepository.existsBySlug("existing-slug")).thenReturn(true);

            // Then
            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("slug");
        }

        @Test
        @DisplayName("should reject invalid edition")
        void shouldRejectInvalidEdition() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("acme", "Acme", "INVALID", null, null);
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);

            // Then
            assertThatThrownBy(() -> tenantService.createTenant(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid edition");
        }

        @Test
        @DisplayName("should transition status from PROVISIONING to ACTIVE")
        void shouldTransitionStatusFromProvisioningToActive() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("acme", "Acme");
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);

            // Capture status at each save invocation since the object is mutated
            java.util.List<String> statusesAtSave = new java.util.ArrayList<>();
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant t = invocation.getArgument(0);
                statusesAtSave.add(t.getStatus());
                return t;
            });

            // When
            tenantService.createTenant(request);

            // Then — first save is PROVISIONING, second is ACTIVE
            assertThat(statusesAtSave).containsExactly("PROVISIONING", "ACTIVE");
        }
    }

    @Nested
    @DisplayName("getTenant")
    class GetTenantTests {

        @Test
        @DisplayName("should return tenant when found")
        void shouldReturnTenantWhenFound() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // When
            Tenant result = tenantService.getTenant(tenant.getId());

            // Then
            assertThat(result).isEqualTo(tenant);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            // Given
            when(tenantRepository.findById("missing-id")).thenReturn(Optional.empty());

            // Then
            assertThatThrownBy(() -> tenantService.getTenant("missing-id"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTenantBySlug")
    class GetTenantBySlugTests {

        @Test
        @DisplayName("should return tenant when slug found")
        void shouldReturnTenantWhenSlugFound() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

            // When
            Tenant result = tenantService.getTenantBySlug("acme");

            // Then
            assertThat(result).isEqualTo(tenant);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when slug not found")
        void shouldThrowResourceNotFoundExceptionWhenSlugNotFound() {
            // Given
            when(tenantRepository.findBySlug("missing")).thenReturn(Optional.empty());

            // Then
            assertThatThrownBy(() -> tenantService.getTenantBySlug("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listTenants")
    class ListTenantsTests {

        @Test
        @DisplayName("should return paginated list of tenants")
        void shouldReturnPaginatedListOfTenants() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            List<Tenant> tenants = List.of(
                    new Tenant("tenant-a", "Tenant A"),
                    new Tenant("tenant-b", "Tenant B"));
            Page<Tenant> page = new PageImpl<>(tenants, pageable, 2);
            when(tenantRepository.findAll(pageable)).thenReturn(page);

            // When
            Page<Tenant> result = tenantService.listTenants(pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenantTests {

        @Test
        @DisplayName("should update tenant name")
        void shouldUpdateTenantName() {
            // Given
            Tenant tenant = new Tenant("acme", "Old Name");
            UpdateTenantRequest request = new UpdateTenantRequest("New Name", null, null, null);
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Tenant result = tenantService.updateTenant(tenant.getId(), request);

            // Then
            assertThat(result.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("should update tenant edition")
        void shouldUpdateTenantEdition() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            UpdateTenantRequest request = new UpdateTenantRequest(null, "ENTERPRISE", null, null);
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Tenant result = tenantService.updateTenant(tenant.getId(), request);

            // Then
            assertThat(result.getEdition()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should update tenant settings and limits")
        void shouldUpdateTenantSettingsAndLimits() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            Map<String, Object> settings = Map.of("locale", "en-US");
            Map<String, Object> limits = Map.of("max_users", 500);
            UpdateTenantRequest request = new UpdateTenantRequest(null, null, settings, limits);
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Tenant result = tenantService.updateTenant(tenant.getId(), request);

            // Then
            assertThat(result.getSettings()).contains("locale");
            assertThat(result.getLimits()).contains("max_users");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            // Given
            when(tenantRepository.findById("missing")).thenReturn(Optional.empty());

            // Then
            assertThatThrownBy(() -> tenantService.updateTenant("missing", new UpdateTenantRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should reject invalid edition on update")
        void shouldRejectInvalidEditionOnUpdate() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            UpdateTenantRequest request = new UpdateTenantRequest(null, "INVALID", null, null);
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // Then
            assertThatThrownBy(() -> tenantService.updateTenant(tenant.getId(), request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid edition");
        }
    }

    @Nested
    @DisplayName("suspendTenant")
    class SuspendTenantTests {

        @Test
        @DisplayName("should set status to SUSPENDED")
        void shouldSetStatusToSuspended() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            tenant.setStatus("ACTIVE");
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // When
            tenantService.suspendTenant(tenant.getId());

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("SUSPENDED");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            // Given
            when(tenantRepository.findById("missing")).thenReturn(Optional.empty());

            // Then
            assertThatThrownBy(() -> tenantService.suspendTenant("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("activateTenant")
    class ActivateTenantTests {

        @Test
        @DisplayName("should set status to ACTIVE")
        void shouldSetStatusToActive() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            tenant.setStatus("SUSPENDED");
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // When
            tenantService.activateTenant(tenant.getId());

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            // Given
            when(tenantRepository.findById("missing")).thenReturn(Optional.empty());

            // Then
            assertThatThrownBy(() -> tenantService.activateTenant("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getGovernorLimits")
    class GetGovernorLimitsTests {

        @Test
        @DisplayName("should return defaults when limits is empty JSON")
        void shouldReturnDefaultsWhenLimitsEmpty() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // When
            GovernorLimits limits = tenantService.getGovernorLimits(tenant.getId());

            // Then
            assertThat(limits).isEqualTo(GovernorLimits.defaults());
        }

        @Test
        @DisplayName("should parse custom limits from JSON")
        void shouldParseCustomLimitsFromJson() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            tenant.setLimits("{\"max_users\": 500, \"storage_gb\": 50}");
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // When
            GovernorLimits limits = tenantService.getGovernorLimits(tenant.getId());

            // Then
            assertThat(limits.maxUsers()).isEqualTo(500);
            assertThat(limits.storageGb()).isEqualTo(50);
            // Other fields should fall back to defaults
            assertThat(limits.apiCallsPerDay()).isEqualTo(100_000);
            assertThat(limits.maxCollections()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return defaults on malformed JSON")
        void shouldReturnDefaultsOnMalformedJson() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            tenant.setLimits("not-valid-json");
            when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

            // When
            GovernorLimits limits = tenantService.getGovernorLimits(tenant.getId());

            // Then
            assertThat(limits).isEqualTo(GovernorLimits.defaults());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when tenant not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            // Given
            when(tenantRepository.findById("missing")).thenReturn(Optional.empty());

            // Then
            assertThatThrownBy(() -> tenantService.getGovernorLimits("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
