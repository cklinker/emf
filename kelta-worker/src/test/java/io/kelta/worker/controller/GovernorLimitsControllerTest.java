package io.kelta.worker.controller;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.events.RecordEventPublisher;
import io.kelta.worker.cache.WorkerCacheManager;
import io.kelta.worker.repository.GovernorLimitsRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GovernorLimitsController}.
 *
 * <p>Verifies the governor-limits endpoint that returns tenant limits
 * and current usage metrics (users, collections, API calls) in JSON:API format.
 */
class GovernorLimitsControllerTest {

    private GovernorLimitsRepository repository;
    private ObjectMapper objectMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RecordEventPublisher recordEventPublisher;
    private GovernorLimitsController controller;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repository = mock(GovernorLimitsRepository.class);
        objectMapper = new ObjectMapper();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        recordEventPublisher = mock(RecordEventPublisher.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Default: Redis returns null (0 API calls)
        when(valueOps.get(anyString())).thenReturn(null);
        WorkerCacheManager cacheManager = new WorkerCacheManager(new SimpleMeterRegistry());
        controller = new GovernorLimitsController(repository, objectMapper, redisTemplate, recordEventPublisher, cacheManager);
    }

    /** Extracts the attributes map from a JSON:API single-resource response body. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(Map<String, Object> body) {
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (Map<String, Object>) data.get("attributes");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> body) {
        return (Map<String, Object>) body.get("data");
    }

    // ==================== GET Tests ====================

    @Nested
    @DisplayName("GET /api/governor-limits")
    class GetStatusTests {

        @Test
        @DisplayName("Should return JSON:API envelope with type and id")
        void returnsJsonApiEnvelope() {
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            Map<String, Object> body = response.getBody();
            assertThat(body).containsKey("data");
            Map<String, Object> data = getData(body);
            assertThat(data.get("type")).isEqualTo("governor-limits");
            assertThat(data.get("id")).isEqualTo("tenant-1");
            assertThat(data).containsKey("attributes");
        }

        @Test
        @DisplayName("Should return default limits when tenant has empty limits")
        void returnsDefaultLimitsWhenEmpty() {
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(5);
            when(repository.countActiveCollections("tenant-1")).thenReturn(12);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> attrs = getAttributes(response.getBody());

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) attrs.get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(100_000);
            assertThat(limits.get("storageGb")).isEqualTo(10);
            assertThat(limits.get("maxUsers")).isEqualTo(100);
            assertThat(limits.get("maxCollections")).isEqualTo(200);
            assertThat(limits.get("maxFieldsPerCollection")).isEqualTo(500);
            assertThat(limits.get("maxWorkflows")).isEqualTo(50);
            assertThat(limits.get("maxReports")).isEqualTo(200);

            assertThat(attrs.get("apiCallsUsed")).isEqualTo(0);
            assertThat(attrs.get("apiCallsLimit")).isEqualTo(100_000);
            assertThat(attrs.get("usersUsed")).isEqualTo(5);
            assertThat(attrs.get("usersLimit")).isEqualTo(100);
            assertThat(attrs.get("collectionsUsed")).isEqualTo(12);
            assertThat(attrs.get("collectionsLimit")).isEqualTo(200);
        }

        @Test
        @DisplayName("Should return configured limits from tenant")
        void returnsConfiguredLimits() {
            String limitsJson = "{\"apiCallsPerDay\":50000,\"storageGb\":50,\"maxUsers\":500,\"maxCollections\":100,\"maxFieldsPerCollection\":1000,\"maxWorkflows\":200,\"maxReports\":500}";
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of(limitsJson));
            when(repository.countActiveUsers("tenant-1")).thenReturn(25);
            when(repository.countActiveCollections("tenant-1")).thenReturn(8);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            Map<String, Object> attrs = getAttributes(response.getBody());

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) attrs.get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(50_000);
            assertThat(limits.get("storageGb")).isEqualTo(50);
            assertThat(limits.get("maxUsers")).isEqualTo(500);
            assertThat(limits.get("maxCollections")).isEqualTo(100);

            assertThat(attrs.get("usersUsed")).isEqualTo(25);
            assertThat(attrs.get("usersLimit")).isEqualTo(500);
            assertThat(attrs.get("collectionsUsed")).isEqualTo(8);
            assertThat(attrs.get("collectionsLimit")).isEqualTo(100);
        }

        @Test
        @DisplayName("Should fill defaults for missing limit keys")
        void fillsDefaultsForMissingKeys() {
            String limitsJson = "{\"apiCallsPerDay\":75000}";
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of(limitsJson));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) getAttributes(response.getBody()).get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(75_000);
            assertThat(limits.get("storageGb")).isEqualTo(10);
            assertThat(limits.get("maxUsers")).isEqualTo(100);
            assertThat(limits.get("maxCollections")).isEqualTo(200);
        }

        @Test
        @DisplayName("Should handle tenant not found")
        void handlesTenantNotFound() {
            when(repository.findTenantLimits("nonexistent")).thenReturn(Optional.empty());
            when(repository.countActiveUsers("nonexistent")).thenReturn(0);
            when(repository.countActiveCollections("nonexistent")).thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("nonexistent");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) getAttributes(response.getBody()).get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(100_000);
        }

        @Test
        @DisplayName("Should handle malformed limits JSON")
        void handlesMalformedJson() {
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{invalid}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(3);
            when(repository.countActiveCollections("tenant-1")).thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) getAttributes(response.getBody()).get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(100_000);
            assertThat(limits.get("maxUsers")).isEqualTo(100);
        }

        @Test
        @DisplayName("Should handle limits as Map object (JDBC driver returns Map)")
        void handlesLimitsAsMap() {
            Map<String, Object> limitsMap = new HashMap<>();
            limitsMap.put("apiCallsPerDay", 60000);
            limitsMap.put("maxUsers", 250);

            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of(limitsMap));
            when(repository.countActiveUsers("tenant-1")).thenReturn(10);
            when(repository.countActiveCollections("tenant-1")).thenReturn(20);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) getAttributes(response.getBody()).get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(60_000);
            assertThat(limits.get("maxUsers")).isEqualTo(250);
            assertThat(limits.get("storageGb")).isEqualTo(10);
        }

        @Test
        @DisplayName("Should parse limits from PGobject-like type (JSONB column)")
        void handlesLimitsAsPGobject() {
            // PostgreSQL JDBC driver returns JSONB columns as PGobject,
            // which is not a String or Map but has a valid toString()
            Object pgObjectLike = new Object() {
                @Override
                public String toString() {
                    return "{\"apiCallsPerDay\":10000000,\"storageGb\":50,\"maxUsers\":500}";
                }
            };

            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of(pgObjectLike));
            when(repository.countActiveUsers("tenant-1")).thenReturn(10);
            when(repository.countActiveCollections("tenant-1")).thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) getAttributes(response.getBody()).get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(10_000_000);
            assertThat(limits.get("storageGb")).isEqualTo(50);
            assertThat(limits.get("maxUsers")).isEqualTo(500);
            // Defaults filled for missing keys
            assertThat(limits.get("maxCollections")).isEqualTo(200);
        }

        @Test
        @DisplayName("Should return zero apiCallsUsed when Redis key is absent")
        void returnsZeroApiCallsUsedWhenRedisEmpty() {
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(getAttributes(response.getBody()).get("apiCallsUsed")).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return daily API call count from Redis")
        void returnsDailyApiCallCountFromRedis() {
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            String today = LocalDate.now(ZoneOffset.UTC).toString();
            when(valueOps.get("api-calls-daily:tenant-1:" + today)).thenReturn("4523");

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(getAttributes(response.getBody()).get("apiCallsUsed")).isEqualTo(4523);
        }

        @Test
        @DisplayName("Should return zero when Redis throws exception")
        void returnsZeroWhenRedisThrows() {
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(getAttributes(response.getBody()).get("apiCallsUsed")).isEqualTo(0);
        }
    }

    // ==================== PUT Tests ====================

    @Nested
    @DisplayName("PUT /api/governor-limits")
    class UpdateLimitsTests {

        @Test
        @DisplayName("Should update tenant limits")
        void updatesTenantLimits() {
            Map<String, Object> newLimits = new LinkedHashMap<>();
            newLimits.put("apiCallsPerDay", 200_000);
            newLimits.put("storageGb", 100);
            newLimits.put("maxUsers", 1000);
            newLimits.put("maxCollections", 500);
            newLimits.put("maxFieldsPerCollection", 1000);
            newLimits.put("maxWorkflows", 100);
            newLimits.put("maxReports", 400);

            // Mock re-read for response
            String updatedJson = "{\"apiCallsPerDay\":200000,\"storageGb\":100,\"maxUsers\":1000,\"maxCollections\":500,\"maxFieldsPerCollection\":1000,\"maxWorkflows\":100,\"maxReports\":400}";
            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of(updatedJson));
            when(repository.countActiveUsers("tenant-1")).thenReturn(50);
            when(repository.countActiveCollections("tenant-1")).thenReturn(30);

            ResponseEntity<Map<String, Object>> response = controller.updateLimits("tenant-1", newLimits);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

            verify(repository).updateTenantLimits(eq("tenant-1"), anyString());

            Map<String, Object> attrs = getAttributes(response.getBody());
            assertThat(attrs).isNotNull();
            assertThat(attrs.get("usersUsed")).isEqualTo(50);
            assertThat(attrs.get("collectionsUsed")).isEqualTo(30);
        }

        @Test
        @DisplayName("Should publish Kafka event when limits are updated")
        @SuppressWarnings("unchecked")
        void publishesKafkaEventOnUpdate() {
            Map<String, Object> newLimits = new LinkedHashMap<>();
            newLimits.put("apiCallsPerDay", 200_000);

            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{\"apiCallsPerDay\":200000}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            controller.updateLimits("tenant-1", newLimits);

            ArgumentCaptor<PlatformEvent<RecordChangedPayload>> captor =
                    ArgumentCaptor.forClass(PlatformEvent.class);
            verify(recordEventPublisher).publish(captor.capture());

            PlatformEvent<RecordChangedPayload> event = captor.getValue();
            assertThat(event.getTenantId()).isEqualTo("tenant-1");
            assertThat(event.getEventType()).isEqualTo("record.updated");
            assertThat(event.getPayload().getCollectionName()).isEqualTo("tenants");
            assertThat(event.getPayload().getRecordId()).isEqualTo("tenant-1");
            assertThat(event.getPayload().getChangedFields()).containsExactly("limits");
        }

        @Test
        @DisplayName("Should not fail update if Kafka event publishing fails")
        void doesNotFailIfKafkaPublishFails() {
            Map<String, Object> newLimits = new LinkedHashMap<>();
            newLimits.put("apiCallsPerDay", 200_000);

            doThrow(new RuntimeException("Kafka is down"))
                    .when(recordEventPublisher).publish(any());

            when(repository.findTenantLimits("tenant-1")).thenReturn(Optional.of("{\"apiCallsPerDay\":200000}"));
            when(repository.countActiveUsers("tenant-1")).thenReturn(0);
            when(repository.countActiveCollections("tenant-1")).thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.updateLimits("tenant-1", newLimits);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            verify(repository).updateTenantLimits(eq("tenant-1"), anyString());
        }
    }
}
