package io.kelta.gateway.health;

import io.kelta.gateway.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gateway accepts HTTP traffic before its route table exists, because
 * {@code RouteInitializer} is an {@code ApplicationRunner} and those run after
 * the web server starts. These tests pin the contract that readiness reports
 * that window honestly.
 */
@DisplayName("RouteReadinessHealthIndicator Tests")
class RouteReadinessHealthIndicatorTest {

    private RouteRegistry routeRegistry;
    private RouteReadinessHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        routeRegistry = mock(RouteRegistry.class);
        when(routeRegistry.size()).thenReturn(0);
        indicator = new RouteReadinessHealthIndicator(routeRegistry);
    }

    @Test
    @DisplayName("is DOWN before routes are initialized")
    void downBeforeInitialization() {
        Health health = indicator.health();

        assertThat(indicator.isReady()).isFalse();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "route table not yet initialized");
    }

    @Test
    @DisplayName("is UP once routes are initialized, reporting the route count")
    void upAfterInitialization() {
        when(routeRegistry.size()).thenReturn(57);

        indicator.markRoutesInitialized();
        Health health = indicator.health();

        assertThat(indicator.isReady()).isTrue();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("routeCount", 57);
    }

    @Test
    @DisplayName("readiness is one-way — a later empty registry does not flap it back DOWN")
    void readinessDoesNotFlapBack() {
        indicator.markRoutesInitialized();
        // A refresh that returns nothing is a worker problem; pulling this pod out
        // of the load balancer would make that outage worse, not better.
        when(routeRegistry.size()).thenReturn(0);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("marking ready repeatedly is harmless")
    void markingIsIdempotent() {
        indicator.markRoutesInitialized();
        indicator.markRoutesInitialized();

        assertThat(indicator.isReady()).isTrue();
    }

    @Test
    @DisplayName("reports the route count even while DOWN, so the log shows progress")
    void reportsCountWhileDown() {
        when(routeRegistry.size()).thenReturn(3);

        assertThat(indicator.health().getDetails()).containsEntry("routeCount", 3);
    }
}
