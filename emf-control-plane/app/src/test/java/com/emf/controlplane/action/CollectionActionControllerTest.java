package com.emf.controlplane.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.emf.controlplane.controller.CollectionActionController;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for CollectionActionController.
 *
 * <p>Tests the generic action routing, not individual handler logic.
 */
class CollectionActionControllerTest {

    private CollectionActionRegistry registry;
    private CollectionActionController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Create a real registry with test handlers
        CollectionActionHandler activateHandler = new TestHandler("validation-rules", "activate", true);
        CollectionActionHandler collectionActionHandler = new TestHandler("reports", "generate-all", false);
        registry = new CollectionActionRegistry(List.of(activateHandler, collectionActionHandler));

        controller = new CollectionActionController(registry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Instance Action - POST /{collection}/{id}/actions/{action}")
    class InstanceActionTests {

        @Test
        @DisplayName("Should execute instance action and return result")
        void executeInstanceAction_success() throws Exception {
            mockMvc.perform(post("/control/validation-rules/rule-1/actions/activate")
                            .header("X-Tenant-ID", "tenant-123")
                            .header("X-User-Id", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executed").value(true))
                    .andExpect(jsonPath("$.id").value("rule-1"))
                    .andExpect(jsonPath("$.action").value("activate"));
        }

        @Test
        @DisplayName("Should return 404 when no handler found")
        void executeInstanceAction_returns404_whenNotFound() throws Exception {
            mockMvc.perform(post("/control/unknown-collection/id-1/actions/unknown-action"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when calling collection action as instance action")
        void executeInstanceAction_returns400_whenCollectionAction() throws Exception {
            mockMvc.perform(post("/control/reports/report-1/actions/generate-all"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("Should pass request body to handler")
        void executeInstanceAction_passesBody() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("recordIds", List.of("r1", "r2")));

            mockMvc.perform(post("/control/validation-rules/rule-1/actions/activate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("X-Tenant-ID", "tenant-123")
                            .header("X-User-Id", "user-1"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Collection Action - POST /{collection}/actions/{action}")
    class CollectionActionTests {

        @Test
        @DisplayName("Should execute collection action and return result")
        void executeCollectionAction_success() throws Exception {
            mockMvc.perform(post("/control/reports/actions/generate-all")
                            .header("X-Tenant-ID", "tenant-123")
                            .header("X-User-Id", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.executed").value(true))
                    .andExpect(jsonPath("$.action").value("generate-all"));
        }

        @Test
        @DisplayName("Should return 404 when no handler found")
        void executeCollectionAction_returns404_whenNotFound() throws Exception {
            mockMvc.perform(post("/control/unknown-collection/actions/unknown-action"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when calling instance action as collection action")
        void executeCollectionAction_returns400_whenInstanceAction() throws Exception {
            mockMvc.perform(post("/control/validation-rules/actions/activate"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("CollectionActionRegistry")
    class RegistryTests {

        @Test
        @DisplayName("Should find registered handler")
        void find_returnsHandler() {
            assertTrue(registry.find("validation-rules", "activate").isPresent());
        }

        @Test
        @DisplayName("Should return empty for unregistered handler")
        void find_returnsEmpty() {
            assertTrue(registry.find("unknown", "action").isEmpty());
        }
    }

    /**
     * Simple test handler that returns a result map.
     */
    private static class TestHandler implements CollectionActionHandler {
        private final String collectionName;
        private final String actionName;
        private final boolean instanceAction;

        TestHandler(String collectionName, String actionName, boolean instanceAction) {
            this.collectionName = collectionName;
            this.actionName = actionName;
            this.instanceAction = instanceAction;
        }

        @Override
        public String getCollectionName() {
            return collectionName;
        }

        @Override
        public String getActionName() {
            return actionName;
        }

        @Override
        public boolean isInstanceAction() {
            return instanceAction;
        }

        @Override
        public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("executed", true);
            result.put("action", actionName);
            if (id != null) {
                result.put("id", id);
            }
            return result;
        }
    }
}
