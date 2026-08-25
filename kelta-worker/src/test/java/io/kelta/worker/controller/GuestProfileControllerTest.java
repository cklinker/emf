package io.kelta.worker.controller;

import io.kelta.worker.cache.WorkerCacheManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuestProfileController Tests")
class GuestProfileControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private GuestProfileController controller;

    @BeforeEach
    void setUp() {
        WorkerCacheManager cacheManager = new WorkerCacheManager(new SimpleMeterRegistry());
        controller = new GuestProfileController(jdbcTemplate, cacheManager);
    }

    @Test
    void shouldResolveGuestProfileFromDatabase() {
        when(jdbcTemplate.queryForList(contains("FROM profile"), eq(String.class), eq("tenant-1")))
                .thenReturn(List.of("guest-profile-id"));

        var response = controller.resolveGuestProfile("tenant-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("guest-profile-id");
    }

    @Test
    void shouldReturnNotFoundWhenTenantHasNoGuestProfile() {
        when(jdbcTemplate.queryForList(contains("FROM profile"), eq(String.class), eq("tenant-1")))
                .thenReturn(List.of());

        var response = controller.resolveGuestProfile("tenant-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldCacheNegativeLookupAndNotQueryDbAgain() {
        when(jdbcTemplate.queryForList(contains("FROM profile"), eq(String.class), eq("tenant-1")))
                .thenReturn(List.of());

        controller.resolveGuestProfile("tenant-1");
        var response = controller.resolveGuestProfile("tenant-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(jdbcTemplate, times(1))
                .queryForList(contains("FROM profile"), eq(String.class), eq("tenant-1"));
    }

    @Test
    void shouldCachePositiveLookupAndNotQueryDbAgain() {
        when(jdbcTemplate.queryForList(contains("FROM profile"), eq(String.class), eq("tenant-1")))
                .thenReturn(List.of("guest-profile-id"));

        controller.resolveGuestProfile("tenant-1");
        var response = controller.resolveGuestProfile("tenant-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("guest-profile-id");
        verify(jdbcTemplate, times(1))
                .queryForList(contains("FROM profile"), eq(String.class), eq("tenant-1"));
    }
}
