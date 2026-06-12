package io.kelta.runtime.flow.executor;

import io.kelta.runtime.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Executes {@code InvokeFlow} states by loading the target flow definition
 * from the {@link FlowStore} and running it synchronously as a sub-execution
 * in the same tenant context.
 * <p>
 * Replaces copy-paste sub-flow reuse: a dispatcher flow can {@code Map} over
 * a list of inputs and {@code InvokeFlow} a shared worker flow per item with
 * mapped {@code Input}, rather than cloning the worker flow once per slug.
 * <p>
 * Depth is bounded by {@link FlowEngine#MAX_INVOKE_DEPTH} — beyond that, the
 * state fails with {@code FlowDepthExceeded} so a flow that invokes itself
 * (directly or transitively) cannot recurse forever.
 *
 * @since 1.0.0
 */
public class InvokeFlowStateExecutor implements StateExecutor {

    private static final Logger log = LoggerFactory.getLogger(InvokeFlowStateExecutor.class);

    private final FlowStore flowStore;
    private final FlowDefinitionParser parser;
    private final StateDataResolver dataResolver;
    private final FlowEngine engine;

    public InvokeFlowStateExecutor(FlowStore flowStore,
                                   FlowDefinitionParser parser,
                                   StateDataResolver dataResolver,
                                   FlowEngine engine) {
        this.flowStore = flowStore;
        this.parser = parser;
        this.dataResolver = dataResolver;
        this.engine = engine;
    }

    @Override
    public String stateType() {
        return "InvokeFlow";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.InvokeFlowState invoke = (StateDefinition.InvokeFlowState) state;

        if (context.invokeDepth() >= FlowEngine.MAX_INVOKE_DEPTH) {
            return handleError(invoke, context, "FlowDepthExceeded",
                "InvokeFlow nesting exceeded MAX_INVOKE_DEPTH=" + FlowEngine.MAX_INVOKE_DEPTH);
        }

        // Resolve target flow ID/name templates against state data so callers
        // can pick the target dynamically (e.g. "${$.targetFlowName}").
        String flowId = resolveString(invoke.flowId(), context.stateData());
        String flowName = resolveString(invoke.flowName(), context.stateData());

        Optional<String> definitionJsonOpt;
        String targetDescription;
        if (flowId != null && !flowId.isBlank()) {
            definitionJsonOpt = flowStore.findFlowDefinitionById(context.tenantId(), flowId);
            targetDescription = "flowId=" + flowId;
        } else if (flowName != null && !flowName.isBlank()) {
            definitionJsonOpt = flowStore.findFlowDefinitionByName(context.tenantId(), flowName);
            targetDescription = "flowName=" + flowName;
        } else {
            return handleError(invoke, context, "FlowNotSpecified",
                "InvokeFlow state '" + invoke.name() + "' has no FlowId or FlowName after template resolution");
        }

        if (definitionJsonOpt.isEmpty()) {
            return handleError(invoke, context, "FlowNotFound",
                "Target flow not found in tenant '" + context.tenantId() + "': " + targetDescription);
        }

        FlowDefinition targetFlow;
        try {
            targetFlow = parser.parse(definitionJsonOpt.get());
        } catch (FlowDefinitionException e) {
            return handleError(invoke, context, "FlowDefinitionInvalid",
                "Target flow definition is invalid (" + targetDescription + "): " + e.getMessage());
        }

        // Build the invoked flow's initial state. If Input is provided, it is
        // resolved against the parent's state data (templates and all) and
        // becomes the entire input. Otherwise InputPath selects a subset of
        // the parent state as the input — same convention as Task.
        Map<String, Object> subInput;
        if (invoke.input() != null && !invoke.input().isEmpty()) {
            Object resolved = dataResolver.resolveDeep(invoke.input(), context.stateData());
            if (resolved instanceof Map) {
                subInput = new LinkedHashMap<>((Map<String, Object>) resolved);
            } else {
                subInput = new LinkedHashMap<>();
                subInput.put("input", resolved);
            }
        } else {
            subInput = dataResolver.applyInputPath(context.stateData(), invoke.inputPath());
        }

        log.debug("InvokeFlow '{}' calling {} at depth {}",
            invoke.name(), targetDescription, context.invokeDepth() + 1);

        SubFlowResult result;
        try {
            result = engine.executeInvokedFlow(targetFlow, subInput, context);
        } catch (Exception e) {
            log.error("InvokeFlow '{}' failed unexpectedly", invoke.name(), e);
            return handleError(invoke, context, e.getClass().getSimpleName(),
                "InvokeFlow '" + invoke.name() + "' threw: " + e.getMessage());
        }

        // An uncaught failure inside the invoked flow is the invoking state's
        // failure too — surface it (which lets the parent's Catch policy
        // recover) so the chain doesn't silently report success.
        if (result.hadUncaughtError()) {
            return handleError(invoke, context, result.uncaughtErrorCode(),
                result.uncaughtErrorMessage());
        }

        // Propagate caught errors from the invoked flow into the parent's
        // failedCount so the top-level summary still reflects partial
        // failures inside the sub-execution.
        if (result.hadCaughtError()) {
            context.addFailedCount(result.caughtErrors().size());
        }

        Map<String, Object> afterResult = dataResolver.applyResultPath(
            context.stateData(), result.stateData(), invoke.resultPath());
        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, invoke.outputPath());

        if (invoke.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(invoke.next(), output);
    }

    private String resolveString(String value, Map<String, Object> stateData) {
        if (value == null) {
            return null;
        }
        Object resolved = dataResolver.resolveDeep(value, stateData);
        return resolved != null ? resolved.toString() : null;
    }

    private StateExecutionResult handleError(StateDefinition.InvokeFlowState invoke,
                                             FlowExecutionContext context,
                                             String errorCode, String errorMessage) {
        for (CatchPolicy catchPolicy : invoke.catchPolicies()) {
            if (catchPolicy.matches(errorCode)) {
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("Error", errorCode);
                errorData.put("Cause", errorMessage);

                Map<String, Object> stateAfterCatch = dataResolver.applyResultPath(
                    context.stateData(), errorData, catchPolicy.resultPath());

                log.debug("InvokeFlow '{}' caught error '{}' — transitioning to '{}'",
                    invoke.name(), errorCode, catchPolicy.next());
                context.recordCaughtError(invoke.name(), errorCode, errorMessage);
                return StateExecutionResult.success(catchPolicy.next(), stateAfterCatch);
            }
        }
        return StateExecutionResult.failure(errorCode, errorMessage, context.stateData());
    }
}
