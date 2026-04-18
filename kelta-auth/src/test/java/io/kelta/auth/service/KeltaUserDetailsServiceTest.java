package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeltaUserDetailsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private KeltaUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new KeltaUserDetailsService(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // --- No request context → must fail hard (RLS invariant) ---

    @Test
    void loadUserByUsername_throwsWhenNoRequestContext() {
        // No RequestContextHolder attributes at all → no session → no tenant
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("admin@test.com"));
    }

    @Test
    void loadUserByUsername_throwsWhenNoTenantInSession() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("admin@test.com"));
    }

    // --- Tenant UUID in session → filters query by tenant ---

    @Test
    void loadUserByUsername_filtersbyTenantUuidFromSession() {
        String tenantId = "07078e7d-2e0c-4892-90f2-8b2906a47f3c";
        setSessionTenant(tenantId);

        KeltaUserDetails expectedUser = buildUser(tenantId);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("admin@test.com"), eq("admin@test.com"), eq(tenantId)))
                .thenReturn(List.of(expectedUser));

        var result = userDetailsService.loadUserByUsername("admin@test.com");

        assertNotNull(result);
        assertEquals(tenantId, ((KeltaUserDetails) result).getTenantId());
    }

    @Test
    void loadUserByUsername_throwsWhenNotFoundInTenant() {
        String tenantId = "07078e7d-2e0c-4892-90f2-8b2906a47f3c";
        setSessionTenant(tenantId);

        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("admin@test.com"), eq("admin@test.com"), eq(tenantId)))
                .thenReturn(Collections.emptyList());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("admin@test.com"));
    }

    // --- Tenant slug in session → resolved to UUID, then filters ---

    @Test
    void loadUserByUsername_resolvesTenantSlugAndFilters() {
        String slug = "default";
        String tenantId = "07078e7d-2e0c-4892-90f2-8b2906a47f3c";
        setSessionTenant(slug);

        when(jdbcTemplate.queryForList("SELECT id FROM tenant WHERE slug = ?", String.class, slug))
                .thenReturn(List.of(tenantId));

        KeltaUserDetails expectedUser = buildUser(tenantId);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("admin@test.com"), eq("admin@test.com"), eq(tenantId)))
                .thenReturn(List.of(expectedUser));

        var result = userDetailsService.loadUserByUsername("admin@test.com");

        assertNotNull(result);
        assertEquals(tenantId, ((KeltaUserDetails) result).getTenantId());
    }

    @Test
    void loadUserByUsername_throwsWhenSlugUnknown() {
        setSessionTenant("unknown-slug");

        when(jdbcTemplate.queryForList("SELECT id FROM tenant WHERE slug = ?", String.class, "unknown-slug"))
                .thenReturn(Collections.emptyList());

        // Unknown slug → no tenant context → hard fail (no cross-tenant fallback).
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("admin@test.com"));
    }

    // --- Helpers ---

    private KeltaUserDetails buildUser(String tenantId) {
        return new KeltaUserDetails(
                "user-1", "admin@test.com", tenantId, "profile-1",
                "System Administrator", "Test Admin", "$2a$10$hash",
                true, false, false
        );
    }

    private void setSessionTenant(String tenantValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("tenantId")).thenReturn(tenantValue);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
