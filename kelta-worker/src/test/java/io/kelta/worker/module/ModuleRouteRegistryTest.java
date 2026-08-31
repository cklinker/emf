package io.kelta.worker.module;

import io.kelta.runtime.module.ModuleManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModuleRouteRegistry")
class ModuleRouteRegistryTest {

    private final ModuleRouteRegistry registry = new ModuleRouteRegistry();

    private static ModuleManifest.RouteManifest route(String path, String key, String... methods) {
        return new ModuleManifest.RouteManifest(path,
                methods.length == 0 ? List.of("GET") : List.of(methods), key);
    }

    @Test
    @DisplayName("resolves a declared route, and only for the tenant that installed the module")
    void resolvesPerTenant() {
        registry.register("t1", "billing", List.of(route("/plans", "billing:list-plans")));

        assertThat(registry.resolve("t1", "billing", "GET", "/plans")).contains("billing:list-plans");
        // A module installed by one tenant must never serve another's request.
        assertThat(registry.resolve("t2", "billing", "GET", "/plans")).isEmpty();
    }

    @Test
    @DisplayName("the method is part of the route, so GET and POST can differ")
    void methodIsPartOfTheRoute() {
        registry.register("t1", "billing", List.of(
                route("/sessions", "billing:list-sessions", "GET"),
                route("/sessions", "billing:create-session", "POST")));

        assertThat(registry.resolve("t1", "billing", "GET", "/sessions"))
                .contains("billing:list-sessions");
        assertThat(registry.resolve("t1", "billing", "POST", "/sessions"))
                .contains("billing:create-session");
        assertThat(registry.resolve("t1", "billing", "DELETE", "/sessions")).isEmpty();
    }

    @Test
    @DisplayName("an undeclared path resolves to nothing rather than to some other handler")
    void undeclaredPathResolvesEmpty() {
        registry.register("t1", "billing", List.of(route("/plans", "billing:list-plans")));

        assertThat(registry.resolve("t1", "billing", "GET", "/plans/secret")).isEmpty();
        assertThat(registry.resolve("t1", "billing", "GET", "/")).isEmpty();
    }

    @Test
    @DisplayName("a trailing slash is the same route, not a missing one")
    void trailingSlashIsTheSameRoute() {
        registry.register("t1", "billing", List.of(route("/plans", "billing:list-plans")));

        assertThat(registry.resolve("t1", "billing", "GET", "/plans/"))
                .contains("billing:list-plans");
    }

    @Test
    @DisplayName("unload removes the routes, so a disabled module stops answering immediately")
    void removeStopsResolution() {
        registry.register("t1", "billing", List.of(route("/plans", "billing:list-plans")));
        registry.register("t1", "other", List.of(route("/thing", "other:thing")));

        registry.remove("t1", "billing");

        assertThat(registry.resolve("t1", "billing", "GET", "/plans")).isEmpty();
        assertThat(registry.resolve("t1", "other", "GET", "/thing")).contains("other:thing");
    }

    @Test
    @DisplayName("re-registering replaces the previous set rather than merging into it")
    void reRegisterReplaces() {
        registry.register("t1", "billing", List.of(route("/old", "billing:old")));
        registry.register("t1", "billing", List.of(route("/new", "billing:new")));

        assertThat(registry.resolve("t1", "billing", "GET", "/old")).isEmpty();
        assertThat(registry.resolve("t1", "billing", "GET", "/new")).contains("billing:new");
        assertThat(registry.routeCount("t1", "billing")).isEqualTo(1);
    }
}
