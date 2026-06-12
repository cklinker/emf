package io.kelta.runtime.flow;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FlowDefinitionValidator")
class FlowDefinitionValidatorTest {

    private FlowDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FlowDefinitionValidator(new ObjectMapper());
    }

    @Test
    @DisplayName("returns no errors for a valid single-state flow")
    void validMinimalFlow() {
        List<String> errors = validator.validate("""
                { "StartAt": "Done", "States": { "Done": { "Type": "Succeed" } } }
                """);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("returns no errors for a valid chained flow")
    void validChainedFlow() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "A",
                    "States": {
                        "A": { "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "B" },
                        "B": { "Type": "Succeed" }
                    }
                }
                """);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("reports missing StartAt as a parse error")
    void missingStartAt() {
        List<String> errors = validator.validate("""
                { "States": { "Done": { "Type": "Succeed" } } }
                """);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("StartAt");
    }

    @Test
    @DisplayName("reports StartAt pointing at an undefined state")
    void startAtUnknownState() {
        List<String> errors = validator.validate("""
                { "StartAt": "Missing", "States": { "Done": { "Type": "Succeed" } } }
                """);

        assertThat(errors).containsExactly(
                "StartAt 'Missing' does not match any defined state");
    }

    @Test
    @DisplayName("reports dangling Task Next")
    void danglingTaskNext() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "A",
                    "States": {
                        "A": { "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "Ghost" }
                    }
                }
                """);

        assertThat(errors).containsExactly(
                "State 'A': Next 'Ghost' does not match any defined state");
    }

    @Test
    @DisplayName("reports dangling Choice Default")
    void danglingChoiceDefault() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Decide",
                    "States": {
                        "Decide": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "StringEquals": "y", "Next": "Yes" }
                            ],
                            "Default": "Ghost"
                        },
                        "Yes": { "Type": "Succeed" }
                    }
                }
                """);

        assertThat(errors).containsExactly(
                "State 'Decide': Default 'Ghost' does not match any defined state");
    }

    @Test
    @DisplayName("reports dangling Choice rule Next")
    void danglingChoiceRuleNext() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Decide",
                    "States": {
                        "Decide": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "StringEquals": "y", "Next": "Ghost" }
                            ],
                            "Default": "Fallback"
                        },
                        "Fallback": { "Type": "Succeed" }
                    }
                }
                """);

        assertThat(errors).containsExactly(
                "State 'Decide': Choice rule target 'Ghost' does not match any defined state");
    }

    @Test
    @DisplayName("reports dangling Catch.Next")
    void danglingCatchNext() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Risky",
                    "States": {
                        "Risky": {
                            "Type": "Task",
                            "Resource": "HTTP_CALLOUT",
                            "End": true,
                            "Catch": [
                                { "ErrorEquals": ["States.ALL"], "Next": "Ghost" }
                            ]
                        }
                    }
                }
                """);

        assertThat(errors).containsExactly(
                "State 'Risky': Catch target 'Ghost' does not match any defined state");
    }

    @Test
    @DisplayName("reports a Map state without an Iterator")
    void mapWithoutIterator() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Loop",
                    "States": {
                        "Loop": { "Type": "Map", "ItemsPath": "$.items", "End": true }
                    }
                }
                """);

        assertThat(errors).containsExactly(
                "State 'Loop': Map state must contain an 'Iterator'");
    }

    @Test
    @DisplayName("reports a Map iterator missing StartAt as a parse error")
    void mapIteratorMissingStartAt() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Loop",
                    "States": {
                        "Loop": {
                            "Type": "Map",
                            "ItemsPath": "$.items",
                            "Iterator": {
                                "States": { "Inner": { "Type": "Succeed" } }
                            },
                            "End": true
                        }
                    }
                }
                """);

        // Parser throws on the inner missing StartAt; validator returns the message.
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("StartAt");
    }

    @Test
    @DisplayName("validates Parallel branches as independent sub-graphs")
    void parallelBranchValidation() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Fan",
                    "States": {
                        "Fan": {
                            "Type": "Parallel",
                            "End": true,
                            "Branches": [
                                {
                                    "StartAt": "A",
                                    "States": {
                                        "A": { "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "Ghost" }
                                    }
                                }
                            ]
                        }
                    }
                }
                """);

        assertThat(errors).anyMatch(e -> e.contains("branch[0]"))
                .anyMatch(e -> e.contains("'Ghost'"));
    }

    @Test
    @DisplayName("collects multiple errors in one pass")
    void collectsMultipleErrors() {
        List<String> errors = validator.validate("""
                {
                    "StartAt": "Ghost",
                    "States": {
                        "A": { "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "AlsoMissing" },
                        "B": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "StringEquals": "y", "Next": "Nowhere" }
                            ],
                            "Default": "Nope"
                        }
                    }
                }
                """);

        assertThat(errors).hasSize(4)
                .anyMatch(e -> e.startsWith("StartAt 'Ghost'"))
                .anyMatch(e -> e.contains("'AlsoMissing'"))
                .anyMatch(e -> e.contains("'Nowhere'"))
                .anyMatch(e -> e.contains("'Nope'"));
    }

    @Test
    @DisplayName("rejects empty JSON input")
    void emptyInput() {
        assertThat(validator.validate("")).containsExactly("Flow definition is empty");
        assertThat(validator.validate(null)).containsExactly("Flow definition is empty");
    }
}
