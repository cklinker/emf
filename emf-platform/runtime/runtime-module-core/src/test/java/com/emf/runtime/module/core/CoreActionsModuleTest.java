package com.emf.runtime.module.core;

import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.runtime.workflow.module.ModuleContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CoreActionsModule")
class CoreActionsModuleTest {

    private final CoreActionsModule module = new CoreActionsModule();

    @Test
    @DisplayName("Should have correct module metadata")
    void shouldHaveCorrectMetadata() {
        assertEquals("emf-core-actions", module.getId());
        assertEquals("Core Actions Module", module.getName());
        assertEquals("1.0.0", module.getVersion());
    }

    @Test
    @DisplayName("Should have no handlers before onStartup")
    void shouldHaveNoHandlersBeforeStartup() {
        assertTrue(module.getActionHandlers().isEmpty());
    }

    @Test
    @DisplayName("Should provide 8 action handlers after onStartup")
    void shouldProvide8HandlersAfterStartup() {
        ModuleContext context = new ModuleContext(null, null, null, new ObjectMapper(),
            new ActionHandlerRegistry(), null);
        module.onStartup(context);

        List<ActionHandler> handlers = module.getActionHandlers();
        assertEquals(8, handlers.size());
    }

    @Test
    @DisplayName("Should provide all expected action type keys")
    void shouldProvideExpectedKeys() {
        ModuleContext context = new ModuleContext(null, null, null, new ObjectMapper(),
            new ActionHandlerRegistry(), null);
        module.onStartup(context);

        Set<String> keys = module.getActionHandlers().stream()
                .map(ActionHandler::getActionTypeKey)
                .collect(Collectors.toSet());

        assertTrue(keys.contains("FIELD_UPDATE"));
        assertTrue(keys.contains("CREATE_RECORD"));
        assertTrue(keys.contains("UPDATE_RECORD"));
        assertTrue(keys.contains("DELETE_RECORD"));
        assertTrue(keys.contains("CREATE_TASK"));
        assertTrue(keys.contains("LOG_MESSAGE"));
        assertTrue(keys.contains("DECISION"));
        assertTrue(keys.contains("TRIGGER_FLOW"));
    }

    @Test
    @DisplayName("Should have no before-save hooks")
    void shouldHaveNoBeforeSaveHooks() {
        assertTrue(module.getBeforeSaveHooks().isEmpty());
    }

    @Test
    @DisplayName("Should create TriggerFlowActionHandler successfully")
    void shouldCreateTriggerFlowHandler() {
        ModuleContext context = new ModuleContext(null, null, null, new ObjectMapper(),
            new ActionHandlerRegistry(), null);
        module.onStartup(context);

        ActionHandler triggerFlow = module.getActionHandlers().stream()
            .filter(h -> "TRIGGER_FLOW".equals(h.getActionTypeKey()))
            .findFirst()
            .orElseThrow();

        assertNotNull(triggerFlow);
    }
}
