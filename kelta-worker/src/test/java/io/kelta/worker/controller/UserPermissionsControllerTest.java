package io.kelta.worker.controller;

import io.kelta.worker.cache.WorkerCacheManager;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserPermissionsController Tests")
class UserPermissionsControllerTest {

    private CerbosPermissionResolver permissionResolver;
    private JdbcTemplate jdbcTemplate;
    private WorkerCacheManager cacheManager;
    private UserPermissionsController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        permissionResolver = mock(CerbosPermissionResolver.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        cacheManager = mock(WorkerCacheManager.class);
        controller = new UserPermissionsController(permissionResolver, jdbcTemplate, cacheManager);
        request = mock(HttpServletRequest.class);
    }

    @Nested
    @DisplayName("getPermissions")
    class GetPermissions {

        @Test
        void shouldReturnEmptyPermissionsWhenNoProfileId() {
            when(permissionResolver.getProfileId(request)).thenReturn(null);

            var response = controller.getPermissions(request);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(Map.of(), response.getBody().get("systemPermissions"));
            assertEquals(Map.of(), response.getBody().get("objectPermissions"));
            assertEquals(Map.of(), response.getBody().get("fieldPermissions"));
        }

        @Test
        void shouldReturnEmptyPermissionsWhenBlankProfileId() {
            when(permissionResolver.getProfileId(request)).thenReturn("  ");

            var response = controller.getPermissions(request);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(Map.of(), response.getBody().get("systemPermissions"));
        }

        @Test
        void shouldReturnCachedPermissions() {
            when(permissionResolver.getProfileId(request)).thenReturn("prof-1");
            Map<String, Object> cached = Map.of(
                    "systemPermissions", Map.of("viewSetup", true),
                    "objectPermissions", Map.of(),
                    "fieldPermissions", Map.of());
            when(cacheManager.getPermissions("prof-1")).thenReturn(Optional.of(cached));

            var response = controller.getPermissions(request);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(cached, response.getBody());
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        void shouldQueryDatabaseOnCacheMiss() {
            when(permissionResolver.getProfileId(request)).thenReturn("prof-1");
            when(cacheManager.getPermissions("prof-1")).thenReturn(Optional.empty());

            var response = controller.getPermissions(request);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().containsKey("systemPermissions"));
            assertTrue(response.getBody().containsKey("objectPermissions"));
            assertTrue(response.getBody().containsKey("fieldPermissions"));
            verify(cacheManager).putPermissions(eq("prof-1"), any());
        }
    }
}
