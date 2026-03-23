package io.kelta.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svix.Svix;
import com.svix.models.EventTypeIn;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.worker.listener.SvixTenantLifecycleHook;
import io.kelta.worker.listener.SvixWebhookPublisher;
import io.kelta.worker.service.SvixTenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * Spring configuration for the Svix webhook service client.
 *
 * <p>Creates a {@link Svix} client bean and registers webhook event types
 * on application startup.
 *
 * <p>Enabled when {@code kelta.svix.auth-token} is set.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "kelta.svix.auth-token", matchIfMissing = false)
public class SvixConfig {

    private static final Logger log = LoggerFactory.getLogger(SvixConfig.class);

    private static final List<String> EVENT_TYPES = List.of(
            "collection.created",
            "collection.updated"
    );

    @Value("${kelta.svix.server-url:http://localhost:8071}")
    private String serverUrl;

    @Value("${kelta.svix.auth-token}")
    private String authToken;

    private Svix svixClient;

    @Bean
    public Svix svix() {
        log.info("Configuring Svix client with server URL: {}", serverUrl);
        var options = new com.svix.SvixOptions();
        options.setServerUrl(serverUrl);
        this.svixClient = new Svix(authToken, options);
        return this.svixClient;
    }

    @Bean
    public SvixTenantService svixTenantService(Svix svix) {
        return new SvixTenantService(svix);
    }

    @Bean
    public SvixTenantLifecycleHook svixTenantLifecycleHook(
            BeforeSaveHookRegistry hookRegistry,
            SvixTenantService svixTenantService) {
        SvixTenantLifecycleHook hook = new SvixTenantLifecycleHook(svixTenantService);
        hookRegistry.register(hook);
        return hook;
    }

    @Bean
    public SvixWebhookPublisher svixWebhookPublisher(Svix svix, ObjectMapper objectMapper) {
        return new SvixWebhookPublisher(svix, objectMapper);
    }

    /**
     * Registers webhook event types with Svix on startup.
     * Idempotent — Svix allows re-registering existing types.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerEventTypes() {
        if (svixClient == null) {
            log.warn("Svix client not initialized — skipping event type registration");
            return;
        }

        for (String eventType : EVENT_TYPES) {
            try {
                var eventTypeIn = new EventTypeIn();
                eventTypeIn.setName(eventType);
                eventTypeIn.setDescription("Collection " + eventType.split("\\.")[1] + " event");
                svixClient.getEventType().create(eventTypeIn);
                log.info("Registered Svix event type: {}", eventType);
            } catch (Exception e) {
                // Svix returns 409 if event type already exists — this is expected
                log.debug("Svix event type '{}' already exists or registration failed: {}",
                        eventType, e.getMessage());
            }
        }
    }
}
