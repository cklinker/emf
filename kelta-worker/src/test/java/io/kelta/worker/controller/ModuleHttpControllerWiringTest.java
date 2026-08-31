package io.kelta.worker.controller;

import io.kelta.worker.module.RuntimeModuleManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The module route controller must be registered whether or not bean scan order cooperates.
 *
 * <p>It was originally gated with {@code @ConditionalOnBean(RuntimeModuleManager.class)}, copying
 * the two module controllers beside it. That annotation is evaluated during component scanning,
 * before every {@code @Configuration} bean method has been registered, so whether it matches depends
 * on scan order — and this controller lost that race in the deployed image. The symptom was awful:
 * every module route returned 404 while the module reported {@code ACTIVE} with its routes
 * registered and its handlers loaded, so nothing anywhere pointed at the controller being absent.
 *
 * <p>Nothing else in the worker suite starts a context, which is why this reached a deployed
 * environment before it was noticed.
 */
@DisplayName("ModuleHttpController wiring")
class ModuleHttpControllerWiringTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withBean(ModuleHttpController.class);

    @Test
    @DisplayName("registers when the module manager is present")
    void registersWithManager() {
        runner.withBean(RuntimeModuleManager.class, () -> mock(RuntimeModuleManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ModuleHttpController.class);
                });
    }

    @Test
    @DisplayName("still registers with no module manager, rather than vanishing")
    void registersWithoutManager() {
        // Module support can legitimately be switched off (ModuleConfig is
        // @ConditionalOnProperty). The controller must still exist and answer 404 — the previous
        // behaviour was for the whole controller to disappear, which is indistinguishable from a
        // route that was never declared.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ModuleHttpController.class);
        });
    }
}
