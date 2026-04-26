package io.kelta.worker.controller;

import io.kelta.worker.service.SvixTenantService;
import io.kelta.worker.service.SvixTenantService.PortalAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SvixPortalController")
class SvixPortalControllerTest {

    @Mock
    private SvixTenantService svixTenantService;

    @Mock
    private HttpServletRequest request;

    private SvixPortalController controller;

    @BeforeEach
    void setUp() {
        controller = new SvixPortalController(svixTenantService, "http://localhost:8071");
    }

    @Test
    @DisplayName("returns bad request when tenant ID is missing")
    void returnsBadRequestWhenNoTenantId() {
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);

        var response = controller.getPortalAccess(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ensures Svix application exists before requesting portal access")
    void ensuresApplicationBeforePortalAccess() {
        when(request.getHeader("X-Tenant-ID")).thenReturn("tenant-123");
        when(svixTenantService.getPortalAccess("tenant-123"))
                .thenReturn(new PortalAccess("test-token", "https://svix.example/portal"));

        var response = controller.getPortalAccess(request);

        verify(svixTenantService).ensureApplication("tenant-123", "tenant-123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("token", "test-token");
        assertThat(response.getBody()).containsEntry("appId", "tenant-123");
        assertThat(response.getBody()).containsEntry("serverUrl", "http://localhost:8071");
    }

    @Test
    @DisplayName("returns 502 when Svix responds with an HTTP error")
    void returnsBadGatewayOnUpstreamHttpError() {
        when(request.getHeader("X-Tenant-ID")).thenReturn("tenant-123");
        when(svixTenantService.getPortalAccess("tenant-123"))
                .thenThrow(new RestClientResponseException(
                        "401 Unauthorized", HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        var response = controller.getPortalAccess(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("returns 500 on unexpected failures")
    void returnsServerErrorOnUnexpectedFailure() {
        when(request.getHeader("X-Tenant-ID")).thenReturn("tenant-123");
        when(svixTenantService.getPortalAccess("tenant-123"))
                .thenThrow(new RuntimeException("boom"));

        var response = controller.getPortalAccess(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
