package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;

import java.util.Map;

/**
 * Executes Pass states by optionally injecting a literal result
 * and applying data flow transforms.
 *
 * @since 1.0.0
 */
public class PassStateExecutor implements StateExecutor {

    private final StateDataResolver dataResolver;

    public PassStateExecutor(StateDataResolver dataResolver) {
        this.dataResolver = dataResolver;
    }

    @Override
    public String stateType() {
        return "Pass";
    }

    @Override
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.PassState pass = (StateDefinition.PassState) state;

        Map<String, Object> input = dataResolver.applyInputPath(context.stateData(), pass.inputPath());
        Map<String, Object> afterResult;

        if (pass.result() != null) {
            afterResult = dataResolver.applyResultPath(context.stateData(), pass.result(), pass.resultPath());
        } else {
            afterResult = context.stateData();
        }

        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, pass.outputPath());

        if (pass.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(pass.next(), output);
    }
}
