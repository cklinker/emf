package io.kelta.worker.controller;

import io.kelta.runtime.workflow.ActionResult;
import io.kelta.worker.module.RuntimeModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inbound webhooks for runtime-installed modules.
 *
 * <p>Spring MVC resolves routes when the application context starts, so a module loaded later
 * cannot contribute a controller of its own. This one generic, platform-owned route is what lets
 * a module accept a webhook from an external system that has no way to know about flows — a
 * payment processor, a delivery service, an upstream poller — by dispatching the raw request to
 * the {@code ActionHandler} the module's manifest names in {@code webhookHandlerKey}.
 *
 * <p><b>This is an unauthenticated path and the platform verifies nothing.</b> The module owns its
 * own trust anchor: it resolves its credential and checks the signature over the raw body before
 * treating the payload as real. The raw body is handed through untouched, because re-serializing
 * would change the bytes an HMAC covers.
 *
 * <p>The {@code tenantId} in the path is <b>untrusted input</b>. It selects which tenant's module
 * — and therefore which secret — to dispatch to, nothing more. An attacker may name any tenant;
 * without that tenant's secret the module's own signature check rejects the request.
 *
 * <p>Answers 404 for every "nothing to dispatch to" case — unknown tenant, module not installed or
 * not active, no webhook handler declared — so an unauthenticated caller cannot tell them apart
 * and enumerate which modules a tenant runs.
 */
@RestController
@RequestMapping("/api/modules/webhooks")
@ConditionalOnBean(RuntimeModuleManager.class)
public class ModuleWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ModuleWebhookController.class);

    /**
     * Headers forwarded to the handler. An allowlist rather than the whole header map: everything
     * a signature scheme needs travels in a purpose-specific header, while the rest carries
     * gateway and infrastructure detail a module has no business reading.
     */
    private static final Set<String> FORWARDED_HEADER_PREFIXES = Set.of(
            "x-", "stripe-", "signature", "digest", "content-type", "user-agent");

    private final RuntimeModuleManager runtimeModuleManager;

    public ModuleWebhookController(RuntimeModuleManager runtimeModuleManager) {
        this.runtimeModuleManager = runtimeModuleManager;
    }

    /** UNAUTHENTICATED path — the dispatched module handler owns verification. */
    @PostMapping("/{tenantId}/{moduleId}")
    public ResponseEntity<Void> receive(@PathVariable String tenantId,
                                        @PathVariable String moduleId,
                                        @RequestHeader Map<String, String> headers,
                                        @RequestBody(required = false) String rawBody) {
        Optional<ActionResult> result;
        try {
            result = runtimeModuleManager.dispatchWebhook(
                    tenantId, moduleId, rawBody, forwardableHeaders(headers));
        } catch (RuntimeException e) {
            // Module code threw rather than returning a failure result. 500 asks the sender to
            // retry, which is the right answer for an unexpected fault.
            log.error("Module '{}' of tenant {} threw handling a webhook: {}",
                    moduleId, tenantId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!result.get().successful()) {
            // The handler rejected it — most often a failed signature check. 401 rather than 500:
            // this is not a fault to retry. The handler's message is logged, never returned; it
            // can name internals of whatever system the module talks to.
            log.warn("Module '{}' of tenant {} rejected a webhook: {}",
                    moduleId, tenantId, result.get().errorMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Header names are case-insensitive on the wire; lower-case them so a module can look one up
     * by a known key instead of guessing the sender's capitalization.
     */
    private Map<String, String> forwardableHeaders(Map<String, String> headers) {
        if (headers == null) {
            return Map.of();
        }
        Map<String, String> forwarded = new java.util.LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (FORWARDED_HEADER_PREFIXES.stream().anyMatch(lower::startsWith)) {
                forwarded.put(lower, value);
            }
        });
        return Map.copyOf(forwarded);
    }
}
