package com.emf.runtime.module.integration.spi.noop;

import com.emf.runtime.module.integration.spi.ScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * No-op implementation of {@link ScriptExecutor} that logs operations
 * and returns dummy IDs. Used as a default when no real script runtime is configured.
 *
 * @since 1.0.0
 */
public class LoggingScriptExecutor implements ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(LoggingScriptExecutor.class);

    @Override
    public Optional<ScriptInfo> getScript(String scriptId) {
        log.info("[NOOP] ScriptExecutor.getScript: scriptId={} — returning empty", scriptId);
        return Optional.empty();
    }

    @Override
    public String queueExecution(String tenantId, String scriptId, String triggerType, String recordId) {
        String id = UUID.randomUUID().toString();
        log.info("[NOOP] ScriptExecutor.queueExecution: id={}, tenant={}, script={}, trigger={}, record={}",
            id, tenantId, scriptId, triggerType, recordId);
        return id;
    }
}
