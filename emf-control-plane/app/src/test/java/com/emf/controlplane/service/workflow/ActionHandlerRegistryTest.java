package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.WorkflowActionType;
import com.emf.controlplane.repository.WorkflowActionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionHandlerRegistryTest {

    private WorkflowActionTypeRepository typeRepository;

    @BeforeEach
    void setUp() {
        typeRepository = mock(WorkflowActionTypeRepository.class);
        when(typeRepository.findByActiveTrue()).thenReturn(List.of());
    }

    private ActionHandler createHandler(String key) {
        return new ActionHandler() {
            @Override
            public String getActionTypeKey() { return key; }

            @Override
            public ActionResult execute(ActionContext context) {
                return ActionResult.success();
            }
        };
    }

    @Nested
    @DisplayName("Handler Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Should register discovered handlers")
        void shouldRegisterHandlers() {
            List<ActionHandler> handlers = List.of(
                createHandler("FIELD_UPDATE"),
                createHandler("EMAIL_ALERT")
            );

            ActionHandlerRegistry registry = new ActionHandlerRegistry(handlers, typeRepository);
            registry.initialize();

            assertEquals(2, registry.size());
            assertTrue(registry.hasHandler("FIELD_UPDATE"));
            assertTrue(registry.hasHandler("EMAIL_ALERT"));
        }

        @Test
        @DisplayName("Should handle empty handler list")
        void shouldHandleEmptyHandlers() {
            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(), typeRepository);
            registry.initialize();

            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should handle null handler list")
        void shouldHandleNullHandlers() {
            ActionHandlerRegistry registry = new ActionHandlerRegistry(null, typeRepository);
            registry.initialize();

            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should handle duplicate handler keys by replacing")
        void shouldReplaceDuplicateHandlers() {
            ActionHandler handler1 = createHandler("FIELD_UPDATE");
            ActionHandler handler2 = createHandler("FIELD_UPDATE");

            ActionHandlerRegistry registry = new ActionHandlerRegistry(
                List.of(handler1, handler2), typeRepository);
            registry.initialize();

            assertEquals(1, registry.size());
            assertTrue(registry.getHandler("FIELD_UPDATE").isPresent());
        }
    }

    @Nested
    @DisplayName("Handler Lookup")
    class LookupTests {

        @Test
        @DisplayName("Should return handler for registered key")
        void shouldReturnHandler() {
            ActionHandler handler = createHandler("FIELD_UPDATE");
            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(handler), typeRepository);
            registry.initialize();

            var result = registry.getHandler("FIELD_UPDATE");

            assertTrue(result.isPresent());
            assertEquals("FIELD_UPDATE", result.get().getActionTypeKey());
        }

        @Test
        @DisplayName("Should return empty for unregistered key")
        void shouldReturnEmptyForMissing() {
            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(), typeRepository);
            registry.initialize();

            var result = registry.getHandler("NONEXISTENT");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return registered keys")
        void shouldReturnRegisteredKeys() {
            List<ActionHandler> handlers = List.of(
                createHandler("FIELD_UPDATE"),
                createHandler("EMAIL_ALERT"),
                createHandler("CREATE_RECORD")
            );

            ActionHandlerRegistry registry = new ActionHandlerRegistry(handlers, typeRepository);
            registry.initialize();

            var keys = registry.getRegisteredKeys();
            assertEquals(3, keys.size());
            assertTrue(keys.contains("FIELD_UPDATE"));
            assertTrue(keys.contains("EMAIL_ALERT"));
            assertTrue(keys.contains("CREATE_RECORD"));
        }
    }

    @Nested
    @DisplayName("Cross-Reference with Database")
    class CrossReferenceTests {

        @Test
        @DisplayName("Should warn when DB type has no handler")
        void shouldWarnMissingHandler() {
            WorkflowActionType dbType = new WorkflowActionType();
            dbType.setKey("SLACK_MESSAGE");
            dbType.setName("Slack Message");
            dbType.setHandlerClass("com.emf.SlackHandler");

            when(typeRepository.findByActiveTrue()).thenReturn(List.of(dbType));

            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(), typeRepository);
            // Should not throw — just logs a warning
            assertDoesNotThrow(registry::initialize);
        }

        @Test
        @DisplayName("Should warn when handler has no DB type")
        void shouldWarnUnregisteredHandler() {
            when(typeRepository.findByActiveTrue()).thenReturn(List.of());

            ActionHandler handler = createHandler("CUSTOM_ACTION");
            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(handler), typeRepository);
            // Should not throw — just logs a warning
            assertDoesNotThrow(registry::initialize);
            assertTrue(registry.hasHandler("CUSTOM_ACTION"));
        }

        @Test
        @DisplayName("Should handle DB exception gracefully")
        void shouldHandleDbException() {
            when(typeRepository.findByActiveTrue()).thenThrow(new RuntimeException("DB down"));

            ActionHandler handler = createHandler("FIELD_UPDATE");
            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(handler), typeRepository);
            // Should not throw — just logs a warning
            assertDoesNotThrow(registry::initialize);
            assertTrue(registry.hasHandler("FIELD_UPDATE"));
        }
    }

    @Nested
    @DisplayName("Refresh")
    class RefreshTests {

        @Test
        @DisplayName("Should re-initialize on refresh")
        void shouldRefresh() {
            ActionHandler handler = createHandler("FIELD_UPDATE");
            ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(handler), typeRepository);
            registry.initialize();

            assertEquals(1, registry.size());

            // Refresh should re-run initialization
            registry.refresh();
            assertEquals(1, registry.size());
            assertTrue(registry.hasHandler("FIELD_UPDATE"));
        }
    }
}
