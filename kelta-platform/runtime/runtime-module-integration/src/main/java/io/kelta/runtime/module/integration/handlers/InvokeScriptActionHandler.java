package io.kelta.runtime.module.integration.handlers;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionRequest;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler that invokes a server-side script.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Inline execution</b> — When {@code scriptSource} is provided, executes the script
 *       synchronously using GraalVM and returns the result directly.</li>
 *   <li><b>Script ID lookup</b> — When {@code scriptId} is provided, looks up the script,
 *       executes it synchronously if source is available, or queues it for async execution.</li>
 * </ul>
 *
 * <p>Config format:
 * <pre>
 * {
 *   "scriptId": "uuid-of-script",
 *   "scriptSource": "var result = record.status === 'Active' ? 'yes' : 'no'; result;",
 *   "inputPayload": {"param1": "value1"},
 *   "timeoutSeconds": 30
 * }
 * </pre>
 *
 * <p>Scripts receive the following bindings:
 * <ul>
 *   <li>{@code record} — the current record data</li>
 *   <li>{@code previousRecord} — the previous record data (before change)</li>
 *   <li>{@code input} — the input payload from config</li>
 *   <li>{@code context} — metadata (tenantId, collectionName, recordId, userId)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class InvokeScriptActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(InvokeScriptActionHandler.class);

    private final ObjectMapper objectMapper;
    private final ScriptExecutor scriptExecutor;

    public InvokeScriptActionHandler(ObjectMapper objectMapper, ScriptExecutor scriptExecutor) {
        this.objectMapper = objectMapper;
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public String getActionTypeKey() {
        return "INVOKE_SCRIPT";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String scriptSource = (String) config.get("scriptSource");
            String scriptId = (String) config.get("scriptId");

            @SuppressWarnings("unchecked")
            Map<String, Object> inputPayload = (Map<String, Object>) config.get("inputPayload");

            int timeoutSeconds = config.containsKey("timeoutSeconds")
                ? ((Number) config.get("timeoutSeconds")).intValue()
                : 0;

            if (scriptSource != null && !scriptSource.isBlank()) {
                return executeInline(context, scriptSource, inputPayload, timeoutSeconds);
            }

            if (scriptId == null || scriptId.isBlank()) {
                return ActionResult.failure("Either 'scriptSource' or 'scriptId' is required");
            }

            return executeByScriptId(context, scriptId, inputPayload, timeoutSeconds);

        } catch (Exception e) {
            log.error("Failed to execute invoke script action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    private ActionResult executeInline(ActionContext context, String scriptSource,
                                       Map<String, Object> inputPayload, int timeoutSeconds) {
        Map<String, Object> bindings = buildBindings(context, inputPayload);

        ScriptExecutionResult result = scriptExecutor.execute(
            new ScriptExecutionRequest(scriptSource, bindings, timeoutSeconds));

        if (!result.success()) {
            log.warn("Inline script execution failed for workflowRule={}: {}",
                context.workflowRuleId(), result.errorMessage());
            return ActionResult.failure(result.errorMessage());
        }

        Map<String, Object> outputData = new HashMap<>(result.output());
        outputData.put("status", "COMPLETED");
        outputData.put("executionTimeMs", result.executionTimeMs());

        log.info("Inline script executed successfully in {}ms for workflowRule={}",
            result.executionTimeMs(), context.workflowRuleId());

        return ActionResult.success(outputData);
    }

    private ActionResult executeByScriptId(ActionContext context, String scriptId,
                                           Map<String, Object> inputPayload, int timeoutSeconds) {
        Optional<ScriptExecutor.ScriptInfo> scriptOpt = scriptExecutor.getScript(scriptId);
        if (scriptOpt.isEmpty()) {
            return ActionResult.failure("Script not found: " + scriptId);
        }

        ScriptExecutor.ScriptInfo script = scriptOpt.get();
        if (!script.active()) {
            return ActionResult.failure("Script is inactive: " + script.name());
        }

        if (script.source() != null && !script.source().isBlank()) {
            Map<String, Object> bindings = buildBindings(context, inputPayload);

            ScriptExecutionResult result = scriptExecutor.execute(
                new ScriptExecutionRequest(script.source(), bindings, timeoutSeconds));

            if (!result.success()) {
                log.warn("Script execution failed: scriptId={}, error={}",
                    scriptId, result.errorMessage());
                return ActionResult.failure(result.errorMessage());
            }

            Map<String, Object> outputData = new HashMap<>(result.output());
            outputData.put("scriptId", scriptId);
            outputData.put("scriptName", script.name());
            outputData.put("status", "COMPLETED");
            outputData.put("executionTimeMs", result.executionTimeMs());

            log.info("Script executed: scriptId={}, scriptName={}, workflowRule={}, elapsed={}ms",
                scriptId, script.name(), context.workflowRuleId(), result.executionTimeMs());

            return ActionResult.success(outputData);
        }

        String executionLogId = scriptExecutor.queueExecution(
            context.tenantId(), scriptId, "WORKFLOW", context.recordId()
        );

        log.info("Script invocation queued: scriptId={}, scriptName={}, workflowRule={}",
            scriptId, script.name(), context.workflowRuleId());

        return ActionResult.success(Map.of(
            "scriptId", scriptId,
            "scriptName", script.name(),
            "executionLogId", executionLogId,
            "status", "QUEUED"
        ));
    }

    private Map<String, Object> buildBindings(ActionContext context, Map<String, Object> inputPayload) {
        Map<String, Object> bindings = new HashMap<>();

        bindings.put("record", context.data() != null ? context.data() : Map.of());
        bindings.put("previousRecord", context.previousData() != null ? context.previousData() : Map.of());
        bindings.put("input", inputPayload != null ? inputPayload : Map.of());
        bindings.put("context", Map.of(
            "tenantId", nullSafe(context.tenantId()),
            "collectionName", nullSafe(context.collectionName()),
            "recordId", nullSafe(context.recordId()),
            "userId", nullSafe(context.userId())
        ));

        return bindings;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            boolean hasScriptId = config.get("scriptId") != null;
            boolean hasScriptSource = config.get("scriptSource") != null;

            if (!hasScriptId && !hasScriptSource) {
                throw new IllegalArgumentException("Config must contain 'scriptId' or 'scriptSource'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
