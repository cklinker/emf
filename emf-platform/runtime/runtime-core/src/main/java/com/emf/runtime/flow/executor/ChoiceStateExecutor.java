package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Executes Choice states by evaluating choice rules against state data
 * and selecting the appropriate transition.
 *
 * @since 1.0.0
 */
public class ChoiceStateExecutor implements StateExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChoiceStateExecutor.class);

    private final ChoiceRuleEvaluator ruleEvaluator;

    public ChoiceStateExecutor(ChoiceRuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    @Override
    public String stateType() {
        return "Choice";
    }

    @Override
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.ChoiceState choice = (StateDefinition.ChoiceState) state;
        Map<String, Object> stateData = context.stateData();

        // Evaluate each rule in order
        for (ChoiceRule rule : choice.choices()) {
            if (ruleEvaluator.evaluate(rule, stateData)) {
                log.debug("Choice '{}' matched rule → '{}'", choice.name(), rule.next());
                return StateExecutionResult.success(rule.next(), stateData);
            }
        }

        // No rule matched — use default
        if (choice.defaultState() != null) {
            log.debug("Choice '{}' using default → '{}'", choice.name(), choice.defaultState());
            return StateExecutionResult.success(choice.defaultState(), stateData);
        }

        // No default — error
        return StateExecutionResult.failure("States.NoChoiceMatched",
            "No choice rule matched and no default state defined for '" + choice.name() + "'",
            stateData);
    }
}
