package io.kelta.worker.controller;

import com.svix.Svix;
import com.svix.api.Authentication;
import com.svix.models.AppPortalAccessIn;
import com.svix.models.AppPortalAccessOut;
import io.kelta.worker.service.SvixTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SvixPortalController")
class SvixPortalControllerTest {

    @Mock
    private Svix svix;

    @Mock
    private SvixTenantService svixTenantService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    private SvixPortalController controller;

    @BeforeEach
    void setUp() {
        controller = new SvixPortalController(svix, svixTenantService, "http://localhost:8071");
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
    void ensuresApplicationBeforePortalAccess() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn("tenant-123");
        when(svix.getAuthentication()).thenReturn(authentication);

        var accessOut = new AppPortalAccessOut();
        accessOut.setToken("test-token");
        when(authentication.appPortalAccess(eq("tenant-123"), any(AppPortalAccessIn.class)))
                .thenReturn(accessOut);

        var response = controller.getPortalAccess(request);

        verify(svixTenantService).ensureApplication("tenant-123", "tenant-123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("token", "test-token");
        assertThat(response.getBody()).containsEntry("appId", "tenant-123");
        assertThat(response.getBody()).containsEntry("serverUrl", "http://localhost:8071");
    }

    @Test
    @DisplayName("returns server error when Svix API fails")
    void returnsServerErrorOnSvixFailure() throws Exception {
        when(request.getHeader("X-Tenant-ID")).thenReturn("tenant-123");
        when(svix.getAuthentication()).thenReturn(authentication);
        when(authentication.appPortalAccess(eq("tenant-123"), any(AppPortalAccessIn.class)))
                .thenThrow(new RuntimeException("Svix unavailable"));

        var response = controller.getPortalAccess(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
