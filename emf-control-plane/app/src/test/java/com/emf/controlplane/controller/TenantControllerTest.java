package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateTenantRequest;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.dto.TenantDto;
import com.emf.controlplane.dto.UpdateTenantRequest;
import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantController.
 */
@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    @Mock
    private TenantService tenantService;

    private TenantController controller;

    @BeforeEach
    void setUp() {
        controller = new TenantController(tenantService);
    }

    @Nested
    @DisplayName("GET /platform/tenants")
    class ListTenantsTests {

        @Test
        @DisplayName("should return 200 OK with paginated tenants")
        void shouldReturnPaginatedTenants() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            List<Tenant> tenants = List.of(
                    new Tenant("acme", "Acme Corp"),
                    new Tenant("beta", "Beta Inc"));
            Page<Tenant> page = new PageImpl<>(tenants, pageable, 2);
            when(tenantService.listTenants(pageable)).thenReturn(page);

            // When
            ResponseEntity<Page<TenantDto>> response = controller.listTenants(pageable);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).hasSize(2);
            assertThat(response.getBody().getContent().get(0).getSlug()).isEqualTo("acme");
        }
    }

    @Nested
    @DisplayName("POST /platform/tenants")
    class CreateTenantTests {

        @Test
        @DisplayName("should return 201 CREATED with new tenant")
        void shouldCreateTenant() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("acme-corp", "Acme Corporation");
            Tenant tenant = new Tenant("acme-corp", "Acme Corporation");
            tenant.setStatus("ACTIVE");
            when(tenantService.createTenant(request)).thenReturn(tenant);

            // When
            ResponseEntity<TenantDto> response = controller.createTenant(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().getSlug()).isEqualTo("acme-corp");
            assertThat(response.getBody().getName()).isEqualTo("Acme Corporation");
        }

        @Test
        @DisplayName("should propagate DuplicateResourceException")
        void shouldPropagateDuplicateException() {
            // Given
            CreateTenantRequest request = new CreateTenantRequest("existing", "Existing");
            when(tenantService.createTenant(request))
                    .thenThrow(new DuplicateResourceException("Tenant", "slug", "existing"));

            // Then
            assertThatThrownBy(() -> controller.createTenant(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("GET /platform/tenants/{id}")
    class GetTenantTests {

        @Test
        @DisplayName("should return 200 OK with tenant")
        void shouldReturnTenant() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            when(tenantService.getTenant(tenant.getId())).thenReturn(tenant);

            // When
            ResponseEntity<TenantDto> response = controller.getTenant(tenant.getId());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getSlug()).isEqualTo("acme");
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException")
        void shouldPropagateNotFoundException() {
            // Given
            when(tenantService.getTenant("missing")).thenThrow(new ResourceNotFoundException("Tenant", "missing"));

            // Then
            assertThatThrownBy(() -> controller.getTenant("missing"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("GET /platform/tenants/by-slug/{slug}")
    class GetTenantBySlugTests {

        @Test
        @DisplayName("should return 200 OK with tenant by slug")
        void shouldReturnTenantBySlug() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme");
            when(tenantService.getTenantBySlug("acme")).thenReturn(tenant);

            // When
            ResponseEntity<TenantDto> response = controller.getTenantBySlug("acme");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getSlug()).isEqualTo("acme");
        }
    }

    @Nested
    @DisplayName("PUT /platform/tenants/{id}")
    class UpdateTenantTests {

        @Test
        @DisplayName("should return 200 OK with updated tenant")
        void shouldUpdateTenant() {
            // Given
            Tenant tenant = new Tenant("acme", "Acme Updated");
            UpdateTenantRequest request = new UpdateTenantRequest("Acme Updated", null, null, null);
            when(tenantService.updateTenant(eq(tenant.getId()), any(UpdateTenantRequest.class))).thenReturn(tenant);

            // When
            ResponseEntity<TenantDto> response = controller.updateTenant(tenant.getId(), request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getName()).isEqualTo("Acme Updated");
        }
    }

    @Nested
    @DisplayName("POST /platform/tenants/{id}/suspend")
    class SuspendTenantTests {

        @Test
        @DisplayName("should return 204 NO CONTENT")
        void shouldSuspendTenant() {
            // Given
            doNothing().when(tenantService).suspendTenant("tenant-id");

            // When
            ResponseEntity<Void> response = controller.suspendTenant("tenant-id");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(tenantService).suspendTenant("tenant-id");
        }
    }

    @Nested
    @DisplayName("POST /platform/tenants/{id}/activate")
    class ActivateTenantTests {

        @Test
        @DisplayName("should return 204 NO CONTENT")
        void shouldActivateTenant() {
            // Given
            doNothing().when(tenantService).activateTenant("tenant-id");

            // When
            ResponseEntity<Void> response = controller.activateTenant("tenant-id");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(tenantService).activateTenant("tenant-id");
        }
    }

    @Nested
    @DisplayName("GET /platform/tenants/{id}/limits")
    class GetLimitsTests {

        @Test
        @DisplayName("should return 200 OK with governor limits")
        void shouldReturnGovernorLimits() {
            // Given
            GovernorLimits limits = GovernorLimits.defaults();
            when(tenantService.getGovernorLimits("tenant-id")).thenReturn(limits);

            // When
            ResponseEntity<GovernorLimits> response = controller.getLimits("tenant-id");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(GovernorLimits.defaults());
        }
    }
}
