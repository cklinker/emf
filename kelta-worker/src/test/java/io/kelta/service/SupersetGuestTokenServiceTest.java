package io.kelta.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SupersetGuestTokenService")
class SupersetGuestTokenServiceTest {

    private SupersetApiClient apiClient;
    private JdbcTemplate jdbcTemplate;
    private SupersetGuestTokenService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(SupersetApiClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new SupersetGuestTokenService(apiClient, jdbcTemplate, "https://superset.rzware.com");
    }

    @Test
    @DisplayName("generates guest token with correct structure")
    void generatesGuestToken() {
        // Mock readable collections
        when(jdbcTemplate.queryForList(contains("profile_object_permission"), eq(String.class), anyString()))
                .thenReturn(List.of("accounts", "contacts"));

        // Mock VIEW_ALL_DATA check
        when(jdbcTemplate.queryForObject(contains("profile_system_permission"), eq(Integer.class), anyString(), anyString()))
                .thenReturn(0);

        // Mock guest token generation
        when(apiClient.generateGuestToken(anyString(), anyMap(), anyList()))
                .thenReturn("guest-token-123");

        Map<String, String> result = service.generateGuestToken(
                "dashboard-uuid", "profile-123", "user@example.com", "tenant-456"
        );

        assertNotNull(result);
        assertEquals("guest-token-123", result.get("token"));
        assertEquals("https://superset.rzware.com", result.get("supersetDomain"));
    }

    @Test
    @DisplayName("returns null when API client fails")
    void returnsNullOnFailure() {
        when(jdbcTemplate.queryForList(contains("profile_object_permission"), eq(String.class), anyString()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("profile_system_permission"), eq(Integer.class), anyString(), anyString()))
                .thenReturn(0);
        when(apiClient.generateGuestToken(anyString(), anyMap(), anyList()))
                .thenReturn(null);

        Map<String, String> result = service.generateGuestToken(
                "dashboard-uuid", "profile-123", "user@example.com", "tenant-456"
        );

        assertNull(result);
    }

    @Test
    @DisplayName("lists dashboards from API client")
    void listsDashboards() {
        var dashboards = List.<Map<String, Object>>of(
                Map.of("id", 1, "dashboard_title", "Sales Overview")
        );
        when(apiClient.listDashboards()).thenReturn(dashboards);

        var result = service.listDashboards();

        assertEquals(1, result.size());
        assertEquals("Sales Overview", result.get(0).get("dashboard_title"));
    }
}
