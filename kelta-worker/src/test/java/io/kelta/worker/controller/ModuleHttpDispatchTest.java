package io.kelta.worker.controller;

import io.kelta.runtime.module.ModuleManifestParser;
import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.ActionResult;
import io.kelta.worker.module.ModuleRouteRegistry;
import io.kelta.worker.module.RuntimeModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end route dispatch, with the billing module's real manifest.
 *
 * <p>On prod, POST routes dispatched to their handlers while GET routes returned 404, with all four
 * routes reported registered and the module ACTIVE on every pod. This exercises the same path —
 * manifest → registry → dispatch — so the asymmetry has somewhere to reproduce.
 */
@DisplayName("Module route dispatch")
class ModuleHttpDispatchTest {

    private static final String TENANT = "t1";

    private ActionHandlerRegistry handlers;
    private ModuleRouteRegistry routes;
    private RuntimeModuleManager manager;

    private static ActionHandler handler(String key) {
        return new ActionHandler() {
            @Override public String getActionTypeKey() { return key; }
            @Override public ActionResult execute(io.kelta.runtime.workflow.ActionContext c) {
                return ActionResult.success(Map.of("handled", key));
            }
        };
    }

    @BeforeEach
    void setUp() throws Exception {
        handlers = new ActionHandlerRegistry();
        routes = new ModuleRouteRegistry();

        String json = Files.readString(
                Path.of("../kelta-modules/billing/src/main/resources/kelta-module.json"));
        var manifest = new ModuleManifestParser(new ObjectMapper()).parse(json);
        routes.register(TENANT, "kelta-billing", manifest.routes());
        for (var r : manifest.routes()) {
            handlers.registerTenantHandler(TENANT, handler(r.handlerKey()));
        }

        ModuleStore store = mock(ModuleStore.class);
        when(store.findByTenantAndModuleId(any(), any())).thenReturn(java.util.Optional.of(
            new TenantModuleData("m1", TENANT, "kelta-billing", "Kelta Billing", "1.0.0", "d",
                "u", "ck", 1L, "C", json, TenantModuleData.STATUS_ACTIVE, "me",
                Instant.now(), Instant.now(), "s3/k.jar", List.of())));

        manager = new RuntimeModuleManager(store, handlers, new ObjectMapper(),
                null, null, null, null, null);
        manager.setModuleRouteRegistry(routes);
    }

    @Test
    @DisplayName("every declared route dispatches — GET as well as POST")
    void everyRouteDispatches() {
        assertThat(manager.dispatchRoute(TENANT, "kelta-billing", "u1", "GET", "/plans",
                Map.of(), null)).isPresent();
        assertThat(manager.dispatchRoute(TENANT, "kelta-billing", "u1", "GET", "/me",
                Map.of(), null)).isPresent();
        assertThat(manager.dispatchRoute(TENANT, "kelta-billing", "u1", "POST", "/checkout-sessions",
                Map.of(), "{}")).isPresent();
        assertThat(manager.dispatchRoute(TENANT, "kelta-billing", "u1", "POST", "/portal-sessions",
                Map.of(), "{}")).isPresent();
    }

    @Test
    @DisplayName("a method the route does not declare does not dispatch")
    void wrongMethodDoesNotDispatch() {
        assertThat(manager.dispatchRoute(TENANT, "kelta-billing", "u1", "POST", "/plans",
                Map.of(), "{}")).isEmpty();
    }
}
