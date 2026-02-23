package com.emf.controlplane.action;

import com.emf.controlplane.action.handlers.*;
import com.emf.controlplane.dto.ExecuteWorkflowRequest;
import com.emf.controlplane.entity.BulkJob;
import com.emf.controlplane.entity.ConnectedApp;
import com.emf.controlplane.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for individual action handler implementations.
 */
class ActionHandlerTest {

    @Nested
    @DisplayName("ActivateValidationRuleHandler")
    class ActivateValidationRuleHandlerTest {

        @Test
        @DisplayName("Should call activateRule on the service")
        void execute_callsActivateRule() {
            ValidationRuleService service = mock(ValidationRuleService.class);
            ActivateValidationRuleHandler handler = new ActivateValidationRuleHandler(service);

            assertEquals("validation-rules", handler.getCollectionName());
            assertEquals("activate", handler.getActionName());
            assertTrue(handler.isInstanceAction());

            Object result = handler.execute("rule-1", null, "tenant-1", "user-1");

            verify(service).activateRule("rule-1");
            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("activated", resultMap.get("status"));
            assertEquals("rule-1", resultMap.get("id"));
        }
    }

    @Nested
    @DisplayName("DeactivateValidationRuleHandler")
    class DeactivateValidationRuleHandlerTest {

        @Test
        @DisplayName("Should call deactivateRule on the service")
        void execute_callsDeactivateRule() {
            ValidationRuleService service = mock(ValidationRuleService.class);
            DeactivateValidationRuleHandler handler = new DeactivateValidationRuleHandler(service);

            assertEquals("validation-rules", handler.getCollectionName());
            assertEquals("deactivate", handler.getActionName());
            assertTrue(handler.isInstanceAction());

            Object result = handler.execute("rule-1", null, "tenant-1", "user-1");

            verify(service).deactivateRule("rule-1");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("ExecuteWorkflowRuleHandler")
    class ExecuteWorkflowRuleHandlerTest {

        @Test
        @DisplayName("Should call executeManual with request body")
        void execute_callsExecuteManual() {
            WorkflowRuleService service = mock(WorkflowRuleService.class);
            when(service.executeManual(eq("rule-1"), any(ExecuteWorkflowRequest.class), eq("user-1")))
                    .thenReturn(List.of("result-1", "result-2"));

            ExecuteWorkflowRuleHandler handler = new ExecuteWorkflowRuleHandler(service);

            assertEquals("workflow-rules", handler.getCollectionName());
            assertEquals("execute", handler.getActionName());
            assertTrue(handler.isInstanceAction());

            Map<String, Object> body = Map.of("recordIds", List.of("record-1", "record-2"));
            Object result = handler.execute("rule-1", body, "tenant-1", "user-1");

            verify(service).executeManual(eq("rule-1"), any(ExecuteWorkflowRequest.class), eq("user-1"));
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle null body gracefully")
        void execute_handlesNullBody() {
            WorkflowRuleService service = mock(WorkflowRuleService.class);
            when(service.executeManual(eq("rule-1"), any(ExecuteWorkflowRequest.class), eq("user-1")))
                    .thenReturn(List.of());

            ExecuteWorkflowRuleHandler handler = new ExecuteWorkflowRuleHandler(service);
            Object result = handler.execute("rule-1", null, "tenant-1", "user-1");

            verify(service).executeManual(eq("rule-1"), any(ExecuteWorkflowRequest.class), eq("user-1"));
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("RotateConnectedAppSecretHandler")
    class RotateConnectedAppSecretHandlerTest {

        @Test
        @DisplayName("Should call rotateSecret and return client info")
        void execute_callsRotateSecret() {
            ConnectedAppService service = mock(ConnectedAppService.class);
            ConnectedApp app = new ConnectedApp();
            app.setClientId("client-id-123");
            ConnectedAppService.ConnectedAppCreateResult createResult =
                    new ConnectedAppService.ConnectedAppCreateResult(app, "new-secret-456");
            when(service.rotateSecret("app-1")).thenReturn(createResult);

            RotateConnectedAppSecretHandler handler = new RotateConnectedAppSecretHandler(service);

            assertEquals("connected-apps", handler.getCollectionName());
            assertEquals("rotate-secret", handler.getActionName());
            assertTrue(handler.isInstanceAction());

            Object result = handler.execute("app-1", null, "tenant-1", "user-1");

            verify(service).rotateSecret("app-1");
            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("app-1", resultMap.get("id"));
            assertEquals("client-id-123", resultMap.get("clientId"));
            assertEquals("new-secret-456", resultMap.get("clientSecret"));
        }
    }

    @Nested
    @DisplayName("AbortBulkJobHandler")
    class AbortBulkJobHandlerTest {

        @Test
        @DisplayName("Should call abortJob on the service")
        void execute_callsAbortJob() {
            BulkJobService service = mock(BulkJobService.class);
            BulkJob job = new BulkJob();
            when(service.abortJob("job-1")).thenReturn(job);

            AbortBulkJobHandler handler = new AbortBulkJobHandler(service);

            assertEquals("bulk-jobs", handler.getCollectionName());
            assertEquals("abort", handler.getActionName());
            assertTrue(handler.isInstanceAction());

            Object result = handler.execute("job-1", null, "tenant-1", "user-1");

            verify(service).abortJob("job-1");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("ToggleWorkflowActionTypeHandler")
    class ToggleWorkflowActionTypeHandlerTest {

        @Test
        @DisplayName("Should call toggleActive on the service")
        void execute_callsToggleActive() {
            WorkflowActionTypeService service = mock(WorkflowActionTypeService.class);
            when(service.toggleActive("type-1")).thenReturn(null); // Returns DTO, mock null for simplicity

            ToggleWorkflowActionTypeHandler handler = new ToggleWorkflowActionTypeHandler(service);

            assertEquals("workflow-action-types", handler.getCollectionName());
            assertEquals("toggle-active", handler.getActionName());
            assertTrue(handler.isInstanceAction());

            handler.execute("type-1", null, "tenant-1", "user-1");

            verify(service).toggleActive("type-1");
        }
    }
}
