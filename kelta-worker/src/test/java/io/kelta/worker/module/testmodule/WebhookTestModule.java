package io.kelta.worker.module.testmodule;

import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionResult;
import io.kelta.runtime.workflow.module.KeltaModule;

import java.util.List;
import java.util.Map;

/**
 * A module that accepts webhooks, used by {@code ModuleWebhookDispatchTest}.
 *
 * <p>Its handler echoes back what the platform handed it, so a test can assert the raw body
 * reached it byte-for-byte — the property an HMAC check depends on — and rejects a request whose
 * signature header is absent, standing in for the real verification a module owns.
 */
public class WebhookTestModule implements KeltaModule {

    public static final String HANDLER_KEY = "test:webhook";

    @Override
    public String getId() {
        return "webhook-module";
    }

    @Override
    public String getName() {
        return "Webhook Test Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<ActionHandler> getActionHandlers() {
        return List.of(new ActionHandler() {
            @Override
            public String getActionTypeKey() {
                return HANDLER_KEY;
            }

            @Override
            @SuppressWarnings("unchecked")
            public ActionResult execute(ActionContext context) {
                Map<String, Object> resolved = context.resolvedData();
                Map<String, String> headers =
                        (Map<String, String>) resolved.getOrDefault("headers", Map.of());
                if (!headers.containsKey("x-test-signature")) {
                    return ActionResult.failure("missing signature");
                }
                return ActionResult.success(Map.of(
                        "echoBody", resolved.getOrDefault("rawBody", ""),
                        "echoTenant", context.tenantId() == null ? "" : context.tenantId(),
                        "echoModule", resolved.getOrDefault("moduleId", "")));
            }
        });
    }
}
