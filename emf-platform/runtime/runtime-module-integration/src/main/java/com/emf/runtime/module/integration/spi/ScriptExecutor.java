package com.emf.runtime.module.integration.spi;

import java.util.Optional;

/**
 * SPI interface for script execution used by the InvokeScriptActionHandler.
 *
 * <p>Implementations provide script lookup and execution queuing functionality.
 * The host application provides the real implementation backed by a database and
 * script runtime. A no-op logging implementation is provided for testing.
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
     * Script information.
     *
     * @param id the script ID
     * @param name the script name
     * @param active whether the script is active
     */
    record ScriptInfo(String id, String name, boolean active) {}
}
