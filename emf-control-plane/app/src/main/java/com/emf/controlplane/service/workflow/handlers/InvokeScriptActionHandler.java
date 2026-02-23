package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.Script;
import com.emf.controlplane.entity.ScriptExecutionLog;
import com.emf.controlplane.repository.ScriptExecutionLogRepository;
import com.emf.controlplane.service.ScriptService;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Action handler that invokes a server-side script.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "scriptId": "uuid-of-script",
 *   "inputPayload": {
 *     "param1": "value1",
 *     "param2": "value2"
 *   }
 * }
 * </pre>
 * <p>
 * Looks up the script by ID, verifies it is active, and creates a
 * {@link ScriptExecutionLog} entry for the invocation.
 */
@Component
public class InvokeScriptActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(InvokeScriptActionHandler.class);

    private final ObjectMapper objectMapper;
    private final ScriptService scriptService;
    private final ScriptExecutionLogRepository scriptExecutionLogRepository;

    public InvokeScriptActionHandler(ObjectMapper objectMapper,
                                     ScriptService scriptService,
                                     ScriptExecutionLogRepository scriptExecutionLogRepository) {
        this.objectMapper = objectMapper;
        this.scriptService = scriptService;
        this.scriptExecutionLogRepository = scriptExecutionLogRepository;
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

            // Load script
            Script script;
            try {
                script = scriptService.getScript(scriptId);
            } catch (Exception e) {
                return ActionResult.failure("Script not found: " + scriptId);
            }

            if (!script.isActive()) {
                return ActionResult.failure("Script is inactive: " + script.getName());
            }

            // Build input data
            Map<String, Object> inputData = new HashMap<>();
            inputData.put("recordId", context.recordId());
            inputData.put("collectionId", context.collectionId());
            inputData.put("collectionName", context.collectionName());
            inputData.put("data", context.data());
            inputData.put("tenantId", context.tenantId());

            @SuppressWarnings("unchecked")
            Map<String, Object> configPayload = (Map<String, Object>) config.get("inputPayload");
            if (configPayload != null) {
                inputData.putAll(configPayload);
            }

            // Create execution log
            ScriptExecutionLog executionLog = new ScriptExecutionLog();
            executionLog.setTenantId(context.tenantId());
            executionLog.setScriptId(scriptId);
            executionLog.setStatus("QUEUED");
            executionLog.setTriggerType("WORKFLOW");
            executionLog.setRecordId(context.recordId());
            executionLog.setExecutedAt(Instant.now());
            executionLog.setLogOutput("Triggered by workflow rule: " + context.workflowRuleId());
            scriptExecutionLogRepository.save(executionLog);

            log.info("Script invocation queued: scriptId={}, scriptName={}, workflowRule={}",
                scriptId, script.getName(), context.workflowRuleId());

            return ActionResult.success(Map.of(
                "scriptId", scriptId,
                "scriptName", script.getName(),
                "executionLogId", executionLog.getId(),
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
