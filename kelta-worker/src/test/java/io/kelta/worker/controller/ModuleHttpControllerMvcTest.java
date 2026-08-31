package io.kelta.worker.controller;

import io.kelta.runtime.workflow.ActionResult;
import io.kelta.worker.module.RuntimeModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The controller's own request mapping, which nothing covered.
 *
 * <p>On prod, POST routes dispatched while GET routes returned 404 with the identical mapping,
 * identical registry contents and the module ACTIVE. Dispatch itself resolves both correctly in
 * isolation, so the gap was always the mapping — and it had no test.
 */
@DisplayName("ModuleHttpController mapping")
class ModuleHttpControllerMvcTest {

    private RuntimeModuleManager manager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        manager = mock(RuntimeModuleManager.class);
        when(manager.dispatchRoute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(ActionResult.success(Map.of("ok", true))));

        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeModuleManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(manager);

        mvc = MockMvcBuilders.standaloneSetup(new ModuleHttpController(provider)).build();
    }

    @Test
    @DisplayName("GET reaches dispatch")
    void getIsMapped() throws Exception {
        mvc.perform(get("/api/modules/kelta-billing/x/plans")
                        .header("X-Tenant-ID", "t1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST reaches dispatch")
    void postIsMapped() throws Exception {
        mvc.perform(post("/api/modules/kelta-billing/x/checkout-sessions")
                        .header("X-Tenant-ID", "t1")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the path handed to the module excludes the platform prefix")
    void dispatchReceivesModuleRelativePath() throws Exception {
        mvc.perform(get("/api/modules/kelta-billing/x/plans").header("X-Tenant-ID", "t1"));

        org.mockito.Mockito.verify(manager).dispatchRoute(
                eq("t1"), eq("kelta-billing"), any(), eq("GET"), eq("/plans"), any(), any());
    }
}
