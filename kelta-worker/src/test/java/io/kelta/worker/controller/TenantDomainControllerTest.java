package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.events.RecordEventPublisher;
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
    @Mock private RecordEventPublisher recordEventPublisher;
    private TenantDomainController controller;

    @BeforeEach
    void setUp() {
        WorkerCacheManager cacheManager = new WorkerCacheManager(new SimpleMeterRegistry());
        controller = new TenantDomainController(jdbcTemplate, cacheManager, recordEventPublisher);
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

    @Nested
    @DisplayName("resolveDomain Tests")
    class ResolveDomain {

        @Test
        void shouldResolveDomainFromDatabase() {
            when(jdbcTemplate.queryForList(contains("tenant_custom_domain tcd"), eq("app.acme.com")))
                    .thenReturn(List.of(Map.of("slug", "acme")));

            var response = controller.resolveDomain("app.acme.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("acme");
        }

        @Test
        void shouldReturnNotFoundForUnknownDomain() {
            when(jdbcTemplate.queryForList(contains("tenant_custom_domain tcd"), eq("unknown.com")))
                    .thenReturn(List.of());

            var response = controller.resolveDomain("unknown.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldCacheNegativeLookupAndNotQueryDbAgain() {
            when(jdbcTemplate.queryForList(contains("tenant_custom_domain tcd"), eq("unknown.com")))
                    .thenReturn(List.of());

            // First call: cache miss, queries DB
            controller.resolveDomain("unknown.com");

            // Second call: should use cached negative result, not query DB again
            var response = controller.resolveDomain("unknown.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // DB should only have been queried once
            org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.times(1))
                    .queryForList(contains("tenant_custom_domain tcd"), eq("unknown.com"));
        }

        @Test
        void shouldCachePositiveLookupAndNotQueryDbAgain() {
            when(jdbcTemplate.queryForList(contains("tenant_custom_domain tcd"), eq("app.acme.com")))
                    .thenReturn(List.of(Map.of("slug", "acme")));

            // First call: cache miss, queries DB
            controller.resolveDomain("app.acme.com");

            // Second call: should use cached result
            var response = controller.resolveDomain("app.acme.com");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("acme");

            // DB should only have been queried once
            org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.times(1))
                    .queryForList(contains("tenant_custom_domain tcd"), eq("app.acme.com"));
        }
    }
}
