package io.kelta.worker.service.billing;

import io.kelta.runtime.module.service.ModuleServiceRegistry;
import io.kelta.worker.service.TenantSlugResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Bean wiring for entitlement resolution, checked in a real Spring context.
 *
 * <p>Nothing else in the worker suite starts a context, so a bean that cannot be constructed
 * compiles, passes every unit test, and fails only at pod startup — that has bitten this codebase
 * three times already ({@code concerns.md} → Test Coverage Gaps). Two things are worth pinning
 * here: that {@code EntitlementService} injection points get the module-aware wrapper, and that the
 * wrapper still constructs when runtime modules are switched off and no
 * {@link ModuleServiceRegistry} bean exists at all.
 */
@DisplayName("Entitlement service wiring")
class EntitlementServiceWiringTest {

    @Configuration
    static class CompiledInOnly {
        @Bean
        EntitlementServiceImpl entitlementServiceImpl() {
            return mock(EntitlementServiceImpl.class);
        }

        @Bean
        TenantSlugResolver tenantSlugResolver() {
            return mock(TenantSlugResolver.class);
        }
    }

    @Configuration
    static class WithModuleRegistry {
        @Bean
        ModuleServiceRegistry moduleServiceRegistry() {
            return new ModuleServiceRegistry();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CompiledInOnly.class)
            .withBean(ModuleAwareEntitlementService.class);

    @Test
    @DisplayName("EntitlementService resolves to the module-aware wrapper, not the compiled-in bean")
    void wrapperIsPrimary() {
        runner.withUserConfiguration(WithModuleRegistry.class).run(context -> {
            assertThat(context).hasNotFailed();
            // Every existing caller injects the interface; they must land on the wrapper without
            // any of them changing.
            assertThat(context.getBean(EntitlementService.class))
                    .isInstanceOf(ModuleAwareEntitlementService.class);
        });
    }

    @Test
    @DisplayName("still starts with runtime modules disabled, when no registry bean exists")
    void startsWithoutModuleRegistry() {
        // ModuleConfig is @ConditionalOnProperty(kelta.modules.runtime.enabled), so the registry
        // can legitimately be absent. A required dependency here would fail the pod, not a test.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EntitlementService.class))
                    .isInstanceOf(ModuleAwareEntitlementService.class);
        });
    }
}
