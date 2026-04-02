package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.cache.WorkerCacheManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantDomainController Tests")
class TenantDomainControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private TenantDomainController controller;

    @BeforeEach
    void setUp() {
        WorkerCacheManager cacheManager = new WorkerCacheManager(new SimpleMeterRegistry());
        controller = new TenantDomainController(jdbcTemplate, cacheManager);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRegisterValidDomain() {
        when(jdbcTemplate.queryForList(contains("tenant_custom_domain WHERE domain"), eq("app.acme.com")))
                .thenReturn(List.of());

        var response = controller.registerDomain(Map.of("domain", "app.acme.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void shouldRejectDuplicateDomain() {
        when(jdbcTemplate.queryForList(contains("tenant_custom_domain WHERE domain"), eq("taken.com")))
                .thenReturn(List.of(Map.of("id", "existing")));

        var response = controller.registerDomain(Map.of("domain", "taken.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldRejectReservedDomain() {
        var response = controller.registerDomain(Map.of("domain", "app.kelta.io"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectInvalidFormat() {
        var response = controller.registerDomain(Map.of("domain", "not a domain!"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectBlankDomain() {
        var response = controller.registerDomain(Map.of("domain", ""));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
