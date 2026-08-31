package io.kelta.worker.service.billing;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.module.service.EntitlementProvider;
import io.kelta.runtime.module.service.MemberEntitlements;
import io.kelta.runtime.module.service.ModuleServiceRegistry;
import io.kelta.worker.service.TenantSlugResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Entitlement resolution prefers a tenant's installed billing module and falls back to the
 * compiled-in service — without any caller changing.
 *
 * <p>The case worth the most here is the tenant binding. A module implementation reads through the
 * query engine, which resolves the tenant's <b>schema</b> from {@code TenantContext}'s slug, not its
 * id. A null slug does not fail: it quietly reads the public schema, so the module would answer
 * from the wrong data — the exact shape of the bug that broke module webhook dispatch, where the
 * original test asserted the id was set and passed against it.
 */
@DisplayName("ModuleAwareEntitlementService")
class ModuleAwareEntitlementServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String SLUG = "acme";
    private static final String MEMBER = "user-1";

    private EntitlementServiceImpl compiledIn;
    private ModuleServiceRegistry registry;
    private TenantSlugResolver slugResolver;
    private ModuleAwareEntitlementService service;

    @BeforeEach
    void setUp() {
        compiledIn = mock(EntitlementServiceImpl.class);
        registry = new ModuleServiceRegistry();
        slugResolver = mock(TenantSlugResolver.class);
        when(slugResolver.resolveSlug(TENANT)).thenReturn(Optional.of(SLUG));
        service = new ModuleAwareEntitlementService(compiledIn, registry, slugResolver);
    }

    private static MemberEntitlements pro() {
        return new MemberEntitlements("PRO", "active",
                Map.of("watches", 25, "channels", List.of("email", "sms")));
    }

    @Test
    @DisplayName("with no module installed, the compiled-in service answers unchanged")
    void fallsBackWhenNoModule() {
        when(compiledIn.resolve(TENANT, MEMBER)).thenReturn(pro());

        assertThat(service.resolve(TENANT, MEMBER).planCode()).isEqualTo("PRO");
        verify(compiledIn).resolve(TENANT, MEMBER);
    }

    @Test
    @DisplayName("a module-published provider takes precedence over the compiled-in service")
    void modulePrecedesCompiledIn() {
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> pro());

        assertThat(service.intLimit(TENANT, MEMBER, "watches", 3)).isEqualTo(25);
        assertThat(service.listLimit(TENANT, MEMBER, "channels")).containsExactly("email", "sms");
        verify(compiledIn, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("the provider is called with the tenant SLUG bound, not just the id")
    void bindsTenantSlugAroundTheCall() {
        AtomicReference<String> seenId = new AtomicReference<>();
        AtomicReference<String> seenSlug = new AtomicReference<>();
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> {
            seenId.set(TenantContext.get());
            seenSlug.set(TenantContext.getSlug());
            return pro();
        });

        service.resolve(TENANT, MEMBER);

        assertThat(seenId).hasValue(TENANT);
        // Asserting the id alone would pass even with the schema resolution broken.
        assertThat(seenSlug).hasValue(SLUG);
    }

    @Test
    @DisplayName("an unresolvable slug refuses the module rather than reading the public schema")
    void refusesModuleWhenSlugUnknown() {
        when(slugResolver.resolveSlug(TENANT)).thenReturn(Optional.empty());
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> pro());
        when(compiledIn.resolve(TENANT, MEMBER)).thenReturn(MemberEntitlements.EMPTY);

        // Answering from the wrong schema is worse than answering conservatively.
        assertThat(service.resolve(TENANT, MEMBER)).isEqualTo(MemberEntitlements.EMPTY);
        verify(compiledIn).resolve(TENANT, MEMBER);
    }

    @Test
    @DisplayName("an already-bound matching context is not rebound")
    void doesNotRebindAnAlreadyBoundContext() {
        AtomicReference<String> seenSlug = new AtomicReference<>();
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> {
            seenSlug.set(TenantContext.getSlug());
            return pro();
        });

        // Worker request paths arrive pre-bound; rebinding could replace a good slug with a failed
        // lookup, so the ambient context must win.
        TenantContext.runWithTenant(TENANT, "bound-by-the-request",
                () -> service.resolve(TENANT, MEMBER));

        assertThat(seenSlug).hasValue("bound-by-the-request");
        verify(slugResolver, never()).resolveSlug(any());
    }

    @Test
    @DisplayName("a throwing module falls back rather than taking the alert path down")
    void moduleFailureFallsBack() {
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> {
            throw new IllegalStateException("module blew up");
        });
        when(compiledIn.resolve(TENANT, MEMBER)).thenReturn(pro());

        // Entitlements gate alert delivery and watch creation; a broken module must not stop them,
        // and must not silently widen access either.
        assertThat(service.resolve(TENANT, MEMBER).planCode()).isEqualTo("PRO");
        verify(compiledIn).resolve(TENANT, MEMBER);
    }

    @Test
    @DisplayName("a module returning null resolves EMPTY rather than NPEing downstream")
    void nullFromModuleBecomesEmpty() {
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> null);

        assertThat(service.resolve(TENANT, MEMBER)).isEqualTo(MemberEntitlements.EMPTY);
        assertThat(service.intLimit(TENANT, MEMBER, "watches", 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("a module for one tenant never answers for another")
    void moduleIsTenantScoped() {
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> pro());
        when(compiledIn.resolve(eq("tenant-2"), any())).thenReturn(MemberEntitlements.EMPTY);

        assertThat(service.resolve("tenant-2", MEMBER)).isEqualTo(MemberEntitlements.EMPTY);
        verify(compiledIn).resolve("tenant-2", MEMBER);
    }

    @Test
    @DisplayName("invalidation stays with the compiled-in cache; module answers are never cached")
    void invalidationTargetsTheCompiledInCache() {
        registry.register(TENANT, EntitlementProvider.class, (EntitlementProvider) (t, u) -> pro());

        service.invalidate(TENANT, MEMBER);
        service.invalidateTenant(TENANT);

        // A module cannot consume the NATS invalidation, so its answers are read fresh every time
        // -- a stale entitlement is worse than a slower lookup.
        verify(compiledIn).invalidate(TENANT, MEMBER);
        verify(compiledIn).invalidateTenant(TENANT);
    }
}
