package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;

/**
 * Executes Succeed states — terminal success.
 *
 * @since 1.0.0
 */
public class SucceedStateExecutor implements StateExecutor {

    @Override
    public String stateType() {
        return "Succeed";
    }

    @Override
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        return StateExecutionResult.terminalSuccess(context.stateData());
    }
}
