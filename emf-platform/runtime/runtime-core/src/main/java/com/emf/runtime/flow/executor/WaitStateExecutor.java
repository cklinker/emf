package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Executes Wait states. For short durations, sleeps in-process.
 * For longer durations or event-based waits, returns a WAITING result
 * so the engine can persist and resume later.
 *
 * @since 1.0.0
 */
public class WaitStateExecutor implements StateExecutor {

    private static final Logger log = LoggerFactory.getLogger(WaitStateExecutor.class);

    /**
     * Maximum in-process wait time in seconds. Beyond this, the execution
     * is persisted and resumed later by the scheduled executor.
     */
    private static final int MAX_INLINE_WAIT_SECONDS = 10;

    @Override
    public String stateType() {
        return "Wait";
    }

    @Override
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.WaitState wait = (StateDefinition.WaitState) state;
        Map<String, Object> stateData = context.stateData();

        // Event-based wait — always persist and wait for external resume
        if (wait.eventName() != null) {
            log.debug("Wait '{}' waiting for event '{}'", wait.name(), wait.eventName());
            return StateExecutionResult.waiting(stateData);
        }

        // Time-based wait
        if (wait.seconds() != null) {
            if (wait.seconds() <= MAX_INLINE_WAIT_SECONDS) {
                // Short wait — sleep in-process
                sleep(wait.seconds() * 1000L);
                if (wait.end()) {
                    return StateExecutionResult.terminalSuccess(stateData);
                }
                return StateExecutionResult.success(wait.next(), stateData);
            } else {
                // Long wait — persist for scheduled resume
                log.debug("Wait '{}' persisting for {}s resume", wait.name(), wait.seconds());
                return StateExecutionResult.waiting(stateData);
            }
        }

        // Timestamp-based wait — always persist
        if (wait.timestamp() != null || wait.timestampPath() != null) {
            return StateExecutionResult.waiting(stateData);
        }

        // No wait criteria — pass through
        if (wait.end()) {
            return StateExecutionResult.terminalSuccess(stateData);
        }
        return StateExecutionResult.success(wait.next(), stateData);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
