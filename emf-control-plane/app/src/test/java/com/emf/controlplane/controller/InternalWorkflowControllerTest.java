package com.emf.controlplane.controller;

import com.emf.controlplane.dto.BeforeSaveRequest;
import com.emf.controlplane.dto.BeforeSaveResponse;
import com.emf.controlplane.service.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InternalWorkflowControllerTest {

    private WorkflowEngine workflowEngine;
    private InternalWorkflowController controller;

    @BeforeEach
    void setUp() {
        workflowEngine = mock(WorkflowEngine.class);
        controller = new InternalWorkflowController(workflowEngine);
    }

    @Test
    @DisplayName("Should delegate to WorkflowEngine and return response")
    void shouldDelegateToEngine() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("orders");
        request.setRecordId(null);
        request.setData(Map.of("name", "Test Order"));
        request.setChangeType("CREATE");
        request.setUserId("user-1");

        Map<String, Object> engineResult = Map.of(
            "fieldUpdates", Map.of("status", "Pending"),
            "rulesEvaluated", 2,
            "actionsExecuted", 1
        );

        when(workflowEngine.evaluateBeforeSave(
            eq("tenant-1"), eq("col-1"), eq("orders"), isNull(),
            eq(Map.of("name", "Test Order")), isNull(), eq(List.of()),
            eq("user-1"), eq("CREATE")
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(200, response.getStatusCode().value());
        BeforeSaveResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Pending", body.getFieldUpdates().get("status"));
        assertEquals(2, body.getRulesEvaluated());
        assertEquals(1, body.getActionsExecuted());
    }

    @Test
    @DisplayName("Should handle null changedFields in request")
    void shouldHandleNullChangedFields() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("orders");
        request.setRecordId("rec-1");
        request.setData(Map.of("status", "Approved"));
        request.setPreviousData(Map.of("status", "Pending"));
        request.setChangedFields(null); // null changedFields
        request.setChangeType("UPDATE");
        request.setUserId("user-1");

        Map<String, Object> engineResult = Map.of(
            "fieldUpdates", Map.of(),
            "rulesEvaluated", 0,
            "actionsExecuted", 0
        );

        when(workflowEngine.evaluateBeforeSave(
            anyString(), anyString(), anyString(), anyString(),
            any(), any(), eq(List.of()), anyString(), anyString()
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(200, response.getStatusCode().value());
        // Verify changedFields was passed as empty list (not null)
        verify(workflowEngine).evaluateBeforeSave(
            eq("tenant-1"), eq("col-1"), eq("orders"), eq("rec-1"),
            eq(Map.of("status", "Approved")),
            eq(Map.of("status", "Pending")),
            eq(List.of()),
            eq("user-1"), eq("UPDATE")
        );
    }

    @Test
    @DisplayName("Should return empty field updates when no rules match")
    void shouldReturnEmptyUpdates() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("orders");
        request.setData(Map.of("name", "Test"));
        request.setChangedFields(List.of());
        request.setChangeType("CREATE");
        request.setUserId("user-1");

        Map<String, Object> engineResult = Map.of(
            "fieldUpdates", Map.of(),
            "rulesEvaluated", 0,
            "actionsExecuted", 0
        );

        when(workflowEngine.evaluateBeforeSave(
            anyString(), anyString(), anyString(), any(),
            any(), any(), any(), anyString(), anyString()
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(200, response.getStatusCode().value());
        BeforeSaveResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.getFieldUpdates().isEmpty());
        assertEquals(0, body.getRulesEvaluated());
        assertEquals(0, body.getActionsExecuted());
    }

    @Test
    @DisplayName("Should pass all request fields to engine")
    void shouldPassAllFields() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("orders");
        request.setRecordId("rec-1");
        request.setData(Map.of("status", "Approved", "total", 200.0));
        request.setPreviousData(Map.of("status", "Pending", "total", 150.0));
        request.setChangedFields(List.of("status", "total"));
        request.setUserId("admin-user");
        request.setChangeType("UPDATE");

        Map<String, Object> engineResult = Map.of(
            "fieldUpdates", Map.of("audit_log", "changed"),
            "rulesEvaluated", 1,
            "actionsExecuted", 1
        );

        when(workflowEngine.evaluateBeforeSave(
            eq("tenant-1"), eq("col-1"), eq("orders"), eq("rec-1"),
            eq(Map.of("status", "Approved", "total", 200.0)),
            eq(Map.of("status", "Pending", "total", 150.0)),
            eq(List.of("status", "total")),
            eq("admin-user"), eq("UPDATE")
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(200, response.getStatusCode().value());
        BeforeSaveResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("changed", body.getFieldUpdates().get("audit_log"));
    }

    @Test
    @DisplayName("Should return 422 when lifecycle handler returns validation errors")
    void shouldReturn422OnLifecycleErrors() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("users");
        request.setData(Map.of());
        request.setChangedFields(List.of());
        request.setChangeType("CREATE");
        request.setUserId("user-1");

        // Engine returns errors from lifecycle handler
        Map<String, Object> engineResult = new java.util.HashMap<>();
        engineResult.put("fieldUpdates", Map.of());
        engineResult.put("rulesEvaluated", 0);
        engineResult.put("actionsExecuted", 0);
        engineResult.put("errors", List.of(
                Map.of("field", "email", "message", "Email is required")
        ));

        when(workflowEngine.evaluateBeforeSave(
            anyString(), anyString(), anyString(), any(),
            any(), any(), any(), anyString(), anyString()
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(422, response.getStatusCode().value());
        BeforeSaveResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.hasErrors());
        assertEquals(1, body.getErrors().size());
        assertEquals("email", body.getErrors().get(0).get("field"));
        assertEquals("Email is required", body.getErrors().get(0).get("message"));
    }

    @Test
    @DisplayName("Should return 200 when no errors present")
    void shouldReturn200WhenNoErrors() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("users");
        request.setData(Map.of("email", "user@example.com"));
        request.setChangedFields(List.of());
        request.setChangeType("CREATE");
        request.setUserId("user-1");

        // Engine returns success with no errors
        Map<String, Object> engineResult = new java.util.HashMap<>();
        engineResult.put("fieldUpdates", Map.of("status", "ACTIVE"));
        engineResult.put("rulesEvaluated", 0);
        engineResult.put("actionsExecuted", 0);
        // No "errors" key in the result

        when(workflowEngine.evaluateBeforeSave(
            anyString(), anyString(), anyString(), any(),
            any(), any(), any(), anyString(), anyString()
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(200, response.getStatusCode().value());
        BeforeSaveResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.hasErrors());
        assertEquals("ACTIVE", body.getFieldUpdates().get("status"));
    }

    @Test
    @DisplayName("Should return 422 with multiple validation errors")
    void shouldReturn422WithMultipleErrors() {
        BeforeSaveRequest request = new BeforeSaveRequest();
        request.setTenantId("tenant-1");
        request.setCollectionId("col-1");
        request.setCollectionName("users");
        request.setData(Map.of());
        request.setChangedFields(List.of());
        request.setChangeType("CREATE");
        request.setUserId("user-1");

        Map<String, Object> engineResult = new java.util.HashMap<>();
        engineResult.put("fieldUpdates", Map.of());
        engineResult.put("rulesEvaluated", 0);
        engineResult.put("actionsExecuted", 0);
        engineResult.put("errors", List.of(
                Map.of("field", "email", "message", "Email is required"),
                Map.of("field", "name", "message", "Name is required")
        ));

        when(workflowEngine.evaluateBeforeSave(
            anyString(), anyString(), anyString(), any(),
            any(), any(), any(), anyString(), anyString()
        )).thenReturn(engineResult);

        ResponseEntity<BeforeSaveResponse> response = controller.evaluateBeforeSave(request);

        assertEquals(422, response.getStatusCode().value());
        BeforeSaveResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.getErrors().size());
    }
}
