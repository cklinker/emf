package io.kelta.gateway.health;

import io.kelta.gateway.route.RouteRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reports the gateway NOT ready until its route table has been populated.
 *
 * <p><b>Why this exists.</b> {@code RouteInitializer} is an
 * {@link org.springframework.boot.ApplicationRunner}, and those run <em>after</em>
 * Spring Boot has started the web server. For the window between "listening" and
 * "routes loaded" the gateway accepts requests and answers
 * {@code /actuator/health} with UP while its route table is still empty — so every
 * {@code /api/**} request 404s. Loading is not instant either: it makes two
 * blocking calls to the worker (tenant-slug priming, then the bootstrap fetch).
 *
 * <p>That window is not just a test annoyance. A Kubernetes readiness probe
 * satisfied during it sends real traffic to a pod that cannot route it, so every
 * rollout serves 404s until each new pod catches up. In CI it showed up as the
 * first few minutes of Playwright specs failing and everything afterwards
 * passing.
 *
 * <p>Registered into the <b>readiness</b> group (see {@code application.yml}), so
 * {@code /actuator/health/readiness} returns 503 until routes exist.
 *
 * <p>The flag is one-way: once ready, the gateway stays ready. A later refresh
 * that returns zero routes is a worker problem, not a reason to pull this pod out
 * of the load balancer — and flapping readiness would be worse than serving a
 * stale route table.
 */
@Component
public class RouteReadinessHealthIndicator implements HealthIndicator {

    private final RouteRegistry routeRegistry;
    private final AtomicBoolean routesInitialized = new AtomicBoolean(false);

    public RouteReadinessHealthIndicator(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    /**
     * Marks the initial route load complete. Called by {@code RouteInitializer}
     * once, whether or not the bootstrap fetch succeeded: static routes are
     * registered unconditionally, so the gateway is still useful, and staying
     * permanently unready because the worker was briefly unreachable would turn a
     * transient failure into an outage.
     */
    public void markRoutesInitialized() {
        routesInitialized.set(true);
    }

    /** True once the initial route load has run. */
    public boolean isReady() {
        return routesInitialized.get();
    }

    @Override
    public Health health() {
        int routeCount = routeRegistry.size();
        if (!routesInitialized.get()) {
            return Health.down()
                    .withDetail("reason", "route table not yet initialized")
                    .withDetail("routeCount", routeCount)
                    .build();
        }
        return Health.up()
                .withDetail("routeCount", routeCount)
                .build();
    }
}
