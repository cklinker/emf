package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.SandboxEnvironmentService;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EnvironmentController")
class EnvironmentControllerTest {

    private SandboxEnvironmentService environmentService;
    private EnvironmentController controller;

    @BeforeEach
    void setUp() {
        environmentService = mock(SandboxEnvironmentService.class);
        controller = new EnvironmentController(environmentService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("listEnvironments should return 400 without tenant context")
    void listShouldRejectNoTenant() {
        var response = controller.listEnvironments();
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("listEnvironments should return environments")
    void listShouldReturnEnvironments() {
        TenantContext.set("t1");
        when(environmentService.listEnvironments("t1")).thenReturn(List.of(
                Map.of("id", "env-1", "name", "Production"),
                Map.of("id", "env-2", "name", "Sandbox")
        ));

        var response = controller.listEnvironments();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("getEnvironment should return 404 when not found")
    void getShouldReturn404() {
        TenantContext.set("t1");
        when(environmentService.getEnvironment("env-nonexistent", "t1")).thenReturn(Optional.empty());

        var response = controller.getEnvironment("env-nonexistent");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("getEnvironment should return environment")
    void getShouldReturnEnvironment() {
        TenantContext.set("t1");
        when(environmentService.getEnvironment("env-1", "t1"))
                .thenReturn(Optional.of(Map.of("id", "env-1", "name", "Sandbox")));

        var response = controller.getEnvironment("env-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("createEnvironment should require name")
    void createShouldRequireName() {
        TenantContext.set("t1");
        var body = new HashMap<String, Object>();

        var response = controller.createEnvironment(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("createEnvironment should create sandbox")
    void createShouldCreateSandbox() {
        TenantContext.set("t1");
        when(environmentService.createSandbox(eq("t1"), eq("Dev"), any(), any(), any(), any()))
                .thenReturn(Map.of("id", "env-new", "name", "Dev", "type", "SANDBOX"));

        var body = new HashMap<String, Object>();
        body.put("name", "Dev");

        var response = controller.createEnvironment(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("createEnvironment should create staging")
    void createShouldCreateStaging() {
        TenantContext.set("t1");
        when(environmentService.createStaging(eq("t1"), eq("Staging"), any(), any(), any(), any()))
                .thenReturn(Map.of("id", "env-new", "name", "Staging", "type", "STAGING"));

        var body = new HashMap<String, Object>();
        body.put("name", "Staging");
        body.put("type", "STAGING");

        var response = controller.createEnvironment(body);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("archiveEnvironment should return error for invalid env")
    void archiveShouldHandleError() {
        TenantContext.set("t1");
        doThrow(new IllegalArgumentException("Environment not found"))
                .when(environmentService).archiveEnvironment("env-bad", "t1");

        var response = controller.archiveEnvironment("env-bad");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("archiveEnvironment should succeed for valid sandbox")
    void archiveShouldSucceed() {
        TenantContext.set("t1");
        doNothing().when(environmentService).archiveEnvironment("env-1", "t1");

        var response = controller.archiveEnvironment("env-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("compareEnvironments should require both environment IDs")
    void compareShouldRequireBothIds() {
        TenantContext.set("t1");
        var body = new HashMap<String, Object>();
        body.put("sourceEnvironmentId", "env-1");

        var response = controller.compareEnvironments(body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
