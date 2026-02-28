package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;

/**
 * Executes Fail states — terminal failure with error code and cause.
 *
 * @since 1.0.0
 */
public class FailStateExecutor implements StateExecutor {

    @Override
    public String stateType() {
        return "Fail";
    }

    @Override
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.FailState fail = (StateDefinition.FailState) state;
        return StateExecutionResult.terminalFailure(
            fail.error() != null ? fail.error() : "UnknownError",
            fail.cause(),
            context.stateData()
        );
    }
}
