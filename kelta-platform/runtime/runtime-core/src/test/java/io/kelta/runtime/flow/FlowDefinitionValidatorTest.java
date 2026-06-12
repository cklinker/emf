package io.kelta.runtime.flow;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FlowDefinitionValidator")
class FlowDefinitionValidatorTest {

    private FlowDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FlowDefinitionValidator(new ObjectMapper());
    }

    @Nested
    @DisplayName("Valid definitions")
    class ValidDefinitions {

        @Test
        @DisplayName("minimal Succeed flow passes")
        void minimalFlowPasses() {
            String json = """
                {
                    "StartAt": "Done",
                    "States": { "Done": { "Type": "Succeed" } }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertTrue(result.isValid(), () -> "expected valid, got: " + result.errors());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("chained Task -> Choice -> terminal passes")
        void chainedFlowPasses() {
            String json = """
                {
                    "StartAt": "Start",
                    "States": {
                        "Start": {
                            "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "Branch"
                        },
                        "Branch": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "BooleanEquals": true, "Next": "Yes" }
                            ],
                            "Default": "No"
                        },
                        "Yes": { "Type": "Succeed" },
                        "No": { "Type": "Fail", "Error": "E", "Cause": "C" }
                    }
                }
                """;

            assertTrue(validator.validate(json).isValid());
        }

        @Test
        @DisplayName("nested Parallel + Map with their own scopes pass")
        void nestedScopesPass() {
            String json = """
                {
                    "StartAt": "Fan",
                    "States": {
                        "Fan": {
                            "Type": "Parallel",
                            "Branches": [
                                {
                                    "StartAt": "BranchA",
                                    "States": { "BranchA": { "Type": "Succeed" } }
                                }
                            ],
                            "Next": "DoMap"
                        },
                        "DoMap": {
                            "Type": "Map",
                            "ItemsPath": "$.items",
                            "Iterator": {
                                "StartAt": "Step",
                                "States": {
                                    "Step": {
                                        "Type": "Task", "Resource": "LOG_MESSAGE", "End": true
                                    }
                                }
                            },
                            "End": true
                        }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);
            assertTrue(result.isValid(), () -> "expected valid, got: " + result.errors());
        }
    }

    @Nested
    @DisplayName("Structural failures")
    class StructuralFailures {

        @Test
        @DisplayName("missing top-level StartAt is rejected")
        void missingStartAtRejected() {
            String json = """
                { "States": { "Done": { "Type": "Succeed" } } }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("StartAt")),
                    () -> "expected StartAt error, got: " + result.errors());
        }

        @Test
        @DisplayName("missing States is rejected")
        void missingStatesRejected() {
            String json = """
                { "StartAt": "Done" }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("States")),
                    () -> "expected States error, got: " + result.errors());
        }

        @Test
        @DisplayName("blank / empty JSON yields an error")
        void blankJsonYieldsError() {
            assertFalse(validator.validate("").isValid());
            assertFalse(validator.validate("   ").isValid());
            assertFalse(validator.validate(null).isValid());
        }

        @Test
        @DisplayName("malformed JSON yields an error")
        void malformedJsonYieldsError() {
            FlowDefinitionValidator.Result result = validator.validate("{ not json");
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("unknown state type is rejected")
        void unknownStateTypeRejected() {
            String json = """
                {
                    "StartAt": "X",
                    "States": { "X": { "Type": "Unknown" } }
                }
                """;

            assertFalse(validator.validate(json).isValid());
        }

        @Test
        @DisplayName("Map without Iterator is rejected")
        void mapWithoutIteratorRejected() {
            String json = """
                {
                    "StartAt": "M",
                    "States": {
                        "M": { "Type": "Map", "ItemsPath": "$.items", "End": true }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);
            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("Iterator")),
                    () -> "expected Iterator error, got: " + result.errors());
        }

        @Test
        @DisplayName("Map iterator missing StartAt is rejected")
        void mapIteratorMissingStartAtRejected() {
            String json = """
                {
                    "StartAt": "M",
                    "States": {
                        "M": {
                            "Type": "Map",
                            "ItemsPath": "$.items",
                            "Iterator": {
                                "States": { "Step": { "Type": "Succeed" } }
                            },
                            "End": true
                        }
                    }
                }
                """;

            assertFalse(validator.validate(json).isValid());
        }
    }

    @Nested
    @DisplayName("Graph failures")
    class GraphFailures {

        @Test
        @DisplayName("dangling Task Next is rejected and names the missing target")
        void danglingNextRejected() {
            String json = """
                {
                    "StartAt": "Start",
                    "States": {
                        "Start": {
                            "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "Missing"
                        }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                    .anyMatch(m -> m.contains("Missing") && m.contains("Start")),
                    () -> "expected Start->Missing error, got: " + result.errors());
        }

        @Test
        @DisplayName("StartAt pointing at undefined state is rejected")
        void danglingStartAtRejected() {
            String json = """
                {
                    "StartAt": "Ghost",
                    "States": { "Real": { "Type": "Succeed" } }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("Ghost")),
                    () -> "expected Ghost error, got: " + result.errors());
        }

        @Test
        @DisplayName("Choice rule Next pointing at undefined state is rejected")
        void choiceNextRejected() {
            String json = """
                {
                    "StartAt": "Pick",
                    "States": {
                        "Pick": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "BooleanEquals": true, "Next": "Nowhere" }
                            ],
                            "Default": "Fallback"
                        },
                        "Fallback": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);
            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("Nowhere")));
        }

        @Test
        @DisplayName("Choice Default pointing at undefined state is rejected")
        void choiceDefaultRejected() {
            String json = """
                {
                    "StartAt": "Pick",
                    "States": {
                        "Pick": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "BooleanEquals": true, "Next": "Yes" }
                            ],
                            "Default": "Phantom"
                        },
                        "Yes": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);
            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("Phantom")));
        }

        @Test
        @DisplayName("Catch.Next pointing at undefined state is rejected")
        void catchNextRejected() {
            String json = """
                {
                    "StartAt": "Risky",
                    "States": {
                        "Risky": {
                            "Type": "Task",
                            "Resource": "HTTP_CALLOUT",
                            "End": true,
                            "Catch": [
                                { "ErrorEquals": ["States.ALL"], "Next": "NoHandler" }
                            ]
                        }
                    }
                }
                """;

            assertFalse(validator.validate(json).isValid());
        }

        @Test
        @DisplayName("nested branch dangling Next is reported with branch path context")
        void nestedBranchDanglingNext() {
            String json = """
                {
                    "StartAt": "Fan",
                    "States": {
                        "Fan": {
                            "Type": "Parallel",
                            "Branches": [
                                {
                                    "StartAt": "BranchA",
                                    "States": {
                                        "BranchA": {
                                            "Type": "Task",
                                            "Resource": "LOG_MESSAGE",
                                            "Next": "Outside"
                                        }
                                    }
                                }
                            ],
                            "End": true
                        }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(m -> m.contains("Outside")),
                    () -> "expected nested branch error referencing Outside, got: "
                            + result.errors());
        }

        @Test
        @DisplayName("state with neither Next nor End is rejected")
        void taskMissingTerminationRejected() {
            String json = """
                {
                    "StartAt": "Stranded",
                    "States": {
                        "Stranded": { "Type": "Task", "Resource": "LOG_MESSAGE" }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                    .anyMatch(m -> m.contains("Next") && m.contains("End")));
        }

        @Test
        @DisplayName("multiple problems are collected, not just the first")
        void multipleProblemsCollected() {
            String json = """
                {
                    "StartAt": "A",
                    "States": {
                        "A": { "Type": "Task", "Resource": "LOG_MESSAGE", "Next": "Missing1" },
                        "B": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.x", "BooleanEquals": true, "Next": "Missing2" }
                            ],
                            "Default": "Missing3"
                        }
                    }
                }
                """;

            FlowDefinitionValidator.Result result = validator.validate(json);

            assertFalse(result.isValid());
            assertEquals(3, result.errors().size(),
                    () -> "expected 3 dangling references, got: " + result.errors());
        }
    }
}
