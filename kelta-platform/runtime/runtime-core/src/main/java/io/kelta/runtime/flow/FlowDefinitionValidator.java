package io.kelta.runtime.flow;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a flow definition JSON for save-time correctness.
 *
 * <p>Combines two layers of checks so authoring errors surface at the write path
 * (HTTP 400) instead of at execution time (silent persist, runtime crash):
 *
 * <ol>
 *   <li><b>Structural parse</b> via {@link FlowDefinitionParser} — catches
 *       missing {@code StartAt}, missing {@code States}, missing required
 *       state fields ({@code Resource}, {@code Choices}, {@code Branches}),
 *       unknown state types, and malformed nested flows (Parallel branches,
 *       Map iterators).</li>
 *   <li><b>Graph check</b> — every {@code StartAt}, {@code Next},
 *       {@code Default}, choice-rule {@code Next}, and {@code Catch.Next}
 *       must resolve to a state defined in the same scope. The top-level
 *       definition and each nested branch/iterator have independent scopes.</li>
 * </ol>
 *
 * <p>Unlike the parser, this class never throws — it returns a {@link Result}
 * with every issue it found, so the caller can return one 400 enumerating
 * all problems at once.
 *
 * @since 1.0.0
 */
public class FlowDefinitionValidator {

    private final FlowDefinitionParser parser;

    public FlowDefinitionValidator(ObjectMapper objectMapper) {
        this.parser = new FlowDefinitionParser(objectMapper);
    }

    /**
     * Validates the flow definition JSON. Returns a {@link Result} containing
     * every problem found; the result is empty when the definition is valid.
     *
     * @param json the flow definition JSON; null or blank yields a single
     *             "definition is empty" error
     */
    public Result validate(String json) {
        List<String> errors = new ArrayList<>();
        if (json == null || json.isBlank()) {
            errors.add("Flow definition is empty");
            return new Result(errors);
        }

        FlowDefinition definition;
        try {
            definition = parser.parse(json);
        } catch (FlowDefinitionException e) {
            errors.add(e.getMessage());
            return new Result(errors);
        }

        validateScope(definition, "", errors);
        return new Result(errors);
    }

    /**
     * Walks a flow scope (top-level or nested branch/iterator) and records
     * every reference to a state name that isn't defined in that scope.
     * Nested Parallel branches and Map iterators are recursed into with
     * their own scopes — Step Functions semantics, where each branch is a
     * self-contained state machine.
     */
    private void validateScope(FlowDefinition scope, String path, List<String> errors) {
        String prefix = path.isEmpty() ? "" : path + ": ";

        if (scope.startAt() == null || scope.startAt().isBlank()) {
            errors.add(prefix + "StartAt is missing or blank");
        } else if (!scope.hasState(scope.startAt())) {
            errors.add(prefix + "StartAt '" + scope.startAt() + "' is not a defined state");
        }

        if (scope.states() == null || scope.states().isEmpty()) {
            errors.add(prefix + "States is empty");
            return;
        }

        for (var entry : scope.states().entrySet()) {
            String stateId = entry.getKey();
            StateDefinition state = entry.getValue();
            String statePath = path.isEmpty() ? stateId : path + "." + stateId;
            validateState(stateId, state, scope, statePath, errors);
        }
    }

    private void validateState(String stateId, StateDefinition state, FlowDefinition scope,
                                String statePath, List<String> errors) {
        switch (state) {
            case StateDefinition.TaskState task -> {
                checkTransition(stateId, task.next(), task.end(), scope, errors);
                for (CatchPolicy c : task.catchPolicies()) {
                    checkTarget(stateId + ".Catch", c.next(), scope, errors);
                }
            }
            case StateDefinition.ChoiceState choice -> {
                if (choice.choices() == null || choice.choices().isEmpty()) {
                    errors.add("State '" + stateId + "': Choices is empty");
                }
                if (choice.defaultState() != null) {
                    checkTarget(stateId + ".Default", choice.defaultState(), scope, errors);
                }
                if (choice.choices() != null) {
                    for (int i = 0; i < choice.choices().size(); i++) {
                        ChoiceRule rule = choice.choices().get(i);
                        String rulePath = stateId + ".Choices[" + i + "]";
                        validateChoiceRule(rule, rulePath, scope, errors);
                    }
                }
            }
            case StateDefinition.ParallelState parallel -> {
                checkTransition(stateId, parallel.next(), parallel.end(), scope, errors);
                for (CatchPolicy c : parallel.catchPolicies()) {
                    checkTarget(stateId + ".Catch", c.next(), scope, errors);
                }
                if (parallel.branches() == null || parallel.branches().isEmpty()) {
                    errors.add("State '" + stateId + "': Branches is empty");
                } else {
                    for (int i = 0; i < parallel.branches().size(); i++) {
                        validateScope(parallel.branches().get(i),
                                statePath + ".Branches[" + i + "]", errors);
                    }
                }
            }
            case StateDefinition.MapState map -> {
                checkTransition(stateId, map.next(), map.end(), scope, errors);
                if (map.iterator() == null) {
                    errors.add("State '" + stateId + "': Map state requires an Iterator");
                } else {
                    validateScope(map.iterator(), statePath + ".Iterator", errors);
                }
            }
            case StateDefinition.WaitState wait ->
                checkTransition(stateId, wait.next(), wait.end(), scope, errors);
            case StateDefinition.PassState pass ->
                checkTransition(stateId, pass.next(), pass.end(), scope, errors);
            case StateDefinition.SucceedState ignored -> { /* terminal */ }
            case StateDefinition.FailState ignored -> { /* terminal */ }
        }
    }

    /**
     * Verifies a non-terminal state has either {@code End=true} or a {@code Next}
     * that points to a defined state. A state with neither would silently strand
     * the executor; with both, {@code End} wins but the dangling Next still
     * deserves a warning.
     */
    private void checkTransition(String stateId, String next, boolean end,
                                  FlowDefinition scope, List<String> errors) {
        if (end) {
            return;
        }
        if (next == null || next.isBlank()) {
            errors.add("State '" + stateId + "': must have either Next or End=true");
            return;
        }
        if (!scope.hasState(next)) {
            errors.add("State '" + stateId + "': Next '" + next + "' is not a defined state");
        }
    }

    private void checkTarget(String contextLabel, String target, FlowDefinition scope,
                              List<String> errors) {
        if (target == null || target.isBlank()) {
            return;
        }
        if (!scope.hasState(target)) {
            errors.add(contextLabel + ": '" + target + "' is not a defined state");
        }
    }

    private void validateChoiceRule(ChoiceRule rule, String rulePath, FlowDefinition scope,
                                     List<String> errors) {
        checkTarget(rulePath, rule.next(), scope, errors);
        switch (rule) {
            case ChoiceRule.And and -> {
                for (int i = 0; i < and.rules().size(); i++) {
                    validateChoiceRule(and.rules().get(i), rulePath + ".And[" + i + "]",
                            scope, errors);
                }
            }
            case ChoiceRule.Or or -> {
                for (int i = 0; i < or.rules().size(); i++) {
                    validateChoiceRule(or.rules().get(i), rulePath + ".Or[" + i + "]",
                            scope, errors);
                }
            }
            case ChoiceRule.Not not ->
                validateChoiceRule(not.rule(), rulePath + ".Not", scope, errors);
            default -> { /* leaf rule — Next was already checked */ }
        }
    }

    /**
     * Outcome of {@link #validate(String)}. {@link #isValid()} returns true
     * when no problems were found; otherwise {@link #errors()} lists every
     * issue in author-friendly form.
     */
    public record Result(List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
