package io.kelta.runtime.flow;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a flow definition JSON string by parsing it and walking the
 * resulting state graph to find dangling transitions.
 *
 * <p>The validator returns the list of structural problems instead of
 * throwing, so save-time callers can report every issue at once. An empty
 * list means the definition is structurally valid and safe to execute.
 *
 * <p>Reported errors include:
 * <ul>
 *   <li>Parse-level failures (missing {@code StartAt}, missing
 *       {@code Resource}, malformed JSON, unknown state types, &c.)</li>
 *   <li>{@code StartAt} pointing at a state that is not defined</li>
 *   <li>{@code Next}, Choice {@code Default}, Choice-rule {@code Next}, or
 *       {@code Catch.Next} pointing at a state that is not defined</li>
 *   <li>{@code Map} states without an {@code Iterator}</li>
 * </ul>
 *
 * <p>Branches of {@code Parallel} states and {@code Map} iterators are
 * validated as independent sub-graphs, since their transitions resolve
 * within their own state map.
 *
 * @since 1.0.0
 */
public class FlowDefinitionValidator {

    private final FlowDefinitionParser parser;

    public FlowDefinitionValidator(ObjectMapper objectMapper) {
        this(new FlowDefinitionParser(objectMapper));
    }

    public FlowDefinitionValidator(FlowDefinitionParser parser) {
        this.parser = parser;
    }

    /**
     * Validates the given flow definition JSON.
     *
     * @param json the flow definition JSON
     * @return ordered list of error messages — empty if the definition is valid
     */
    public List<String> validate(String json) {
        List<String> errors = new ArrayList<>();
        if (json == null || json.isBlank()) {
            errors.add("Flow definition is empty");
            return errors;
        }

        FlowDefinition definition;
        try {
            definition = parser.parse(json);
        } catch (FlowDefinitionException e) {
            errors.add(e.getMessage());
            return errors;
        }
        validateGraph(definition, errors, "");
        return errors;
    }

    private void validateGraph(FlowDefinition definition, List<String> errors, String path) {
        if (definition.startAt() == null || definition.startAt().isBlank()) {
            errors.add(path + "StartAt is missing");
        } else if (!definition.hasState(definition.startAt())) {
            errors.add(path + "StartAt '" + definition.startAt()
                    + "' does not match any defined state");
        }

        if (definition.states() == null || definition.states().isEmpty()) {
            return;
        }

        for (var entry : definition.states().entrySet()) {
            String stateId = entry.getKey();
            StateDefinition state = entry.getValue();
            String statePath = path + "State '" + stateId + "': ";

            switch (state) {
                case StateDefinition.TaskState task -> {
                    checkTarget(task.next(), "Next", definition, errors, statePath);
                    checkCatchTargets(task.catchPolicies(), definition, errors, statePath);
                }
                case StateDefinition.ChoiceState choice -> {
                    if (choice.defaultState() != null
                            && !definition.hasState(choice.defaultState())) {
                        errors.add(statePath + "Default '" + choice.defaultState()
                                + "' does not match any defined state");
                    }
                    for (ChoiceRule rule : choice.choices()) {
                        validateChoiceRule(rule, definition, errors, statePath);
                    }
                }
                case StateDefinition.ParallelState parallel -> {
                    checkTarget(parallel.next(), "Next", definition, errors, statePath);
                    checkCatchTargets(parallel.catchPolicies(), definition, errors, statePath);
                    int i = 0;
                    for (FlowDefinition branch : parallel.branches()) {
                        validateGraph(branch, errors, statePath + "branch[" + i + "] ");
                        i++;
                    }
                }
                case StateDefinition.MapState map -> {
                    checkTarget(map.next(), "Next", definition, errors, statePath);
                    if (map.iterator() == null) {
                        errors.add(statePath + "Map state must contain an 'Iterator'");
                    } else {
                        validateGraph(map.iterator(), errors, statePath + "iterator ");
                    }
                }
                case StateDefinition.WaitState wait ->
                        checkTarget(wait.next(), "Next", definition, errors, statePath);
                case StateDefinition.PassState pass ->
                        checkTarget(pass.next(), "Next", definition, errors, statePath);
                case StateDefinition.SucceedState ignored -> { }
                case StateDefinition.FailState ignored -> { }
            }
        }
    }

    private void checkTarget(String next, String fieldName, FlowDefinition definition,
                             List<String> errors, String statePath) {
        if (next != null && !definition.hasState(next)) {
            errors.add(statePath + fieldName + " '" + next
                    + "' does not match any defined state");
        }
    }

    private void checkCatchTargets(List<CatchPolicy> policies, FlowDefinition definition,
                                   List<String> errors, String statePath) {
        if (policies == null) return;
        for (CatchPolicy c : policies) {
            if (c.next() != null && !definition.hasState(c.next())) {
                errors.add(statePath + "Catch target '" + c.next()
                        + "' does not match any defined state");
            }
        }
    }

    private void validateChoiceRule(ChoiceRule rule, FlowDefinition definition,
                                    List<String> errors, String statePath) {
        if (rule.next() != null && !definition.hasState(rule.next())) {
            errors.add(statePath + "Choice rule target '" + rule.next()
                    + "' does not match any defined state");
        }
        switch (rule) {
            case ChoiceRule.And and -> {
                for (ChoiceRule inner : and.rules()) {
                    validateChoiceRule(inner, definition, errors, statePath);
                }
            }
            case ChoiceRule.Or or -> {
                for (ChoiceRule inner : or.rules()) {
                    validateChoiceRule(inner, definition, errors, statePath);
                }
            }
            case ChoiceRule.Not not -> validateChoiceRule(not.rule(), definition, errors, statePath);
            default -> { }
        }
    }
}
