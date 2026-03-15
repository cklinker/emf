package io.kelta.auth.service;

import io.kelta.auth.model.KeltaUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @Test
    void loadUserByUsername_returnsUserDetails() {
        KeltaUserDetails expectedUser = new KeltaUserDetails(
                "user-1", "admin@test.com", "tenant-1", "profile-1",
                "System Administrator", "Test Admin", "$2a$10$hash",
                true, false, false
        );

        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("admin@test.com")))
                .thenReturn(List.of(expectedUser));

        var result = userDetailsService.loadUserByUsername("admin@test.com");

        assertNotNull(result);
        assertEquals("admin@test.com", result.getUsername());
        assertTrue(result.isEnabled());
        assertTrue(result.isAccountNonLocked());
    }

    @Test
    void loadUserByUsername_throwsWhenNotFound() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("unknown@test.com")))
                .thenReturn(Collections.emptyList());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown@test.com"));
    }
}
