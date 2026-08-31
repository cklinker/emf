package io.kelta.worker.service.billing;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.module.service.EntitlementProvider;
import io.kelta.runtime.module.service.MemberEntitlements;
import io.kelta.runtime.module.service.ModuleServiceRegistry;
import io.kelta.worker.service.TenantSlugResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Prefers a tenant's module-provided {@link EntitlementProvider}, falling back to the compiled-in
 * {@link EntitlementServiceImpl}.
 *
 * <p>Every caller of {@link EntitlementService} keeps its existing signature and needs no change:
 * {@code AlertDispatchService}, {@code WatchController} and the quota hook ask the same questions
 * and simply reach a different answerer on tenants that have installed a billing module. A tenant
 * with no such module resolves nothing here and behaves exactly as before.
 *
 * <h2>Why the tenant is bound here and not in the module</h2>
 * A module implementation reads its data through the query engine, which resolves the tenant's
 * schema from {@code TenantContext}'s <b>slug</b>, not its id. A module cannot look a slug up —
 * {@link TenantSlugResolver} is platform-side — and a null slug silently reads the public schema
 * instead of the tenant's, which is exactly how module webhook dispatch broke once. So the platform
 * establishes the context around the call.
 *
 * <p>The binding is skipped when the ambient context already matches, since worker request paths
 * arrive with it pre-bound; rebinding there would risk replacing a correct slug with a failed
 * lookup.
 *
 * <h2>No caching</h2>
 * Module results are deliberately not cached. A module cannot consume the NATS invalidation that
 * keeps {@link EntitlementServiceImpl}'s cache honest, and a stale entitlement — a member who has
 * cancelled still being served, or an upgrade not taking effect — is worse than a slower lookup.
 *
 * @since 1.0.0
 */
@Service
@Primary
public class ModuleAwareEntitlementService implements EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(ModuleAwareEntitlementService.class);

    private final EntitlementService compiledIn;
    private final ModuleServiceRegistry moduleServices;
    private final TenantSlugResolver tenantSlugResolver;

    public ModuleAwareEntitlementService(EntitlementServiceImpl compiledIn,
                                         @Nullable ModuleServiceRegistry moduleServices,
                                         @Nullable TenantSlugResolver tenantSlugResolver) {
        this.compiledIn = compiledIn;
        this.moduleServices = moduleServices;
        this.tenantSlugResolver = tenantSlugResolver;
    }

    @Override
    public MemberEntitlements resolve(String tenantId, String userId) {
        Optional<EntitlementProvider> provider = moduleProvider(tenantId);
        if (provider.isEmpty()) {
            return compiledIn.resolve(tenantId, userId);
        }
        try {
            MemberEntitlements resolved = callInTenant(tenantId, provider.get(), userId);
            // A module returning null would otherwise NPE somewhere downstream in the alert path;
            // treat a broken contract the same as a broken module -- restrictively, not fatally.
            return resolved == null ? MemberEntitlements.EMPTY : resolved;
        } catch (RuntimeException e) {
            // Entitlements gate alert delivery and watch creation. A module that throws must not
            // take those paths down, and must not silently widen access either, so fall back to
            // the compiled-in answer and make the failure loud.
            log.error("Module EntitlementProvider failed for tenant {} member {}; "
                    + "falling back to compiled-in entitlements", tenantId, userId, e);
            return compiledIn.resolve(tenantId, userId);
        }
    }

    @Override
    public int intLimit(String tenantId, String userId, String key, int deflt) {
        return resolve(tenantId, userId).intValue(key, deflt);
    }

    @Override
    public boolean boolLimit(String tenantId, String userId, String key, boolean deflt) {
        return resolve(tenantId, userId).boolValue(key, deflt);
    }

    @Override
    public List<String> listLimit(String tenantId, String userId, String key) {
        return resolve(tenantId, userId).listValue(key);
    }

    @Override
    public void invalidate(String tenantId, String userId) {
        // Only the compiled-in path caches; a module's answers are always read fresh.
        compiledIn.invalidate(tenantId, userId);
    }

    @Override
    public void invalidateTenant(String tenantId) {
        compiledIn.invalidateTenant(tenantId);
    }

    /** Resolved per call: a module can be disabled at runtime, and its ClassLoader closed with it. */
    private Optional<EntitlementProvider> moduleProvider(String tenantId) {
        if (moduleServices == null || tenantId == null) {
            return Optional.empty();
        }
        return moduleServices.find(tenantId, EntitlementProvider.class);
    }

    /**
     * Runs the provider with the tenant bound, unless the ambient context already is that tenant
     * with a slug — worker request paths arrive pre-bound, and rebinding could replace a good slug
     * with a failed lookup.
     */
    private MemberEntitlements callInTenant(String tenantId, EntitlementProvider provider,
                                            String userId) {
        if (tenantId.equals(TenantContext.get()) && TenantContext.getSlug() != null) {
            return provider.resolve(tenantId, userId);
        }
        String slug = tenantSlugResolver == null
                ? null : tenantSlugResolver.resolveSlug(tenantId).orElse(null);
        if (slug == null) {
            // Without a slug the query engine reads the public schema rather than the tenant's,
            // which would answer from the wrong data instead of failing. Refuse the module path.
            log.error("No tenant slug for {}; cannot call the module EntitlementProvider safely, "
                    + "falling back to compiled-in entitlements", tenantId);
            return compiledIn.resolve(tenantId, userId);
        }
        return TenantContext.callWithTenant(tenantId, slug, () -> provider.resolve(tenantId, userId));
    }
}
