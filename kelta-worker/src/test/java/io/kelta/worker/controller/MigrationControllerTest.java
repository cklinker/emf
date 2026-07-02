package io.kelta.worker.controller;

import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.MigrationRunRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.CollectionVersionService;
import io.kelta.worker.service.MigrationExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MigrationController")
class MigrationControllerTest {

    private CollectionVersionService versionService;
    private MigrationExecutionService executionService;
    private MigrationRunRepository runRepository;
    private CerbosPermissionResolver permissionResolver;
    private BootstrapRepository bootstrapRepository;
    private MigrationController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        versionService = mock(CollectionVersionService.class);
        executionService = mock(MigrationExecutionService.class);
        runRepository = mock(MigrationRunRepository.class);
        permissionResolver = mock(CerbosPermissionResolver.class);
        bootstrapRepository = mock(BootstrapRepository.class);
        request = mock(HttpServletRequest.class);
        controller = new MigrationController(versionService, executionService, runRepository,
                permissionResolver, bootstrapRepository);
    }

    private void grantPermission() {
        when(permissionResolver.getProfileId(request)).thenReturn("profile-1");
        when(bootstrapRepository.findProfileSystemPermissions("profile-1")).thenReturn(List.of(
                Map.of("permission_name", "CUSTOMIZE_APPLICATION", "granted", true)));
    }

    @Test
    @DisplayName("execute rejects a caller without CUSTOMIZE_APPLICATION")
    void executeRequiresPermission() {
        when(permissionResolver.getProfileId(request)).thenReturn("profile-1");
        when(bootstrapRepository.findProfileSystemPermissions("profile-1")).thenReturn(List.of());

        assertThatThrownBy(() -> controller.execute(Map.of("planId", "c1:1"), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("execute 400s when neither planId nor collectionId/targetVersion is given")
    void executeValidatesParams() {
        grantPermission();
        ResponseEntity<Map<String, Object>> response = controller.execute(Map.of(), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("execute parses a composite planId into collectionId + targetVersion")
    void executeParsesPlanId() {
        grantPermission();
        when(executionService.execute("c1", 2, false, false)).thenReturn(Map.of("status", "completed"));

        ResponseEntity<Map<String, Object>> response =
                controller.execute(Map.of("planId", "c1:2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).execute("c1", 2, false, false);
    }

    @Test
    @DisplayName("execute passes through dryRun + force flags")
    void executePassesFlags() {
        grantPermission();
        when(executionService.execute("c1", 3, true, true)).thenReturn(Map.of("dryRun", true));

        controller.execute(Map.of("collectionId", "c1", "targetVersion", 3, "dryRun", true, "force", true), request);

        verify(executionService).execute("c1", 3, true, true);
    }

    @Test
    @DisplayName("rollback 404s for an unknown run id")
    void rollbackUnknownRun() {
        grantPermission();
        when(runRepository.findRun("run-x")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.rollback("run-x", request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("rollback restores the run's from_version, forcing type changes")
    void rollbackRestoresFromVersion() {
        grantPermission();
        when(runRepository.findRun("run-1")).thenReturn(Map.of(
                "collection_id", "c1", "from_version", 5));
        when(executionService.execute("c1", 5, false, true)).thenReturn(Map.of("status", "completed"));

        ResponseEntity<Map<String, Object>> response = controller.rollback("run-1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).execute("c1", 5, false, true);
    }
}
