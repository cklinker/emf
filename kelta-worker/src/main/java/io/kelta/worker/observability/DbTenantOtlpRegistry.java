package io.kelta.worker.observability;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kelta.runtime.context.TenantContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * DB-backed {@link TenantOtlpRegistry} (Rec 7) — the self-service source of per-tenant
 * OTLP targets, resolved from {@code tenant_otlp_target} and cached briefly so the hot
 * span-export path doesn't hit the DB per span. A tenant with no enabled DB row falls
 * back to the operator-configured {@link PropertiesTenantOtlpRegistry}.
 *
 * <p>Reads run under the target tenant's {@code TenantContext} so RLS returns its row;
 * the cache reloads on a short TTL, and the admin write path calls {@link #invalidate}
 * for immediate effect on the originating pod (other pods refresh within the TTL).
 */
@Component
@Primary
public class DbTenantOtlpRegistry implements TenantOtlpRegistry {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final TenantOtlpTargetRepository repository;
    private final PropertiesTenantOtlpRegistry fallback;
    private final Cache<String, Optional<OtlpTarget>> cache =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(10_000).build();

    public DbTenantOtlpRegistry(TenantOtlpTargetRepository repository, PropertiesTenantOtlpRegistry fallback) {
        this.repository = repository;
        this.fallback = fallback;
    }

    @Override
    public Optional<OtlpTarget> targetFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return cache.get(tenantId, this::load);
    }

    /** Drop a tenant's cached target so the next lookup re-reads the DB. */
    public void invalidate(String tenantId) {
        cache.invalidate(tenantId);
    }

    private Optional<OtlpTarget> load(String tenantId) {
        Optional<TenantOtlpTargetRepository.StoredTarget> stored =
                TenantContext.callWithTenant(tenantId, () -> repository.find(tenantId));
        if (stored.isPresent() && stored.get().enabled()
                && stored.get().endpoint() != null && !stored.get().endpoint().isBlank()) {
            return Optional.of(new OtlpTarget(stored.get().endpoint(), stored.get().headers()));
        }
        return fallback.targetFor(tenantId);
    }
}
