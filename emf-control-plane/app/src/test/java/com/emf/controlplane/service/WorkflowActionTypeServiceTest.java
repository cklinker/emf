package com.emf.controlplane.service;

import com.emf.controlplane.dto.WorkflowActionTypeDto;
import com.emf.controlplane.entity.WorkflowActionType;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.WorkflowActionTypeRepository;
import com.emf.controlplane.service.workflow.ActionHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowActionTypeServiceTest {

    private WorkflowActionTypeRepository repository;
    private ActionHandlerRegistry handlerRegistry;
    private WorkflowActionTypeService service;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowActionTypeRepository.class);
        handlerRegistry = mock(ActionHandlerRegistry.class);
        service = new WorkflowActionTypeService(repository, handlerRegistry);
    }

    private WorkflowActionType createActionType(String key, String name, String category) {
        WorkflowActionType type = new WorkflowActionType();
        type.setKey(key);
        type.setName(name);
        type.setCategory(category);
        type.setHandlerClass("com.emf.test.Handler");
        type.setActive(true);
        type.setBuiltIn(true);
        return type;
    }

    @Nested
    @DisplayName("List Action Types")
    class ListTests {

        @Test
        @DisplayName("Should list all action types with handler availability")
        void shouldListAll() {
            WorkflowActionType fieldUpdate = createActionType("FIELD_UPDATE", "Field Update", "DATA");
            WorkflowActionType emailAlert = createActionType("EMAIL_ALERT", "Email Alert", "COMMUNICATION");

            when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(emailAlert, fieldUpdate));
            when(handlerRegistry.hasHandler("FIELD_UPDATE")).thenReturn(true);
            when(handlerRegistry.hasHandler("EMAIL_ALERT")).thenReturn(false);

            List<WorkflowActionTypeDto> result = service.listActionTypes();

            assertEquals(2, result.size());
            assertEquals("EMAIL_ALERT", result.get(0).getKey());
            assertFalse(result.get(0).isHandlerAvailable());
            assertEquals("FIELD_UPDATE", result.get(1).getKey());
            assertTrue(result.get(1).isHandlerAvailable());
        }

        @Test
        @DisplayName("Should list only active action types")
        void shouldListActive() {
            WorkflowActionType active = createActionType("FIELD_UPDATE", "Field Update", "DATA");
            when(repository.findByActiveTrue()).thenReturn(List.of(active));
            when(handlerRegistry.hasHandler("FIELD_UPDATE")).thenReturn(true);

            List<WorkflowActionTypeDto> result = service.listActiveActionTypes();

            assertEquals(1, result.size());
            assertTrue(result.get(0).isActive());
        }

        @Test
        @DisplayName("Should list by category")
        void shouldListByCategory() {
            WorkflowActionType type = createActionType("FIELD_UPDATE", "Field Update", "DATA");
            when(repository.findByCategoryAndActiveTrue("DATA")).thenReturn(List.of(type));
            when(handlerRegistry.hasHandler("FIELD_UPDATE")).thenReturn(true);

            List<WorkflowActionTypeDto> result = service.listByCategory("DATA");

            assertEquals(1, result.size());
            assertEquals("DATA", result.get(0).getCategory());
        }
    }

    @Nested
    @DisplayName("Get Action Type")
    class GetTests {

        @Test
        @DisplayName("Should get action type by key")
        void shouldGetByKey() {
            WorkflowActionType type = createActionType("FIELD_UPDATE", "Field Update", "DATA");
            when(repository.findByKey("FIELD_UPDATE")).thenReturn(Optional.of(type));
            when(handlerRegistry.hasHandler("FIELD_UPDATE")).thenReturn(true);

            WorkflowActionTypeDto result = service.getByKey("FIELD_UPDATE");

            assertEquals("FIELD_UPDATE", result.getKey());
            assertEquals("Field Update", result.getName());
            assertTrue(result.isHandlerAvailable());
        }

        @Test
        @DisplayName("Should throw when key not found")
        void shouldThrowWhenKeyNotFound() {
            when(repository.findByKey("NONEXISTENT")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.getByKey("NONEXISTENT"));
        }

        @Test
        @DisplayName("Should get action type by ID")
        void shouldGetById() {
            WorkflowActionType type = createActionType("EMAIL_ALERT", "Email Alert", "COMMUNICATION");
            type.setId("test-id-123");
            when(repository.findById("test-id-123")).thenReturn(Optional.of(type));
            when(handlerRegistry.hasHandler("EMAIL_ALERT")).thenReturn(false);

            WorkflowActionTypeDto result = service.getById("test-id-123");

            assertEquals("EMAIL_ALERT", result.getKey());
            assertFalse(result.isHandlerAvailable());
        }
    }

    @Nested
    @DisplayName("Toggle Active")
    class ToggleTests {

        @Test
        @DisplayName("Should toggle active status")
        void shouldToggleActive() {
            WorkflowActionType type = createActionType("FIELD_UPDATE", "Field Update", "DATA");
            type.setActive(true);
            when(repository.findById("type-1")).thenReturn(Optional.of(type));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(handlerRegistry.hasHandler("FIELD_UPDATE")).thenReturn(true);

            WorkflowActionTypeDto result = service.toggleActive("type-1");

            assertFalse(result.isActive());
            verify(repository).save(type);
        }

        @Test
        @DisplayName("Should throw when ID not found for toggle")
        void shouldThrowWhenNotFound() {
            when(repository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.toggleActive("nonexistent"));
        }
    }
}
