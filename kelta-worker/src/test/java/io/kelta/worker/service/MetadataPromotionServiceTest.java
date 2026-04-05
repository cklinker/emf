package io.kelta.worker.service;

import io.kelta.worker.repository.EnvironmentPromotionRepository;
import io.kelta.worker.repository.EnvironmentRepository;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MetadataPromotionService")
class MetadataPromotionServiceTest {

    private EnvironmentPromotionRepository promotionRepository;
    private EnvironmentRepository environmentRepository;
    private SandboxEnvironmentService sandboxEnvironmentService;
    private PackageService packageService;
    private ObjectMapper objectMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private MetadataPromotionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        promotionRepository = mock(EnvironmentPromotionRepository.class);
        environmentRepository = mock(EnvironmentRepository.class);
        sandboxEnvironmentService = mock(SandboxEnvironmentService.class);
        packageService = mock(PackageService.class);
        objectMapper = JsonMapper.builder().build();
        kafkaTemplate = mock(KafkaTemplate.class);

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service = new MetadataPromotionService(promotionRepository, environmentRepository,
                sandboxEnvironmentService, packageService, objectMapper, kafkaTemplate);
    }

    @Test
    @DisplayName("createPromotion should validate source environment exists")
    void createPromotionShouldValidateSource() {
        when(environmentRepository.findByIdAndTenant("env-nonexistent", "t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPromotion("t1", "env-nonexistent", "env-2", "FULL", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source environment not found");
    }

    @Test
    @DisplayName("createPromotion should validate target environment exists")
    void createPromotionShouldValidateTarget() {
        Map<String, Object> sourceEnv = new HashMap<>();
        sourceEnv.put("id", "env-1");
        sourceEnv.put("status", "ACTIVE");
        when(environmentRepository.findByIdAndTenant("env-1", "t1")).thenReturn(Optional.of(sourceEnv));
        when(environmentRepository.findByIdAndTenant("env-nonexistent", "t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPromotion("t1", "env-1", "env-nonexistent", "FULL", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target environment not found");
    }

    @Test
    @DisplayName("createPromotion should require active environments")
    void createPromotionShouldRequireActiveEnvs() {
        Map<String, Object> sourceEnv = new HashMap<>();
        sourceEnv.put("id", "env-1");
        sourceEnv.put("status", "ACTIVE");
        Map<String, Object> targetEnv = new HashMap<>();
        targetEnv.put("id", "env-2");
        targetEnv.put("status", "ARCHIVED");
        when(environmentRepository.findByIdAndTenant("env-1", "t1")).thenReturn(Optional.of(sourceEnv));
        when(environmentRepository.findByIdAndTenant("env-2", "t1")).thenReturn(Optional.of(targetEnv));

        assertThatThrownBy(() -> service.createPromotion("t1", "env-1", "env-2", "FULL", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active");
    }

    @Test
    @DisplayName("createPromotion should create full promotion successfully")
    void createPromotionShouldCreateSuccessfully() {
        Map<String, Object> sourceEnv = new HashMap<>();
        sourceEnv.put("id", "env-1");
        sourceEnv.put("status", "ACTIVE");
        Map<String, Object> targetEnv = new HashMap<>();
        targetEnv.put("id", "env-2");
        targetEnv.put("status", "ACTIVE");
        when(environmentRepository.findByIdAndTenant("env-1", "t1")).thenReturn(Optional.of(sourceEnv));
        when(environmentRepository.findByIdAndTenant("env-2", "t1")).thenReturn(Optional.of(targetEnv));

        // Snapshot creation
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", "snap-1");
        when(sandboxEnvironmentService.createSnapshot(eq("t1"), eq("env-1"), any(), any()))
                .thenReturn(snapshot);

        when(promotionRepository.create(eq("t1"), eq("env-1"), eq("env-2"), eq("FULL"), eq("snap-1"), eq("admin")))
                .thenReturn("promo-1");

        Map<String, Object> promoResult = new HashMap<>();
        promoResult.put("id", "promo-1");
        promoResult.put("status", "PENDING");
        when(promotionRepository.findByIdAndTenant("promo-1", "t1")).thenReturn(Optional.of(promoResult));

        var result = service.createPromotion("t1", "env-1", "env-2", "FULL", null, "admin");

        assertThat(result.get("id")).isEqualTo("promo-1");
        assertThat(result.get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("approvePromotion should reject non-PENDING promotion")
    void approveShouldRejectNonPending() {
        Map<String, Object> promo = new HashMap<>();
        promo.put("id", "promo-1");
        promo.put("status", "COMPLETED");
        when(promotionRepository.findByIdAndTenant("promo-1", "t1")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> service.approvePromotion("promo-1", "t1", "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in PENDING status");
    }

    @Test
    @DisplayName("approvePromotion should approve pending promotion")
    void approveShouldApprovePending() {
        Map<String, Object> promo = new HashMap<>();
        promo.put("id", "promo-1");
        promo.put("status", "PENDING");
        when(promotionRepository.findByIdAndTenant("promo-1", "t1")).thenReturn(Optional.of(promo));

        Map<String, Object> approved = new HashMap<>();
        approved.put("id", "promo-1");
        approved.put("status", "APPROVED");
        // First call returns PENDING, second call (after approve) returns APPROVED
        when(promotionRepository.findByIdAndTenant("promo-1", "t1"))
                .thenReturn(Optional.of(promo))
                .thenReturn(Optional.of(approved));

        var result = service.approvePromotion("promo-1", "t1", "manager");

        verify(promotionRepository).approve("promo-1", "manager");
    }

    @Test
    @DisplayName("rollbackPromotion should only rollback completed promotions")
    void rollbackShouldRejectNonCompleted() {
        Map<String, Object> promo = new HashMap<>();
        promo.put("id", "promo-1");
        promo.put("status", "PENDING");
        when(promotionRepository.findByIdAndTenant("promo-1", "t1")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> service.rollbackPromotion("promo-1", "t1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed promotions");
    }

    @Test
    @DisplayName("rollbackPromotion should require snapshot")
    void rollbackShouldRequireSnapshot() {
        Map<String, Object> promo = new HashMap<>();
        promo.put("id", "promo-1");
        promo.put("status", "COMPLETED");
        promo.put("snapshot_id", null);
        when(promotionRepository.findByIdAndTenant("promo-1", "t1")).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> service.rollbackPromotion("promo-1", "t1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot");
    }

    @Test
    @DisplayName("listPromotions should delegate to repository")
    void listShouldDelegate() {
        List<Map<String, Object>> promos = List.of(Map.of("id", "promo-1"));
        when(promotionRepository.findByTenant("t1", 50, 0)).thenReturn(promos);

        var result = service.listPromotions("t1", 50, 0);

        assertThat(result).hasSize(1);
    }
}
