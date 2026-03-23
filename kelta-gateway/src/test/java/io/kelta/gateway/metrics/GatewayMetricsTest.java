package io.kelta.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the centralized GatewayMetrics service.
 * Uses SimpleMeterRegistry (in-memory) to verify metric recording.
 */
@DisplayName("GatewayMetrics Tests")
class GatewayMetricsTest {

    private SimpleMeterRegistry registry;
    private GatewayMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new GatewayMetrics(registry);
    }

    @Nested
    @DisplayName("kelta.gateway.requests (Timer)")
    class RequestTimer {

        @Test
        @DisplayName("Should record request duration with all tags")
        void shouldRecordRequestDuration() {
            metrics.recordRequest("acme", "GET", "200", "users", Duration.ofMillis(150));

            Timer timer = registry.find("kelta.gateway.requests")
                    .tag("tenant", "acme")
                    .tag("method", "GET")
                    .tag("status", "200")
                    .tag("route", "users")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(150);
        }

        @Test
        @DisplayName("Should use 'unknown' for null tag values")
        void shouldUseUnknownForNullTags() {
            metrics.recordRequest(null, null, null, null, Duration.ofMillis(10));

            Timer timer = registry.find("kelta.gateway.requests")
                    .tag("tenant", "unknown")
                    .tag("method", "unknown")
                    .tag("status", "unknown")
                    .tag("route", "unknown")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should accumulate multiple requests")
        void shouldAccumulateMultipleRequests() {
            metrics.recordRequest("acme", "GET", "200", "users", Duration.ofMillis(100));
            metrics.recordRequest("acme", "GET", "200", "users", Duration.ofMillis(200));

            Timer timer = registry.find("kelta.gateway.requests")
                    .tag("tenant", "acme")
                    .tag("method", "GET")
                    .tag("status", "200")
                    .tag("route", "users")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should separate metrics by tenant")
        void shouldSeparateByTenant() {
            metrics.recordRequest("acme", "GET", "200", "users", Duration.ofMillis(100));
            metrics.recordRequest("globex", "GET", "200", "users", Duration.ofMillis(100));

            Timer acmeTimer = registry.find("kelta.gateway.requests")
                    .tag("tenant", "acme").timer();
            Timer globexTimer = registry.find("kelta.gateway.requests")
                    .tag("tenant", "globex").timer();

            assertThat(acmeTimer).isNotNull();
            assertThat(globexTimer).isNotNull();
            assertThat(acmeTimer.count()).isEqualTo(1);
            assertThat(globexTimer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.requests.active (Gauge)")
    class ActiveRequestsGauge {

        @Test
        @DisplayName("Should increment and decrement active requests")
        void shouldIncrementAndDecrement() {
            metrics.incrementActiveRequests("acme");
            metrics.incrementActiveRequests("acme");

            Gauge gauge = registry.find("kelta.gateway.requests.active")
                    .tag("tenant", "acme").gauge();

            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(2.0);

            metrics.decrementActiveRequests("acme");
            assertThat(gauge.value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should separate gauges by tenant")
        void shouldSeparateByTenant() {
            metrics.incrementActiveRequests("acme");
            metrics.incrementActiveRequests("globex");
            metrics.incrementActiveRequests("globex");

            Gauge acmeGauge = registry.find("kelta.gateway.requests.active")
                    .tag("tenant", "acme").gauge();
            Gauge globexGauge = registry.find("kelta.gateway.requests.active")
                    .tag("tenant", "globex").gauge();

            assertThat(acmeGauge).isNotNull();
            assertThat(globexGauge).isNotNull();
            assertThat(acmeGauge.value()).isEqualTo(1.0);
            assertThat(globexGauge.value()).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.auth.failures (Counter)")
    class AuthFailuresCounter {

        @Test
        @DisplayName("Should record auth failure with tenant and reason")
        void shouldRecordAuthFailure() {
            metrics.recordAuthFailure("acme", "missing_token");

            Counter counter = registry.find("kelta.gateway.auth.failures")
                    .tag("tenant", "acme")
                    .tag("reason", "missing_token")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should separate by reason")
        void shouldSeparateByReason() {
            metrics.recordAuthFailure("acme", "missing_token");
            metrics.recordAuthFailure("acme", "invalid_token");
            metrics.recordAuthFailure("acme", "invalid_token");

            Counter missing = registry.find("kelta.gateway.auth.failures")
                    .tag("reason", "missing_token").counter();
            Counter invalid = registry.find("kelta.gateway.auth.failures")
                    .tag("reason", "invalid_token").counter();

            assertThat(missing.count()).isEqualTo(1.0);
            assertThat(invalid.count()).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.authz.denied (Counter)")
    class AuthzDeniedCounter {

        @Test
        @DisplayName("Should record authz denied with all tags")
        void shouldRecordAuthzDenied() {
            metrics.recordAuthzDenied("acme", "users", "DELETE");

            Counter counter = registry.find("kelta.gateway.authz.denied")
                    .tag("tenant", "acme")
                    .tag("route", "users")
                    .tag("method", "DELETE")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.ratelimit.exceeded (Counter)")
    class RateLimitExceededCounter {

        @Test
        @DisplayName("Should record rate limit exceeded for tenant")
        void shouldRecordRateLimitExceeded() {
            metrics.recordRateLimitExceeded("acme");
            metrics.recordRateLimitExceeded("acme");

            Counter counter = registry.find("kelta.gateway.ratelimit.exceeded")
                    .tag("tenant", "acme")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.ratelimit.remaining.ratio (Gauge)")
    class RateLimitRemainingGauge {

        @Test
        @DisplayName("Should record remaining ratio as percentage")
        void shouldRecordRemainingRatio() {
            metrics.recordRateLimitRemaining("acme", 50, 100);

            Gauge gauge = registry.find("kelta.gateway.ratelimit.remaining.ratio")
                    .tag("tenant", "acme").gauge();

            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("Should handle zero limit without division error")
        void shouldHandleZeroLimit() {
            metrics.recordRateLimitRemaining("acme", 0, 0);

            Gauge gauge = registry.find("kelta.gateway.ratelimit.remaining.ratio")
                    .tag("tenant", "acme").gauge();

            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should update gauge value on subsequent calls")
        void shouldUpdateGaugeValue() {
            metrics.recordRateLimitRemaining("acme", 80, 100);

            Gauge gauge = registry.find("kelta.gateway.ratelimit.remaining.ratio")
                    .tag("tenant", "acme").gauge();
            assertThat(gauge.value()).isEqualTo(80.0);

            metrics.recordRateLimitRemaining("acme", 20, 100);
            assertThat(gauge.value()).isEqualTo(20.0);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.permissions.resolve (Timer)")
    class PermissionsResolveTimer {

        @Test
        @DisplayName("Should record cache hit duration")
        void shouldRecordCacheHit() {
            metrics.recordPermissionResolve("tenant-123", "cache", Duration.ofMillis(5));

            Timer timer = registry.find("kelta.gateway.permissions.resolve")
                    .tag("tenant", "tenant-123")
                    .tag("source", "cache")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should record worker fetch duration")
        void shouldRecordWorkerFetch() {
            metrics.recordPermissionResolve("tenant-123", "worker", Duration.ofMillis(250));

            Timer timer = registry.find("kelta.gateway.permissions.resolve")
                    .tag("tenant", "tenant-123")
                    .tag("source", "worker")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.tenant.resolution (Counter)")
    class TenantResolutionCounter {

        @Test
        @DisplayName("Should record slug resolution success")
        void shouldRecordSlugSuccess() {
            metrics.recordTenantResolution("slug", "success");

            Counter counter = registry.find("kelta.gateway.tenant.resolution")
                    .tag("method", "slug")
                    .tag("result", "success")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should record header resolution success")
        void shouldRecordHeaderSuccess() {
            metrics.recordTenantResolution("header", "success");

            Counter counter = registry.find("kelta.gateway.tenant.resolution")
                    .tag("method", "header")
                    .tag("result", "success")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should record no tenant context")
        void shouldRecordNoTenantContext() {
            metrics.recordTenantResolution("none", "skipped");

            Counter counter = registry.find("kelta.gateway.tenant.resolution")
                    .tag("method", "none")
                    .tag("result", "skipped")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("kelta.gateway.errors (Counter)")
    class ErrorsCounter {

        @Test
        @DisplayName("Should record error with all tags")
        void shouldRecordError() {
            metrics.recordError("acme", "401", "UNAUTHORIZED");

            Counter counter = registry.find("kelta.gateway.errors")
                    .tag("tenant", "acme")
                    .tag("status", "401")
                    .tag("error_code", "UNAUTHORIZED")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should separate by error code")
        void shouldSeparateByErrorCode() {
            metrics.recordError("acme", "401", "UNAUTHORIZED");
            metrics.recordError("acme", "403", "FORBIDDEN");
            metrics.recordError("acme", "429", "RATE_LIMIT_EXCEEDED");

            assertThat(registry.find("kelta.gateway.errors")
                    .tag("error_code", "UNAUTHORIZED").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("kelta.gateway.errors")
                    .tag("error_code", "FORBIDDEN").counter().count()).isEqualTo(1.0);
            assertThat(registry.find("kelta.gateway.errors")
                    .tag("error_code", "RATE_LIMIT_EXCEEDED").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should use 'unknown' for null tenant")
        void shouldUseUnknownForNullTenant() {
            metrics.recordError(null, "500", "INTERNAL_ERROR");

            Counter counter = registry.find("kelta.gateway.errors")
                    .tag("tenant", "unknown")
                    .tag("status", "500")
                    .tag("error_code", "INTERNAL_ERROR")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }
}
