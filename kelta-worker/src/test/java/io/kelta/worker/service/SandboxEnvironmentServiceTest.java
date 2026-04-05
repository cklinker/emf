package io.kelta.worker.service;

import io.kelta.worker.repository.EnvironmentRepository;
import io.kelta.worker.repository.PackageRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SandboxEnvironmentService")
class SandboxEnvironmentServiceTest {

    private EnvironmentRepository environmentRepository;
    private PackageRepository packageRepository;
    private ObjectMapper objectMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private JdbcTemplate jdbcTemplate;
    private SandboxEnvironmentService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        environmentRepository = mock(EnvironmentRepository.class);
        packageRepository = mock(PackageRepository.class);
        objectMapper = JsonMapper.builder().build();
        kafkaTemplate = mock(KafkaTemplate.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        when(environmentRepository.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service = new SandboxEnvironmentService(environmentRepository, packageRepository,
                objectMapper, kafkaTemplate);
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
        when(environmentRepository.create(eq("t1"), eq("Production"), any(), eq("PRODUCTION"), isNull(), isNull(), any()))
                .thenReturn("prod-new");

        Map<String, Object> newProd = new HashMap<>();
        newProd.put("id", "prod-new");
        newProd.put("type", "PRODUCTION");
        when(environmentRepository.findByIdAndTenant("prod-new", "t1")).thenReturn(Optional.of(newProd));

        var result = service.ensureProductionEnvironment("t1", "admin");

        assertThat(result.get("id")).isEqualTo("prod-new");
        verify(environmentRepository).create(eq("t1"), eq("Production"), any(), eq("PRODUCTION"), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("createSandbox should reject duplicate names")
    void createSandboxShouldRejectDuplicateNames() {
        when(environmentRepository.existsByTenantAndName("t1", "Dev")).thenReturn(true);

        assertThatThrownBy(() -> service.createSandbox("t1", "Dev", null, null, null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("createSandbox should create sandbox from production by default")
    void createSandboxShouldCreateFromProduction() {
        when(environmentRepository.existsByTenantAndName("t1", "Dev Sandbox")).thenReturn(false);

        // Production environment setup
        Map<String, Object> prodEnv = new HashMap<>();
        prodEnv.put("id", "prod-1");
        prodEnv.put("type", "PRODUCTION");
        when(environmentRepository.findProductionByTenant("t1")).thenReturn(Optional.of(prodEnv));

        when(environmentRepository.create(eq("t1"), eq("Dev Sandbox"), any(), eq("SANDBOX"), eq("prod-1"), any(), any()))
                .thenReturn("sandbox-1");

        Map<String, Object> sandboxEnv = new HashMap<>();
        sandboxEnv.put("id", "sandbox-1");
        sandboxEnv.put("type", "SANDBOX");
        sandboxEnv.put("name", "Dev Sandbox");
        when(environmentRepository.findByIdAndTenant("sandbox-1", "t1")).thenReturn(Optional.of(sandboxEnv));

        // Mock snapshot creation queries
        when(jdbcTemplate.queryForList(contains("FROM collection WHERE tenant_id"), eq("t1"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM field f JOIN collection"), eq("t1"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM role WHERE tenant_id"), eq("t1"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM policy WHERE tenant_id"), eq("t1"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM ui_page WHERE tenant_id"), eq("t1"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM ui_menu WHERE tenant_id"), eq("t1"))).thenReturn(List.of());
        when(environmentRepository.createSnapshot(any(), any(), any(), any(), anyInt(), any())).thenReturn("snap-1");

        var result = service.createSandbox("t1", "Dev Sandbox", null, null, null, "admin");

        assertThat(result.get("name")).isEqualTo("Dev Sandbox");
        verify(environmentRepository).create(eq("t1"), eq("Dev Sandbox"), any(), eq("SANDBOX"), eq("prod-1"), any(), any());
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
}
