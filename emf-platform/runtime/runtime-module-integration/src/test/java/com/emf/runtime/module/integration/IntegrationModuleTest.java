package com.emf.runtime.module.integration;

import com.emf.runtime.module.integration.spi.EmailService;
import com.emf.runtime.module.integration.spi.PendingActionStore;
import com.emf.runtime.module.integration.spi.ScriptExecutor;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.module.ModuleContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IntegrationModule")
class IntegrationModuleTest {

    private final IntegrationModule module = new IntegrationModule();

    @Test
    @DisplayName("Should have correct module metadata")
    void shouldHaveCorrectMetadata() {
        assertEquals("emf-integration", module.getId());
        assertEquals("Integration Module", module.getName());
        assertEquals("1.0.0", module.getVersion());
    }

    @Test
    @DisplayName("Should have no handlers before startup")
    void shouldHaveNoHandlersBeforeStartup() {
        assertTrue(module.getActionHandlers().isEmpty());
    }

    @Test
    @DisplayName("Should register 7 handlers after startup")
    void shouldRegister7HandlersAfterStartup() {
        ModuleContext context = new ModuleContext(null, null, null, new ObjectMapper());
        module.onStartup(context);
        assertEquals(7, module.getActionHandlers().size());
    }

    @Test
    @DisplayName("Should register all expected action type keys")
    void shouldRegisterAllExpectedKeys() {
        ModuleContext context = new ModuleContext(null, null, null, new ObjectMapper());
        module.onStartup(context);

        Set<String> keys = module.getActionHandlers().stream()
            .map(ActionHandler::getActionTypeKey)
            .collect(Collectors.toSet());

        assertTrue(keys.contains("HTTP_CALLOUT"));
        assertTrue(keys.contains("OUTBOUND_MESSAGE"));
        assertTrue(keys.contains("SEND_NOTIFICATION"));
        assertTrue(keys.contains("PUBLISH_EVENT"));
        assertTrue(keys.contains("DELAY"));
        assertTrue(keys.contains("EMAIL_ALERT"));
        assertTrue(keys.contains("INVOKE_SCRIPT"));
    }

    @Test
    @DisplayName("Should use custom SPI implementations from extensions")
    void shouldUseCustomSpiFromExtensions() {
        PendingActionStore customStore = (t, e, w, a, r, s, snap) -> "custom-id";
        EmailService customEmail = new EmailService() {
            @Override public java.util.Optional<EmailTemplate> getTemplate(String id) {
                return java.util.Optional.empty();
            }
            @Override public String queueEmail(String t, String to, String s, String b, String src, String sid) {
                return "custom-email-id";
            }
        };
        ScriptExecutor customScript = new ScriptExecutor() {
            @Override public java.util.Optional<ScriptInfo> getScript(String id) {
                return java.util.Optional.empty();
            }
            @Override public String queueExecution(String t, String s, String tt, String r) {
                return "custom-exec-id";
            }
        };

        Map<Class<?>, Object> extensions = Map.of(
            PendingActionStore.class, customStore,
            EmailService.class, customEmail,
            ScriptExecutor.class, customScript
        );

        ModuleContext context = new ModuleContext(null, null, null, new ObjectMapper(), null, extensions);
        module.onStartup(context);

        assertEquals(7, module.getActionHandlers().size());
    }
}
