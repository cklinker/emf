package io.kelta.worker.controller;

import io.kelta.runtime.workflow.ActionResult;
import io.kelta.worker.module.RuntimeModuleManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The authenticated HTTP surface a module can serve, at
 * {@code /api/modules/{moduleId}/x/**}.
 *
 * <p>A module can never contribute a {@code @RestController} — Spring MVC resolves routes when the
 * context starts. This is the generic, platform-owned route that stands in for one, the same way
 * {@link ModuleWebhookController} does for unauthenticated inbound traffic.
 *
 * <p><b>Why this prefix and not an arbitrary path.</b> {@code /api/modules/**} is already a static
 * gateway route, so nothing here needs a gateway change, a bootstrap change, or a NATS event, and
 * there is no collision surface against tenant collection routes. It is also already authenticated:
 * the gateway enforces {@code API_ACCESS} before a request reaches this controller, so a manifest
 * can never make an authenticated prefix unauthenticated. Unauthenticated traffic keeps exactly one
 * home, {@code /api/modules/webhooks/}, which stays a short and auditable entry in the gateway's
 * allowlist.
 *
 * <p>A module that is not ACTIVE, or serves no matching route, gets a uniform 404 — the same
 * non-enumerable answer the webhook route gives, so a caller cannot probe which modules a tenant has
 * installed.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/modules/{moduleId}/x")
@ConditionalOnBean(RuntimeModuleManager.class)
public class ModuleHttpController {

    private static final Logger log = LoggerFactory.getLogger(ModuleHttpController.class);

    /** Caps a runaway module response rather than streaming it to the caller. */
    private static final int MAX_RESPONSE_ENTRIES = 1000;

    private final RuntimeModuleManager runtimeModuleManager;

    public ModuleHttpController(RuntimeModuleManager runtimeModuleManager) {
        this.runtimeModuleManager = runtimeModuleManager;
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<?> dispatch(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestHeader(value = "X-User-Id", required = false) String userId,
                                      @PathVariable String moduleId,
                                      @RequestBody(required = false) String rawBody,
                                      HttpServletRequest request) {
        String path = modulePath(request, moduleId);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<ActionResult> result;
        try {
            result = runtimeModuleManager.dispatchRoute(tenantId, moduleId, userId,
                    request.getMethod(), path, queryParams(request), rawBody);
        } catch (RuntimeException e) {
            // Module code threw rather than returning a failure. The message is logged, never
            // returned: it can name internals of whatever the module talks to.
            log.error("Module '{}' of tenant {} threw serving {} {}: {}",
                    moduleId, tenantId, request.getMethod(), path, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "The module failed to handle this request"));
        }

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!result.get().successful()) {
            // A handler's own rejection is the caller's fault far more often than the server's,
            // and its message is written for the caller.
            return ResponseEntity.badRequest()
                    .body(Map.of("error", String.valueOf(result.get().errorMessage())));
        }

        Map<String, Object> output = result.get().outputData();
        if (output != null && output.size() > MAX_RESPONSE_ENTRIES) {
            log.error("Module '{}' of tenant {} returned {} entries for {} {} — refusing",
                    moduleId, tenantId, output.size(), request.getMethod(), path);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "The module returned an oversized response"));
        }
        return ResponseEntity.ok(output == null ? Map.of() : output);
    }

    /**
     * The path the module sees: everything after {@code /api/modules/{moduleId}/x}.
     *
     * <p>Returns null when the request does not actually sit under that prefix, so a crafted URI
     * cannot produce a path the module never declared.
     */
    private String modulePath(HttpServletRequest request, String moduleId) {
        String uri = request.getRequestURI();
        String prefix = "/api/modules/" + moduleId + "/x";
        int at = uri.indexOf(prefix);
        if (at < 0) {
            return null;
        }
        String path = uri.substring(at + prefix.length());
        if (path.isEmpty()) {
            path = "/";
        }
        return path.contains("..") ? null : path;
    }

    private Map<String, Object> queryParams(HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                params.put(name, values.length == 1 ? values[0] : java.util.List.of(values));
            }
        });
        return params;
    }
}
