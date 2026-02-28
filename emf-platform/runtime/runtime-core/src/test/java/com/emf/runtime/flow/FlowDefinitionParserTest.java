package com.emf.runtime.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowDefinitionParser")
class FlowDefinitionParserTest {

    private FlowDefinitionParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlowDefinitionParser(new ObjectMapper());
    }

    @Nested
    @DisplayName("Basic Flow Structure")
    class BasicStructure {

        @Test
        @DisplayName("parses minimal flow with single Succeed state")
        void parsesMinimalFlow() {
            String json = """
                {
                    "StartAt": "Done",
                    "States": {
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);

            assertEquals("Done", def.startAt());
            assertNull(def.comment());
            assertNull(def.metadata());
            assertEquals(1, def.states().size());
            assertTrue(def.hasState("Done"));
            assertFalse(def.hasState("Missing"));
        }

        @Test
        @DisplayName("parses flow with comment and metadata")
        void parsesCommentAndMetadata() {
            String json = """
                {
                    "Comment": "Test flow",
                    "StartAt": "Done",
                    "States": {
                        "Done": { "Type": "Succeed" }
                    },
                    "_metadata": {
                        "nodePositions": { "Done": { "x": 100, "y": 200 } }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);

            assertEquals("Test flow", def.comment());
            assertNotNull(def.metadata());
            assertNotNull(def.metadata().get("nodePositions"));
        }

        @Test
        @DisplayName("throws on missing StartAt")
        void throwsOnMissingStartAt() {
            String json = """
                {
                    "States": { "Done": { "Type": "Succeed" } }
                }
                """;

            assertThrows(FlowDefinitionException.class, () -> parser.parse(json));
        }

        @Test
        @DisplayName("throws on missing States")
        void throwsOnMissingStates() {
            String json = """
                { "StartAt": "Done" }
                """;

            assertThrows(FlowDefinitionException.class, () -> parser.parse(json));
        }

        @Test
        @DisplayName("throws on invalid JSON")
        void throwsOnInvalidJson() {
            assertThrows(FlowDefinitionException.class, () -> parser.parse("not json"));
        }
    }

    @Nested
    @DisplayName("Task State")
    class TaskStateParsing {

        @Test
        @DisplayName("parses Task state with all properties")
        void parsesFullTaskState() {
            String json = """
                {
                    "StartAt": "FetchData",
                    "States": {
                        "FetchData": {
                            "Type": "Task",
                            "Resource": "HTTP_CALLOUT",
                            "InputPath": "$.orderData",
                            "OutputPath": "$",
                            "ResultPath": "$.apiResponse",
                            "TimeoutSeconds": 30,
                            "Next": "ProcessResult"
                        },
                        "ProcessResult": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition state = def.getState("FetchData");

            assertInstanceOf(StateDefinition.TaskState.class, state);
            StateDefinition.TaskState task = (StateDefinition.TaskState) state;

            assertEquals("FetchData", task.name());
            assertEquals("Task", task.type());
            assertEquals("HTTP_CALLOUT", task.resource());
            assertEquals("$.orderData", task.inputPath());
            assertEquals("$", task.outputPath());
            assertEquals("$.apiResponse", task.resultPath());
            assertEquals(30, task.timeoutSeconds());
            assertEquals("ProcessResult", task.next());
            assertFalse(task.end());
        }

        @Test
        @DisplayName("parses Task state with End=true")
        void parsesTaskWithEnd() {
            String json = """
                {
                    "StartAt": "Final",
                    "States": {
                        "Final": {
                            "Type": "Task",
                            "Resource": "LOG_MESSAGE",
                            "End": true
                        }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.TaskState task = (StateDefinition.TaskState) def.getState("Final");

            assertTrue(task.end());
            assertNull(task.next());
        }

        @Test
        @DisplayName("parses Task state with retry and catch policies")
        void parsesTaskWithRetryAndCatch() {
            String json = """
                {
                    "StartAt": "Risky",
                    "States": {
                        "Risky": {
                            "Type": "Task",
                            "Resource": "HTTP_CALLOUT",
                            "End": true,
                            "Retry": [
                                {
                                    "ErrorEquals": ["HttpTimeout", "Http5xx"],
                                    "IntervalSeconds": 5,
                                    "MaxAttempts": 3,
                                    "BackoffRate": 2.0
                                }
                            ],
                            "Catch": [
                                {
                                    "ErrorEquals": ["States.ALL"],
                                    "Next": "HandleError",
                                    "ResultPath": "$.error"
                                }
                            ]
                        },
                        "HandleError": { "Type": "Fail", "Error": "CatchAll", "Cause": "Unhandled error" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.TaskState task = (StateDefinition.TaskState) def.getState("Risky");

            assertEquals(1, task.retry().size());
            RetryPolicy retry = task.retry().get(0);
            assertEquals(List.of("HttpTimeout", "Http5xx"), retry.errorEquals());
            assertEquals(5, retry.intervalSeconds());
            assertEquals(3, retry.maxAttempts());
            assertEquals(2.0, retry.backoffRate());

            assertEquals(1, task.catchPolicies().size());
            CatchPolicy catchPolicy = task.catchPolicies().get(0);
            assertEquals(List.of("States.ALL"), catchPolicy.errorEquals());
            assertEquals("HandleError", catchPolicy.next());
            assertEquals("$.error", catchPolicy.resultPath());
        }

        @Test
        @DisplayName("parses Task state with Name override")
        void parsesTaskWithNameOverride() {
            String json = """
                {
                    "StartAt": "step1",
                    "States": {
                        "step1": {
                            "Type": "Task",
                            "Name": "Fetch Order Data",
                            "Resource": "HTTP_CALLOUT",
                            "End": true
                        }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.TaskState task = (StateDefinition.TaskState) def.getState("step1");
            assertEquals("Fetch Order Data", task.name());
        }
    }

    @Nested
    @DisplayName("Choice State")
    class ChoiceStateParsing {

        @Test
        @DisplayName("parses Choice state with string and numeric rules")
        void parsesChoiceState() {
            String json = """
                {
                    "StartAt": "CheckStatus",
                    "States": {
                        "CheckStatus": {
                            "Type": "Choice",
                            "Choices": [
                                {
                                    "Variable": "$.status",
                                    "StringEquals": "ACTIVE",
                                    "Next": "ProcessActive"
                                },
                                {
                                    "Variable": "$.amount",
                                    "NumericGreaterThan": 100.0,
                                    "Next": "HighValue"
                                }
                            ],
                            "Default": "DefaultHandler"
                        },
                        "ProcessActive": { "Type": "Succeed" },
                        "HighValue": { "Type": "Succeed" },
                        "DefaultHandler": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition state = def.getState("CheckStatus");

            assertInstanceOf(StateDefinition.ChoiceState.class, state);
            StateDefinition.ChoiceState choice = (StateDefinition.ChoiceState) state;

            assertEquals(2, choice.choices().size());
            assertEquals("DefaultHandler", choice.defaultState());
            assertEquals("Choice", choice.type());

            // First rule: StringEquals
            assertInstanceOf(ChoiceRule.StringEquals.class, choice.choices().get(0));
            ChoiceRule.StringEquals strRule = (ChoiceRule.StringEquals) choice.choices().get(0);
            assertEquals("$.status", strRule.variable());
            assertEquals("ACTIVE", strRule.value());
            assertEquals("ProcessActive", strRule.next());

            // Second rule: NumericGreaterThan
            assertInstanceOf(ChoiceRule.NumericGreaterThan.class, choice.choices().get(1));
            ChoiceRule.NumericGreaterThan numRule = (ChoiceRule.NumericGreaterThan) choice.choices().get(1);
            assertEquals("$.amount", numRule.variable());
            assertEquals(100.0, numRule.value());
            assertEquals("HighValue", numRule.next());
        }

        @Test
        @DisplayName("parses compound And/Or/Not rules")
        void parsesCompoundRules() {
            String json = """
                {
                    "StartAt": "Check",
                    "States": {
                        "Check": {
                            "Type": "Choice",
                            "Choices": [
                                {
                                    "And": [
                                        { "Variable": "$.status", "StringEquals": "ACTIVE" },
                                        { "Variable": "$.amount", "NumericGreaterThan": 50 }
                                    ],
                                    "Next": "ActiveHighValue"
                                },
                                {
                                    "Not": {
                                        "Variable": "$.deleted",
                                        "BooleanEquals": true
                                    },
                                    "Next": "NotDeleted"
                                }
                            ],
                            "Default": "Fallback"
                        },
                        "ActiveHighValue": { "Type": "Succeed" },
                        "NotDeleted": { "Type": "Succeed" },
                        "Fallback": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.ChoiceState choice = (StateDefinition.ChoiceState) def.getState("Check");

            // And rule
            assertInstanceOf(ChoiceRule.And.class, choice.choices().get(0));
            ChoiceRule.And andRule = (ChoiceRule.And) choice.choices().get(0);
            assertEquals(2, andRule.rules().size());
            assertEquals("ActiveHighValue", andRule.next());

            // Not rule
            assertInstanceOf(ChoiceRule.Not.class, choice.choices().get(1));
            ChoiceRule.Not notRule = (ChoiceRule.Not) choice.choices().get(1);
            assertInstanceOf(ChoiceRule.BooleanEquals.class, notRule.rule());
            assertEquals("NotDeleted", notRule.next());
        }

        @Test
        @DisplayName("parses all string comparison operators")
        void parsesAllStringOperators() {
            String json = """
                {
                    "StartAt": "Check",
                    "States": {
                        "Check": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.a", "StringEquals": "x", "Next": "S1" },
                                { "Variable": "$.b", "StringNotEquals": "y", "Next": "S2" },
                                { "Variable": "$.c", "StringGreaterThan": "m", "Next": "S3" },
                                { "Variable": "$.d", "StringLessThan": "n", "Next": "S4" },
                                { "Variable": "$.e", "StringGreaterThanEquals": "o", "Next": "S5" },
                                { "Variable": "$.f", "StringLessThanEquals": "p", "Next": "S6" },
                                { "Variable": "$.g", "StringMatches": "test*", "Next": "S7" }
                            ],
                            "Default": "End"
                        },
                        "S1": { "Type": "Succeed" }, "S2": { "Type": "Succeed" },
                        "S3": { "Type": "Succeed" }, "S4": { "Type": "Succeed" },
                        "S5": { "Type": "Succeed" }, "S6": { "Type": "Succeed" },
                        "S7": { "Type": "Succeed" }, "End": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.ChoiceState choice = (StateDefinition.ChoiceState) def.getState("Check");
            assertEquals(7, choice.choices().size());

            assertInstanceOf(ChoiceRule.StringEquals.class, choice.choices().get(0));
            assertInstanceOf(ChoiceRule.StringNotEquals.class, choice.choices().get(1));
            assertInstanceOf(ChoiceRule.StringGreaterThan.class, choice.choices().get(2));
            assertInstanceOf(ChoiceRule.StringLessThan.class, choice.choices().get(3));
            assertInstanceOf(ChoiceRule.StringGreaterThanEquals.class, choice.choices().get(4));
            assertInstanceOf(ChoiceRule.StringLessThanEquals.class, choice.choices().get(5));
            assertInstanceOf(ChoiceRule.StringMatches.class, choice.choices().get(6));
        }

        @Test
        @DisplayName("parses existence check operators")
        void parsesExistenceChecks() {
            String json = """
                {
                    "StartAt": "Check",
                    "States": {
                        "Check": {
                            "Type": "Choice",
                            "Choices": [
                                { "Variable": "$.email", "IsPresent": true, "Next": "HasEmail" },
                                { "Variable": "$.phone", "IsNull": true, "Next": "NoPhone" }
                            ],
                            "Default": "End"
                        },
                        "HasEmail": { "Type": "Succeed" },
                        "NoPhone": { "Type": "Succeed" },
                        "End": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.ChoiceState choice = (StateDefinition.ChoiceState) def.getState("Check");

            assertInstanceOf(ChoiceRule.IsPresent.class, choice.choices().get(0));
            ChoiceRule.IsPresent present = (ChoiceRule.IsPresent) choice.choices().get(0);
            assertTrue(present.isPresent());

            assertInstanceOf(ChoiceRule.IsNull.class, choice.choices().get(1));
            ChoiceRule.IsNull isNull = (ChoiceRule.IsNull) choice.choices().get(1);
            assertTrue(isNull.isNull());
        }
    }

    @Nested
    @DisplayName("Parallel State")
    class ParallelStateParsing {

        @Test
        @DisplayName("parses Parallel state with branches")
        void parsesParallelState() {
            String json = """
                {
                    "StartAt": "RunBoth",
                    "States": {
                        "RunBoth": {
                            "Type": "Parallel",
                            "ResultPath": "$.parallelResults",
                            "Branches": [
                                {
                                    "StartAt": "BranchA",
                                    "States": { "BranchA": { "Type": "Succeed" } }
                                },
                                {
                                    "StartAt": "BranchB",
                                    "States": { "BranchB": { "Type": "Succeed" } }
                                }
                            ],
                            "Next": "Done"
                        },
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition state = def.getState("RunBoth");

            assertInstanceOf(StateDefinition.ParallelState.class, state);
            StateDefinition.ParallelState parallel = (StateDefinition.ParallelState) state;

            assertEquals(2, parallel.branches().size());
            assertEquals("$.parallelResults", parallel.resultPath());
            assertEquals("Done", parallel.next());
            assertEquals("Parallel", parallel.type());

            assertEquals("BranchA", parallel.branches().get(0).startAt());
            assertEquals("BranchB", parallel.branches().get(1).startAt());
        }
    }

    @Nested
    @DisplayName("Map State")
    class MapStateParsing {

        @Test
        @DisplayName("parses Map state with iterator")
        void parsesMapState() {
            String json = """
                {
                    "StartAt": "ProcessItems",
                    "States": {
                        "ProcessItems": {
                            "Type": "Map",
                            "ItemsPath": "$.items",
                            "MaxConcurrency": 5,
                            "ResultPath": "$.processedItems",
                            "Iterator": {
                                "StartAt": "ProcessItem",
                                "States": {
                                    "ProcessItem": {
                                        "Type": "Task",
                                        "Resource": "FIELD_UPDATE",
                                        "End": true
                                    }
                                }
                            },
                            "End": true
                        }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition state = def.getState("ProcessItems");

            assertInstanceOf(StateDefinition.MapState.class, state);
            StateDefinition.MapState map = (StateDefinition.MapState) state;

            assertEquals("$.items", map.itemsPath());
            assertEquals(5, map.maxConcurrency());
            assertEquals("$.processedItems", map.resultPath());
            assertTrue(map.end());
            assertEquals("Map", map.type());

            assertNotNull(map.iterator());
            assertEquals("ProcessItem", map.iterator().startAt());
        }
    }

    @Nested
    @DisplayName("Wait State")
    class WaitStateParsing {

        @Test
        @DisplayName("parses Wait state with seconds")
        void parsesWaitWithSeconds() {
            String json = """
                {
                    "StartAt": "Delay",
                    "States": {
                        "Delay": {
                            "Type": "Wait",
                            "Seconds": 60,
                            "Next": "Continue"
                        },
                        "Continue": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.WaitState wait = (StateDefinition.WaitState) def.getState("Delay");

            assertEquals(60, wait.seconds());
            assertNull(wait.timestamp());
            assertNull(wait.eventName());
            assertEquals("Continue", wait.next());
            assertEquals("Wait", wait.type());
        }

        @Test
        @DisplayName("parses Wait state with event name")
        void parsesWaitWithEvent() {
            String json = """
                {
                    "StartAt": "WaitForApproval",
                    "States": {
                        "WaitForApproval": {
                            "Type": "Wait",
                            "EventName": "approval.received",
                            "Next": "Approved"
                        },
                        "Approved": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.WaitState wait = (StateDefinition.WaitState) def.getState("WaitForApproval");

            assertNull(wait.seconds());
            assertEquals("approval.received", wait.eventName());
        }
    }

    @Nested
    @DisplayName("Pass State")
    class PassStateParsing {

        @Test
        @DisplayName("parses Pass state with literal result")
        void parsesPassWithResult() {
            String json = """
                {
                    "StartAt": "InjectDefaults",
                    "States": {
                        "InjectDefaults": {
                            "Type": "Pass",
                            "Result": { "priority": "MEDIUM", "retries": 3 },
                            "ResultPath": "$.defaults",
                            "Next": "Process"
                        },
                        "Process": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.PassState pass = (StateDefinition.PassState) def.getState("InjectDefaults");

            assertNotNull(pass.result());
            assertEquals("MEDIUM", pass.result().get("priority"));
            assertEquals(3, pass.result().get("retries"));
            assertEquals("$.defaults", pass.resultPath());
            assertEquals("Pass", pass.type());
        }
    }

    @Nested
    @DisplayName("Fail State")
    class FailStateParsing {

        @Test
        @DisplayName("parses Fail state with error and cause")
        void parsesFailState() {
            String json = """
                {
                    "StartAt": "Error",
                    "States": {
                        "Error": {
                            "Type": "Fail",
                            "Error": "ValidationFailed",
                            "Cause": "Required field 'email' is missing"
                        }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.FailState fail = (StateDefinition.FailState) def.getState("Error");

            assertEquals("ValidationFailed", fail.error());
            assertEquals("Required field 'email' is missing", fail.cause());
            assertEquals("Fail", fail.type());
        }
    }

    @Nested
    @DisplayName("Complex Flow")
    class ComplexFlow {

        @Test
        @DisplayName("parses a multi-state flow with chained transitions")
        void parsesChainedFlow() {
            String json = """
                {
                    "Comment": "Order processing flow",
                    "StartAt": "ValidateOrder",
                    "States": {
                        "ValidateOrder": {
                            "Type": "Task",
                            "Resource": "QUERY_RECORDS",
                            "ResultPath": "$.validation",
                            "Next": "CheckValid"
                        },
                        "CheckValid": {
                            "Type": "Choice",
                            "Choices": [
                                {
                                    "Variable": "$.validation.isValid",
                                    "BooleanEquals": true,
                                    "Next": "ProcessPayment"
                                }
                            ],
                            "Default": "RejectOrder"
                        },
                        "ProcessPayment": {
                            "Type": "Task",
                            "Resource": "HTTP_CALLOUT",
                            "ResultPath": "$.payment",
                            "Next": "SendConfirmation",
                            "Retry": [
                                {
                                    "ErrorEquals": ["HttpTimeout"],
                                    "IntervalSeconds": 2,
                                    "MaxAttempts": 3,
                                    "BackoffRate": 2.0
                                }
                            ],
                            "Catch": [
                                {
                                    "ErrorEquals": ["States.ALL"],
                                    "Next": "PaymentFailed",
                                    "ResultPath": "$.error"
                                }
                            ]
                        },
                        "SendConfirmation": {
                            "Type": "Task",
                            "Resource": "EMAIL_ALERT",
                            "End": true
                        },
                        "RejectOrder": {
                            "Type": "Fail",
                            "Error": "InvalidOrder",
                            "Cause": "Order validation failed"
                        },
                        "PaymentFailed": {
                            "Type": "Fail",
                            "Error": "PaymentError",
                            "Cause": "Payment processing failed"
                        }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);

            assertEquals("Order processing flow", def.comment());
            assertEquals("ValidateOrder", def.startAt());
            assertEquals(6, def.states().size());

            // Verify chain
            StateDefinition.TaskState validate = (StateDefinition.TaskState) def.getState("ValidateOrder");
            assertEquals("CheckValid", validate.next());

            StateDefinition.ChoiceState check = (StateDefinition.ChoiceState) def.getState("CheckValid");
            assertEquals(1, check.choices().size());
            assertEquals("RejectOrder", check.defaultState());

            StateDefinition.TaskState payment = (StateDefinition.TaskState) def.getState("ProcessPayment");
            assertEquals("SendConfirmation", payment.next());
            assertEquals(1, payment.retry().size());
            assertEquals(1, payment.catchPolicies().size());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("unknown state type throws exception")
        void unknownStateTypeThrows() {
            String json = """
                {
                    "StartAt": "Bad",
                    "States": {
                        "Bad": { "Type": "Unknown" }
                    }
                }
                """;

            assertThrows(FlowDefinitionException.class, () -> parser.parse(json));
        }

        @Test
        @DisplayName("Task state without Resource throws exception")
        void taskWithoutResourceThrows() {
            String json = """
                {
                    "StartAt": "NoResource",
                    "States": {
                        "NoResource": { "Type": "Task", "End": true }
                    }
                }
                """;

            assertThrows(FlowDefinitionException.class, () -> parser.parse(json));
        }

        @Test
        @DisplayName("Choice state without Choices array throws exception")
        void choiceWithoutChoicesThrows() {
            String json = """
                {
                    "StartAt": "NoChoices",
                    "States": {
                        "NoChoices": { "Type": "Choice" }
                    }
                }
                """;

            assertThrows(FlowDefinitionException.class, () -> parser.parse(json));
        }

        @Test
        @DisplayName("Task with empty retry and catch uses empty lists")
        void emptyRetryAndCatch() {
            String json = """
                {
                    "StartAt": "Simple",
                    "States": {
                        "Simple": {
                            "Type": "Task",
                            "Resource": "LOG_MESSAGE",
                            "End": true
                        }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            StateDefinition.TaskState task = (StateDefinition.TaskState) def.getState("Simple");

            assertTrue(task.retry().isEmpty());
            assertTrue(task.catchPolicies().isEmpty());
        }

        @Test
        @DisplayName("state ID is used as name when Name not specified")
        void stateIdAsDefaultName() {
            String json = """
                {
                    "StartAt": "MyStateId",
                    "States": {
                        "MyStateId": { "Type": "Succeed" }
                    }
                }
                """;

            FlowDefinition def = parser.parse(json);
            assertEquals("MyStateId", def.getState("MyStateId").name());
        }
    }
}
