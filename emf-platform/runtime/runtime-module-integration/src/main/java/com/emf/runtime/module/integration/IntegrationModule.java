package com.emf.runtime.module.integration;

import com.emf.runtime.module.integration.handlers.*;
import com.emf.runtime.module.integration.spi.EmailService;
import com.emf.runtime.module.integration.spi.PendingActionStore;
import com.emf.runtime.module.integration.spi.ScriptExecutor;
import com.emf.runtime.module.integration.spi.noop.LoggingEmailService;
import com.emf.runtime.module.integration.spi.noop.LoggingPendingActionStore;
import com.emf.runtime.module.integration.spi.noop.LoggingScriptExecutor;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.module.EmfModule;
import com.emf.runtime.workflow.module.ModuleContext;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * EMF module that provides integration action handlers for external communication.
 *
 * <p>This module registers the following action handlers:
 * <ul>
 *   <li><b>HTTP_CALLOUT</b> — Makes HTTP requests with response capture</li>
 *   <li><b>OUTBOUND_MESSAGE</b> — Sends outbound webhook messages</li>
 *   <li><b>SEND_NOTIFICATION</b> — Sends in-app notifications</li>
 *   <li><b>PUBLISH_EVENT</b> — Publishes custom events (e.g., Kafka)</li>
 *   <li><b>DELAY</b> — Delays subsequent workflow actions</li>
 *   <li><b>EMAIL_ALERT</b> — Sends email notifications</li>
 *   <li><b>INVOKE_SCRIPT</b> — Invokes server-side scripts</li>
 * </ul>
 *
 * <p>Handlers with deep infrastructure dependencies use SPI interfaces:
 * <ul>
 *   <li>{@link PendingActionStore} — for DelayActionHandler</li>
 *   <li>{@link EmailService} — for EmailAlertActionHandler</li>
 *   <li>{@link ScriptExecutor} — for InvokeScriptActionHandler</li>
 * </ul>
 *
 * <p>If no SPI implementations are provided via ModuleContext extensions,
 * logging no-op implementations are used as defaults.
 *
 * @since 1.0.0
 */
public class IntegrationModule implements EmfModule {

    private final List<ActionHandler> handlers = new ArrayList<>();

    @Override
    public String getId() {
        return "emf-integration";
    }

    @Override
    public String getName() {
        return "Integration Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void onStartup(ModuleContext context) {
        var objectMapper = context.objectMapper();

        // HTTP handlers — use RestTemplate from extensions or create default
        RestTemplate restTemplate = context.getExtension(RestTemplate.class);

        handlers.add(new HttpCalloutActionHandler(objectMapper, restTemplate));
        handlers.add(new OutboundMessageActionHandler(objectMapper, restTemplate));

        // Simple handlers (ObjectMapper only)
        handlers.add(new SendNotificationActionHandler(objectMapper));

        // Event publishing — use EventPublisher from extensions (optional)
        PublishEventActionHandler.EventPublisher eventPublisher =
            context.getExtension(PublishEventActionHandler.EventPublisher.class);
        handlers.add(new PublishEventActionHandler(objectMapper, eventPublisher));

        // SPI-based handlers — use implementations from extensions or defaults
        PendingActionStore pendingActionStore = context.getExtension(PendingActionStore.class);
        if (pendingActionStore == null) {
            pendingActionStore = new LoggingPendingActionStore();
        }
        handlers.add(new DelayActionHandler(objectMapper, pendingActionStore));

        EmailService emailService = context.getExtension(EmailService.class);
        if (emailService == null) {
            emailService = new LoggingEmailService();
        }
        handlers.add(new EmailAlertActionHandler(objectMapper, emailService));

        ScriptExecutor scriptExecutor = context.getExtension(ScriptExecutor.class);
        if (scriptExecutor == null) {
            scriptExecutor = new LoggingScriptExecutor();
        }
        handlers.add(new InvokeScriptActionHandler(objectMapper, scriptExecutor));
    }

    @Override
    public List<ActionHandler> getActionHandlers() {
        return handlers;
    }
}
