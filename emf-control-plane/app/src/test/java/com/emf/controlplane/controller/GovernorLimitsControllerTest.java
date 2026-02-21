package com.emf.controlplane.controller;

import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.service.GovernorLimitsService;
import com.emf.controlplane.service.TenantService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovernorLimitsController")
class GovernorLimitsControllerTest {

    @Mock
    private GovernorLimitsService governorLimitsService;

    @Mock
    private TenantService tenantService;

    private GovernorLimitsController controller;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        controller = new GovernorLimitsController(governorLimitsService, tenantService);
        TenantContextHolder.set(TENANT_ID, "test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("GET /control/governor-limits")
    class GetStatusTests {

        @Test
        @DisplayName("should return 200 OK with governor limits status")
        void shouldReturnStatus() {
            // Given
            GovernorLimitsService.GovernorLimitsStatus status =
                    new GovernorLimitsService.GovernorLimitsStatus(
                            GovernorLimits.defaults(),
                            500, 100_000,
                            25, 100,
                            5, 200);
            when(governorLimitsService.getStatus(TENANT_ID)).thenReturn(status);

            // When
            ResponseEntity<GovernorLimitsService.GovernorLimitsStatus> response = controller.getStatus();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(status);
            assertThat(response.getBody().apiCallsUsed()).isEqualTo(500);
            assertThat(response.getBody().usersUsed()).isEqualTo(25);
            assertThat(response.getBody().collectionsUsed()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("PUT /control/governor-limits")
    class UpdateLimitsTests {

        @Test
        @DisplayName("should return 200 OK with updated limits")
        void shouldUpdateLimits() {
            // Given
            GovernorLimits newLimits = new GovernorLimits(200_000, 20, 200, 400, 1000, 100, 400);
            when(tenantService.updateGovernorLimits(TENANT_ID, newLimits)).thenReturn(newLimits);

            // When
            ResponseEntity<GovernorLimits> response = controller.updateLimits(newLimits);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(newLimits);
            assertThat(response.getBody().apiCallsPerDay()).isEqualTo(200_000);
            assertThat(response.getBody().maxUsers()).isEqualTo(200);
            verify(tenantService).updateGovernorLimits(TENANT_ID, newLimits);
        }
    }
}
