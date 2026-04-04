package io.kelta.runtime.module.integration.spi;

import java.util.Map;
import java.util.Optional;

/**
 * SPI interface for script execution used by the InvokeScriptActionHandler.
 *
 * <p>Implementations provide script lookup, execution queuing, and synchronous
 * execution functionality. The host application provides the real implementation
 * backed by a database and script runtime. A no-op logging implementation is
 * provided for testing.
 *
 * @since 1.0.0
 */
public interface ScriptExecutor {

    /**
     * Retrieves script information by ID.
     *
     * @param scriptId the script ID
     * @return the script info, or empty if not found
     */
    Optional<ScriptInfo> getScript(String scriptId);

    /**
     * Queues a script execution.
     *
     * @param tenantId the tenant ID
     * @param scriptId the script ID
     * @param triggerType the trigger type (e.g., "WORKFLOW")
     * @param recordId the record ID that triggered the execution
     * @return the generated execution log ID
     */
    String queueExecution(String tenantId, String scriptId, String triggerType, String recordId);

    /**
     * Executes a script synchronously with the given bindings.
     *
     * <p>The script source is executed in a sandboxed environment with access to the
     * provided bindings (e.g., record data, input payload). The execution is subject
     * to timeout and resource limits defined by the implementation.
     *
     * @param request the script execution request containing source, bindings, and config
     * @return the execution result including output data, success status, and timing
     */
    ScriptExecutionResult execute(ScriptExecutionRequest request);

    /**
     * Script information.
     *
     * @param id the script ID
     * @param name the script name
     * @param active whether the script is active
     * @param source the script source code (may be null for queue-only scripts)
     */
    record ScriptInfo(String id, String name, boolean active, String source) {

        /**
         * Backward-compatible constructor without source.
         */
        public ScriptInfo(String id, String name, boolean active) {
            this(id, name, active, null);
        }
    }

    /**
     * Request object for synchronous script execution.
     *
     * @param scriptSource the JavaScript source code to execute
     * @param bindings variables to expose to the script (e.g., record, input, context)
     * @param timeoutSeconds maximum execution time in seconds (0 = use default)
     */
    record ScriptExecutionRequest(
        String scriptSource,
        Map<String, Object> bindings,
        int timeoutSeconds
    ) {
        public ScriptExecutionRequest(String scriptSource, Map<String, Object> bindings) {
            this(scriptSource, bindings, 0);
        }
    }

    /**
     * Result of a synchronous script execution.
     *
     * @param success whether the script executed without errors
     * @param output the script's return value or output data
     * @param errorMessage error message if execution failed
     * @param executionTimeMs wall-clock execution time in milliseconds
     */
    record ScriptExecutionResult(
        boolean success,
        Map<String, Object> output,
        String errorMessage,
        long executionTimeMs
    ) {
        public static ScriptExecutionResult success(Map<String, Object> output, long executionTimeMs) {
            return new ScriptExecutionResult(true, output, null, executionTimeMs);
        }

        public static ScriptExecutionResult failure(String errorMessage, long executionTimeMs) {
            return new ScriptExecutionResult(false, Map.of(), errorMessage, executionTimeMs);
        }
    }
}
