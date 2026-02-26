package com.emf.worker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GovernorLimitsController}.
 *
 * <p>Verifies the governor-limits endpoint that returns tenant limits
 * and current usage metrics (users, collections, API calls).
 */
class GovernorLimitsControllerTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private GovernorLimitsController controller;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Default: Redis returns null (0 API calls)
        when(valueOps.get(anyString())).thenReturn(null);
        controller = new GovernorLimitsController(jdbcTemplate, objectMapper, redisTemplate);
    }

    // ==================== GET Tests ====================

    @Nested
    @DisplayName("GET /api/collections/governor-limits")
    class GetStatusTests {

        @Test
        @DisplayName("Should return default limits when tenant has empty limits")
        void returnsDefaultLimitsWhenEmpty() {
            // Given: tenant with empty limits
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow("{}")));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(5);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(12);

            // When
            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            // Then
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) body.get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(100_000);
            assertThat(limits.get("storageGb")).isEqualTo(10);
            assertThat(limits.get("maxUsers")).isEqualTo(100);
            assertThat(limits.get("maxCollections")).isEqualTo(200);
            assertThat(limits.get("maxFieldsPerCollection")).isEqualTo(500);
            assertThat(limits.get("maxWorkflows")).isEqualTo(50);
            assertThat(limits.get("maxReports")).isEqualTo(200);

            // Usage metrics
            assertThat(body.get("apiCallsUsed")).isEqualTo(0);
            assertThat(body.get("apiCallsLimit")).isEqualTo(100_000);
            assertThat(body.get("usersUsed")).isEqualTo(5);
            assertThat(body.get("usersLimit")).isEqualTo(100);
            assertThat(body.get("collectionsUsed")).isEqualTo(12);
            assertThat(body.get("collectionsLimit")).isEqualTo(200);
        }

        @Test
        @DisplayName("Should return configured limits from tenant")
        void returnsConfiguredLimits() {
            String limitsJson = "{\"apiCallsPerDay\":50000,\"storageGb\":50,\"maxUsers\":500,\"maxCollections\":100,\"maxFieldsPerCollection\":1000,\"maxWorkflows\":200,\"maxReports\":500}";
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow(limitsJson)));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(25);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(8);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) body.get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(50_000);
            assertThat(limits.get("storageGb")).isEqualTo(50);
            assertThat(limits.get("maxUsers")).isEqualTo(500);
            assertThat(limits.get("maxCollections")).isEqualTo(100);
            assertThat(limits.get("maxFieldsPerCollection")).isEqualTo(1000);
            assertThat(limits.get("maxWorkflows")).isEqualTo(200);
            assertThat(limits.get("maxReports")).isEqualTo(500);

            assertThat(body.get("usersUsed")).isEqualTo(25);
            assertThat(body.get("usersLimit")).isEqualTo(500);
            assertThat(body.get("collectionsUsed")).isEqualTo(8);
            assertThat(body.get("collectionsLimit")).isEqualTo(100);
        }

        @Test
        @DisplayName("Should fill defaults for missing limit keys")
        void fillsDefaultsForMissingKeys() {
            // Only apiCallsPerDay is set, rest should be defaults
            String limitsJson = "{\"apiCallsPerDay\":75000}";
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow(limitsJson)));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) response.getBody().get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(75_000);
            assertThat(limits.get("storageGb")).isEqualTo(10); // default
            assertThat(limits.get("maxUsers")).isEqualTo(100); // default
            assertThat(limits.get("maxCollections")).isEqualTo(200); // default
        }

        @Test
        @DisplayName("Should handle tenant not found")
        void handlesTenantNotFound() {
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("nonexistent")))
                    .thenReturn(List.of());
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("nonexistent")))
                    .thenReturn(0);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("nonexistent")))
                    .thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("nonexistent");

            // Should return defaults
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) response.getBody().get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(100_000);
        }

        @Test
        @DisplayName("Should handle malformed limits JSON")
        void handlesMalformedJson() {
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow("{invalid}")));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(3);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) response.getBody().get("limits");
            // Should fall back to defaults
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(100_000);
            assertThat(limits.get("maxUsers")).isEqualTo(100);
        }

        @Test
        @DisplayName("Should handle limits as Map object (JDBC driver returns Map)")
        void handlesLimitsAsMap() {
            Map<String, Object> limitsMap = new HashMap<>();
            limitsMap.put("apiCallsPerDay", 60000);
            limitsMap.put("maxUsers", 250);

            Map<String, Object> row = new HashMap<>();
            row.put("limits", limitsMap);

            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(row));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(10);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(20);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) response.getBody().get("limits");
            assertThat(limits.get("apiCallsPerDay")).isEqualTo(60_000);
            assertThat(limits.get("maxUsers")).isEqualTo(250);
            assertThat(limits.get("storageGb")).isEqualTo(10); // default for missing key
        }

        @Test
        @DisplayName("Should return zero apiCallsUsed when Redis key is absent")
        void returnsZeroApiCallsUsedWhenRedisEmpty() {
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow("{}")));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(response.getBody().get("apiCallsUsed")).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return daily API call count from Redis")
        void returnsDailyApiCallCountFromRedis() {
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow("{}")));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);

            // Mock Redis returning a daily count
            String today = LocalDate.now(ZoneOffset.UTC).toString();
            when(valueOps.get("api-calls-daily:tenant-1:" + today)).thenReturn("4523");

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(response.getBody().get("apiCallsUsed")).isEqualTo(4523);
        }

        @Test
        @DisplayName("Should return zero when Redis throws exception")
        void returnsZeroWhenRedisThrows() {
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow("{}")));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(0);

            // Mock Redis failure
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

            ResponseEntity<Map<String, Object>> response = controller.getStatus("tenant-1");

            assertThat(response.getBody().get("apiCallsUsed")).isEqualTo(0);
        }
    }

    // ==================== PUT Tests ====================

    @Nested
    @DisplayName("PUT /api/collections/governor-limits")
    class UpdateLimitsTests {

        @Test
        @DisplayName("Should update tenant limits")
        void updatesTenantLimits() {
            // Given: update request
            Map<String, Object> newLimits = new LinkedHashMap<>();
            newLimits.put("apiCallsPerDay", 200_000);
            newLimits.put("storageGb", 100);
            newLimits.put("maxUsers", 1000);
            newLimits.put("maxCollections", 500);
            newLimits.put("maxFieldsPerCollection", 1000);
            newLimits.put("maxWorkflows", 100);
            newLimits.put("maxReports", 400);

            // Mock update
            when(jdbcTemplate.update(contains("UPDATE tenant"), anyString(), eq("tenant-1")))
                    .thenReturn(1);

            // Mock re-read for response
            String updatedJson = "{\"apiCallsPerDay\":200000,\"storageGb\":100,\"maxUsers\":1000,\"maxCollections\":500,\"maxFieldsPerCollection\":1000,\"maxWorkflows\":100,\"maxReports\":400}";
            when(jdbcTemplate.queryForList(contains("FROM tenant"), eq("tenant-1")))
                    .thenReturn(List.of(tenantLimitsRow(updatedJson)));
            when(jdbcTemplate.queryForObject(contains("FROM platform_user"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(50);
            when(jdbcTemplate.queryForObject(contains("FROM collection"), eq(Integer.class), eq("tenant-1")))
                    .thenReturn(30);

            // When
            ResponseEntity<Map<String, Object>> response = controller.updateLimits("tenant-1", newLimits);

            // Then
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

            // Verify update was called
            verify(jdbcTemplate).update(contains("UPDATE tenant"), anyString(), eq("tenant-1"));

            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("usersUsed")).isEqualTo(50);
            assertThat(body.get("collectionsUsed")).isEqualTo(30);
        }
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> tenantLimitsRow(String limitsJson) {
        Map<String, Object> row = new HashMap<>();
        row.put("limits", limitsJson);
        return row;
    }
}
