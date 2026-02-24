package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.module.integration.spi.ScriptExecutor;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Action handler that invokes a server-side script.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "scriptId": "uuid-of-script",
 *   "inputPayload": {"param1": "value1"}
 * }
 * </pre>
 *
 * <p>Looks up the script by ID, verifies it is active, and queues an execution.
 * Uses {@link ScriptExecutor} SPI for script lookup and execution queuing.
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

            String scriptId = (String) config.get("scriptId");
            if (scriptId == null || scriptId.isBlank()) {
                return ActionResult.failure("Script ID is required");
            }

            Optional<ScriptExecutor.ScriptInfo> scriptOpt = scriptExecutor.getScript(scriptId);
            if (scriptOpt.isEmpty()) {
                return ActionResult.failure("Script not found: " + scriptId);
            }

            ScriptExecutor.ScriptInfo script = scriptOpt.get();
            if (!script.active()) {
                return ActionResult.failure("Script is inactive: " + script.name());
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
        } catch (Exception e) {
            log.error("Failed to execute invoke script action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("scriptId") == null) {
                throw new IllegalArgumentException("Config must contain 'scriptId'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
