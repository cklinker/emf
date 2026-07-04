package io.kelta.worker.controller;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionRequest;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.ScriptRepository;
import io.kelta.worker.repository.ScriptRepository.Script;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Synchronous, HTTP-invoked execution of an admin-authored server script.
 *
 * <p>Backs the end-user Quick Actions "run script" path (`useScriptExecution`). The script
 * source is admin-authored and runs in the sandboxed {@link ScriptExecutor} (GraalVM) with a
 * timeout; a caller supplies only an {@code input} payload and an optional record
 * {@code context}. Only scripts whose {@code scriptType} is HTTP-invocable
 * ({@code API_ENDPOINT} / {@code EVENT_HANDLER}) and that are {@code active} may be run this
 * way — trigger/validation/scheduled scripts run only in their own lifecycle contexts.
 *
 * <p>Reads are tenant-scoped by RLS. The route lives under {@code /api/scripts/**} (already
 * gateway-routed) and gets the blanket {@code API_ACCESS} check. Additionally, a script may
 * declare {@code requiredPermission} — execution is then gated on the caller's profile
 * holding that system permission (403 otherwise); unset means any API user may run it.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scripts")
public class ScriptExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ScriptExecutionController.class);

    /** Script types that may be invoked over HTTP. Trigger/validation/scheduled run elsewhere. */
    private static final Set<String> INVOCABLE_TYPES = Set.of("API_ENDPOINT", "EVENT_HANDLER");

    private final ScriptRepository scriptRepository;
    private final ScriptExecutor scriptExecutor;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public ScriptExecutionController(ScriptRepository scriptRepository,
                                     ScriptExecutor scriptExecutor,
                                     CerbosPermissionResolver permissionResolver,
                                     BootstrapRepository bootstrapRepository) {
        this.scriptRepository = scriptRepository;
        this.scriptExecutor = scriptExecutor;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    @PostMapping("/{id}/execute")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable String id,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {

        Optional<Script> scriptOpt = scriptRepository.findById(id);
        if (scriptOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Script script = scriptOpt.get();

        if (!hasRequiredPermission(script, httpRequest)) {
            return error(HttpStatus.FORBIDDEN,
                    "Executing this script requires the " + script.requiredPermission() + " permission");
        }
        if (!script.active()) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Script is not active");
        }
        if (!INVOCABLE_TYPES.contains(script.scriptType())) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Script type " + script.scriptType() + " cannot be invoked via the execute endpoint");
        }
        if (script.source() == null || script.source().isBlank()) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Script has no source");
        }

        Map<String, Object> requestBody = body != null ? body : Map.of();
        Map<String, Object> input = requestBody.get("input") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, Object> callerContext = requestBody.get("context") instanceof Map<?, ?> c
                ? (Map<String, Object>) c : Map.of();
        String recordId = callerContext.get("recordId") instanceof String s ? s : null;

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("input", input);
        bindings.put("context", Map.of(
                "tenantId", tenantId != null ? tenantId : "",
                "userId", userId != null ? userId : "",
                "scriptId", script.id(),
                "collectionName", callerContext.getOrDefault("collectionName", ""),
                "recordId", recordId != null ? recordId : ""));

        ScriptExecutionResult result;
        try {
            result = scriptExecutor.execute(new ScriptExecutionRequest(script.source(), bindings, 0));
        } catch (RuntimeException ex) {
            log.warn("Script {} execution threw: {}", id, ex.getMessage());
            result = ScriptExecutionResult.failure(ex.getMessage(), 0L);
        }

        safeLog(tenantId, script.id(), result, recordId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("output", result.output());
        response.put("executionTimeMs", result.executionTimeMs());
        if (!result.success()) {
            response.put("message", result.errorMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * A script with no {@code requiredPermission} runs for any API user (back-compat).
     * When set, the caller's profile must hold that system permission — missing
     * identity fails closed.
     */
    private boolean hasRequiredPermission(Script script, HttpServletRequest request) {
        String required = script.requiredPermission();
        if (required == null || required.isBlank()) {
            return true;
        }
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            return false;
        }
        return bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> required.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
    }

    private void safeLog(String tenantId, String scriptId, ScriptExecutionResult result, String recordId) {
        try {
            scriptRepository.insertExecutionLog(tenantId, scriptId,
                    result.success() ? "SUCCESS" : "FAILURE",
                    result.executionTimeMs(), recordId, result.errorMessage());
        } catch (RuntimeException ex) {
            // Never let audit-log persistence failure mask the execution result.
            log.warn("Failed to write script_execution_log for script {}: {}", scriptId, ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
