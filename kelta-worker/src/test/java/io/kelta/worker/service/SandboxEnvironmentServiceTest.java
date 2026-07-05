package io.kelta.worker.service;

import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.repository.EnvironmentRepository;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SandboxEnvironmentService")
class SandboxEnvironmentServiceTest {

    private EnvironmentRepository environmentRepository;
    private PackageService packageService;
    private PlatformEventPublisher eventPublisher;
    private SandboxEnvironmentService service;

    @BeforeEach
    void setUp() {
        environmentRepository = mock(EnvironmentRepository.class);
        packageService = mock(PackageService.class);
        eventPublisher = mock(PlatformEventPublisher.class);

        service = new SandboxEnvironmentService(environmentRepository, packageService,
                new ObjectMapper(), eventPublisher);
    }

    @Test
    @DisplayName("ensureProductionEnvironment should return existing production env")
    void ensureProductionShouldReturnExisting() {
        Map<String, Object> prodEnv = new HashMap<>();
        prodEnv.put("id", "prod-1");
        prodEnv.put("type", "PRODUCTION");
        when(environmentRepository.findProductionByTenant("t1")).thenReturn(Optional.of(prodEnv));

        var result = service.ensureProductionEnvironment("t1", "admin");

        assertThat(result.get("id")).isEqualTo("prod-1");
        verify(environmentRepository, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ensureProductionEnvironment should create when none exists")
    void ensureProductionShouldCreateWhenNone() {
        when(environmentRepository.findProductionByTenant("t1")).thenReturn(Optional.empty());
        when(environmentRepository.create(eq("t1"), eq("Production"), any(), eq("PRODUCTION"),
                isNull(), isNull(), any())).thenReturn("prod-new");

        Map<String, Object> newProd = new HashMap<>();
        newProd.put("id", "prod-new");
        newProd.put("type", "PRODUCTION");
        when(environmentRepository.findByIdAndTenant("prod-new", "t1")).thenReturn(Optional.of(newProd));

        var result = service.ensureProductionEnvironment("t1", "admin");

        assertThat(result.get("id")).isEqualTo("prod-new");
        verify(environmentRepository).updateStatus("prod-new", "t1", "ACTIVE");
    }

    @Test
    @DisplayName("archiveEnvironment should reject production environment")
    void archiveShouldRejectProduction() {
        Map<String, Object> prodEnv = new HashMap<>();
        prodEnv.put("id", "prod-1");
        prodEnv.put("type", "PRODUCTION");
        when(environmentRepository.findByIdAndTenant("prod-1", "t1")).thenReturn(Optional.of(prodEnv));

        assertThatThrownBy(() -> service.archiveEnvironment("prod-1", "t1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("production");
    }

    @Test
    @DisplayName("archiveEnvironment should archive sandbox")
    void archiveShouldArchiveSandbox() {
        Map<String, Object> sandboxEnv = new HashMap<>();
        sandboxEnv.put("id", "sandbox-1");
        sandboxEnv.put("type", "SANDBOX");
        when(environmentRepository.findByIdAndTenant("sandbox-1", "t1")).thenReturn(Optional.of(sandboxEnv));

        service.archiveEnvironment("sandbox-1", "t1");

        verify(environmentRepository).updateStatus("sandbox-1", "t1", "ARCHIVED");
    }

    @Test
    @DisplayName("listEnvironments should return all environments for tenant")
    void listShouldReturnAll() {
        List<Map<String, Object>> envs = List.of(
                Map.of("id", "env-1", "type", "PRODUCTION"),
                Map.of("id", "env-2", "type", "SANDBOX")
        );
        when(environmentRepository.findByTenant("t1")).thenReturn(envs);

        var result = service.listEnvironments("t1");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("updateEnvironment should reject production modification")
    void updateShouldRejectProduction() {
        Map<String, Object> prodEnv = new HashMap<>();
        prodEnv.put("id", "prod-1");
        prodEnv.put("type", "PRODUCTION");
        when(environmentRepository.findByIdAndTenant("prod-1", "t1")).thenReturn(Optional.of(prodEnv));

        assertThatThrownBy(() -> service.updateEnvironment("prod-1", "t1", "Renamed", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("production");
    }

    @Test
    @DisplayName("createSnapshot stores the full export package as the snapshot document")
    void createSnapshotStoresPackage() {
        Map<String, Object> env = new HashMap<>();
        env.put("id", "env-1");
        env.put("type", "PRODUCTION");
        when(environmentRepository.findByIdAndTenant("env-1", "t1")).thenReturn(Optional.of(env));

        Map<String, Object> options = Map.of("name", "snap");
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("formatVersion", 2);
        pkg.put("items", List.of(Map.of("type", "COLLECTION", "data", Map.of("name", "orders"))));
        when(packageService.exportAllOptions("t1", "snap", "snapshot")).thenReturn(options);
        when(packageService.exportPackage("t1", options, false)).thenReturn(pkg);

        when(environmentRepository.createSnapshot(eq("t1"), eq("env-1"), eq("snap"),
                any(), eq(1), eq("admin"))).thenReturn("snap-1");
        when(environmentRepository.findSnapshotById("snap-1", "t1"))
                .thenReturn(Optional.of(Map.of("id", "snap-1", "item_count", 1)));

        var result = service.createSnapshot("t1", "env-1", "snap", "admin");

        assertThat(result.get("id")).isEqualTo("snap-1");
        verify(environmentRepository).createSnapshot(eq("t1"), eq("env-1"), eq("snap"),
                contains("\"formatVersion\":2"), eq(1), eq("admin"));
    }

    @Test
    @DisplayName("snapshotPackage parses the stored package document")
    void snapshotPackageParsesDocument() {
        when(environmentRepository.findSnapshotById("snap-1", "t1")).thenReturn(Optional.of(Map.of(
                "id", "snap-1",
                "snapshot_data", "{\"formatVersion\":2,\"items\":[]}")));

        var pkg = service.snapshotPackage("snap-1", "t1");

        assertThat(pkg.get("formatVersion")).isEqualTo(2);
        assertThat(pkg.get("items")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("snapshotPackage rejects an unknown snapshot")
    void snapshotPackageRejectsUnknown() {
        when(environmentRepository.findSnapshotById("snap-x", "t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.snapshotPackage("snap-x", "t1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Snapshot not found");
    }
}
