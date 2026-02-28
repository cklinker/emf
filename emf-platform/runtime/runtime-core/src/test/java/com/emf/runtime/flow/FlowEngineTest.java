package com.emf.runtime.flow;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowEngine")
class FlowEngineTest {

    private FlowEngine engine;
    private InMemoryFlowStore flowStore;
    private ActionHandlerRegistry handlerRegistry;

    @BeforeEach
    void setUp() {
        flowStore = new InMemoryFlowStore();
        handlerRegistry = new ActionHandlerRegistry();
        ObjectMapper objectMapper = new ObjectMapper();

        // Register a simple test handler
        handlerRegistry.register(new ActionHandler() {
            @Override
            public String getActionTypeKey() { return "LOG_MESSAGE"; }

            @Override
            public ActionResult execute(ActionContext context) {
                return ActionResult.success(Map.of("logged", true));
            }
        });

        // Register an echo handler that returns its input
        handlerRegistry.register(new ActionHandler() {
            @Override
            public String getActionTypeKey() { return "ECHO"; }

            @Override
            public ActionResult execute(ActionContext context) {
                return ActionResult.success(context.data() != null ? context.data() : Map.of());
            }
        });

        // Register a failing handler
        handlerRegistry.register(new ActionHandler() {
            @Override
            public String getActionTypeKey() { return "FAIL_HANDLER"; }

            @Override
            public ActionResult execute(ActionContext context) {
                return ActionResult.failure("Handler intentionally failed");
            }
        });

        engine = new FlowEngine(flowStore, handlerRegistry, objectMapper, 2);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Nested
    @DisplayName("Simple Flows")
    class SimpleFlows {

        @Test
        @DisplayName("executes single Task → Succeed flow")
        void singleTaskToSucceed() {
            String json = """
                {
                    "StartAt": "Log",
                    "States": {
                        "Log": {
                            "Type": "Task",
                            "Resource": "LOG_MESSAGE",
                            "ResultPath": "$.logResult",
                            "Next": "Done"
                        },
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            Map<String, Object> input = Map.of("message", "hello");
            Map<String, Object> result = engine.executeSynchronous("t1", "f1", json, input, "u1");

            assertNotNull(result);
            // Result should have the log result at $.logResult
            assertNotNull(result.get("logResult"));
        }

        @Test
        @DisplayName("executes Pass state with literal result")
        void passStateWithResult() {
            String json = """
                {
                    "StartAt": "Inject",
                    "States": {
                        "Inject": {
                            "Type": "Pass",
                            "Result": { "defaultPriority": "HIGH" },
                            "ResultPath": "$.defaults",
                            "Next": "Done"
                        },
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            Map<String, Object> result = engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            assertNotNull(result.get("defaults"));
            @SuppressWarnings("unchecked")
            Map<String, Object> defaults = (Map<String, Object>) result.get("defaults");
            assertEquals("HIGH", defaults.get("defaultPriority"));
        }

        @Test
        @DisplayName("Fail state produces failure")
        void failStateProducesFailure() {
            String json = """
                {
                    "StartAt": "Error",
                    "States": {
                        "Error": {
                            "Type": "Fail",
                            "Error": "ValidationFailed",
                            "Cause": "Missing required field"
                        }
                    }
                }
                """;

            engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            // The execution should be marked as FAILED
            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            assertFalse(executions.isEmpty());
            assertEquals(FlowExecutionData.STATUS_FAILED, executions.get(0).status());
        }
    }

    @Nested
    @DisplayName("Choice Branching")
    class ChoiceBranching {

        @Test
        @DisplayName("Choice routes to matching rule")
        void choiceRoutesToMatchingRule() {
            String json = """
                {
                    "StartAt": "Check",
                    "States": {
                        "Check": {
                            "Type": "Choice",
                            "Choices": [
                                {
                                    "Variable": "$.status",
                                    "StringEquals": "ACTIVE",
                                    "Next": "ActivePath"
                                }
                            ],
                            "Default": "DefaultPath"
                        },
                        "ActivePath": { "Type": "Succeed" },
                        "DefaultPath": {
                            "Type": "Fail",
                            "Error": "UnexpectedDefault",
                            "Cause": "Should not reach default"
                        }
                    }
                }
                """;

            Map<String, Object> input = Map.of("status", "ACTIVE");
            Map<String, Object> result = engine.executeSynchronous("t1", "f1", json, input, "u1");

            // Should have reached Succeed, not Fail
            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            assertEquals(FlowExecutionData.STATUS_COMPLETED, executions.get(0).status());
        }

        @Test
        @DisplayName("Choice falls through to default")
        void choiceFallsToDefault() {
            String json = """
                {
                    "StartAt": "Check",
                    "States": {
                        "Check": {
                            "Type": "Choice",
                            "Choices": [
                                {
                                    "Variable": "$.status",
                                    "StringEquals": "ACTIVE",
                                    "Next": "ActivePath"
                                }
                            ],
                            "Default": "DefaultPath"
                        },
                        "ActivePath": {
                            "Type": "Fail",
                            "Error": "UnexpectedActive",
                            "Cause": "Should not match"
                        },
                        "DefaultPath": { "Type": "Succeed" }
                    }
                }
                """;

            Map<String, Object> input = Map.of("status", "INACTIVE");
            engine.executeSynchronous("t1", "f1", json, input, "u1");

            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            assertEquals(FlowExecutionData.STATUS_COMPLETED, executions.get(0).status());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Catch policy redirects to fallback state")
        void catchRedirectsToFallback() {
            String json = """
                {
                    "StartAt": "Risky",
                    "States": {
                        "Risky": {
                            "Type": "Task",
                            "Resource": "FAIL_HANDLER",
                            "Next": "ShouldNotReach",
                            "Catch": [
                                {
                                    "ErrorEquals": ["States.ALL"],
                                    "Next": "HandleError",
                                    "ResultPath": "$.error"
                                }
                            ]
                        },
                        "ShouldNotReach": { "Type": "Succeed" },
                        "HandleError": { "Type": "Succeed" }
                    }
                }
                """;

            Map<String, Object> result = engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            // Should have been caught and redirected to HandleError → Succeed
            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            assertEquals(FlowExecutionData.STATUS_COMPLETED, executions.get(0).status());

            // Error data should be at $.error
            assertNotNull(result.get("error"));
        }

        @Test
        @DisplayName("uncaught failure fails execution")
        void uncaughtFailureFailsExecution() {
            String json = """
                {
                    "StartAt": "Risky",
                    "States": {
                        "Risky": {
                            "Type": "Task",
                            "Resource": "FAIL_HANDLER",
                            "End": true
                        }
                    }
                }
                """;

            engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            assertEquals(FlowExecutionData.STATUS_FAILED, executions.get(0).status());
        }

        @Test
        @DisplayName("missing resource produces failure")
        void missingResourceFailsExecution() {
            String json = """
                {
                    "StartAt": "Bad",
                    "States": {
                        "Bad": {
                            "Type": "Task",
                            "Resource": "NON_EXISTENT",
                            "End": true
                        }
                    }
                }
                """;

            engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            assertEquals(FlowExecutionData.STATUS_FAILED, executions.get(0).status());
        }
    }

    @Nested
    @DisplayName("Data Flow")
    class DataFlow {

        @Test
        @DisplayName("InputPath → ResultPath → OutputPath pipeline")
        void fullDataFlowPipeline() {
            String json = """
                {
                    "StartAt": "Process",
                    "States": {
                        "Process": {
                            "Type": "Task",
                            "Resource": "ECHO",
                            "InputPath": "$.order",
                            "ResultPath": "$.processResult",
                            "Next": "Done"
                        },
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("order", Map.of("id", "ord-1", "amount", 100));
            input.put("metadata", Map.of("source", "api"));

            Map<String, Object> result = engine.executeSynchronous("t1", "f1", json, input, "u1");

            // processResult should contain the echo of the order data
            assertNotNull(result.get("processResult"));
            // metadata should be preserved
            assertNotNull(result.get("metadata"));
        }

        @Test
        @DisplayName("chained tasks accumulate state")
        void chainedTasksAccumulateState() {
            String json = """
                {
                    "StartAt": "Step1",
                    "States": {
                        "Step1": {
                            "Type": "Pass",
                            "Result": { "step1": "done" },
                            "ResultPath": "$.results.step1",
                            "Next": "Step2"
                        },
                        "Step2": {
                            "Type": "Pass",
                            "Result": { "step2": "done" },
                            "ResultPath": "$.results.step2",
                            "Next": "Done"
                        },
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            Map<String, Object> result = engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            @SuppressWarnings("unchecked")
            Map<String, Object> results = (Map<String, Object>) result.get("results");
            assertNotNull(results);
            assertNotNull(results.get("step1"));
            assertNotNull(results.get("step2"));
        }
    }

    @Nested
    @DisplayName("Step Logging")
    class StepLogging {

        @Test
        @DisplayName("records step logs for each state")
        void recordsStepLogs() {
            String json = """
                {
                    "StartAt": "A",
                    "States": {
                        "A": { "Type": "Pass", "Next": "B" },
                        "B": { "Type": "Pass", "Next": "C" },
                        "C": { "Type": "Succeed" }
                    }
                }
                """;

            engine.executeSynchronous("t1", "f1", json, Map.of(), "u1");

            List<FlowExecutionData> executions = flowStore.getAllExecutions();
            String execId = executions.get(0).id();
            List<FlowStepLogData> steps = flowStore.loadStepLogs(execId);

            assertEquals(3, steps.size());
        }
    }

    @Nested
    @DisplayName("Async Execution")
    class AsyncExecution {

        @Test
        @DisplayName("startExecution returns execution ID immediately")
        void startExecutionReturnsImmediately() {
            String json = """
                {
                    "StartAt": "Done",
                    "States": {
                        "Done": { "Type": "Succeed" }
                    }
                }
                """;

            String executionId = engine.startExecution("t1", "f1", json, Map.of(), "u1", false);
            assertNotNull(executionId);
            assertFalse(executionId.isEmpty());
        }

        @Test
        @DisplayName("cancel execution marks as cancelled")
        void cancelExecution() {
            String executionId = flowStore.createExecution("t1", "f1", "u1", null, Map.of(), false);
            engine.cancelExecution(executionId);

            Optional<FlowExecutionData> exec = flowStore.loadExecution(executionId);
            assertTrue(exec.isPresent());
            assertEquals(FlowExecutionData.STATUS_CANCELLED, exec.get().status());
        }
    }

    // -------------------------------------------------------------------------
    // In-Memory FlowStore for testing
    // -------------------------------------------------------------------------

    static class InMemoryFlowStore implements FlowStore {
        private final Map<String, FlowExecutionData> executions = new ConcurrentHashMap<>();
        private final Map<String, List<FlowStepLogData>> stepLogs = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> pendingResumes = new ConcurrentHashMap<>();

        @Override
        public String createExecution(String tenantId, String flowId, String startedBy,
                                      String triggerRecordId, Map<String, Object> initialInput,
                                      boolean isTest) {
            String id = UUID.randomUUID().toString();
            executions.put(id, new FlowExecutionData(
                id, tenantId, flowId, FlowExecutionData.STATUS_RUNNING, startedBy,
                triggerRecordId, initialInput != null ? initialInput : Map.of(),
                null, null, 0, null, initialInput, isTest, Instant.now(), null));
            stepLogs.put(id, new ArrayList<>());
            return id;
        }

        @Override
        public Optional<FlowExecutionData> loadExecution(String executionId) {
            return Optional.ofNullable(executions.get(executionId));
        }

        @Override
        public void updateExecutionState(String executionId, String currentNodeId,
                                         Map<String, Object> stateData, String status, int stepCount) {
            FlowExecutionData existing = executions.get(executionId);
            if (existing != null) {
                executions.put(executionId, new FlowExecutionData(
                    existing.id(), existing.tenantId(), existing.flowId(), status,
                    existing.startedBy(), existing.triggerRecordId(), stateData,
                    currentNodeId, existing.errorMessage(), stepCount, existing.durationMs(),
                    existing.initialInput(), existing.isTest(), existing.startedAt(), null));
            }
        }

        @Override
        public void completeExecution(String executionId, String status, Map<String, Object> stateData,
                                      String errorMessage, int durationMs, int stepCount) {
            FlowExecutionData existing = executions.get(executionId);
            if (existing != null) {
                executions.put(executionId, new FlowExecutionData(
                    existing.id(), existing.tenantId(), existing.flowId(), status,
                    existing.startedBy(), existing.triggerRecordId(), stateData,
                    existing.currentNodeId(), errorMessage, stepCount, durationMs,
                    existing.initialInput(), existing.isTest(), existing.startedAt(), Instant.now()));
            }
        }

        @Override
        public void cancelExecution(String executionId) {
            FlowExecutionData existing = executions.get(executionId);
            if (existing != null) {
                executions.put(executionId, new FlowExecutionData(
                    existing.id(), existing.tenantId(), existing.flowId(),
                    FlowExecutionData.STATUS_CANCELLED,
                    existing.startedBy(), existing.triggerRecordId(), existing.stateData(),
                    existing.currentNodeId(), null, existing.stepCount(), existing.durationMs(),
                    existing.initialInput(), existing.isTest(), existing.startedAt(), Instant.now()));
            }
        }

        @Override
        public String logStepExecution(String executionId, String stateId, String stateName,
                                       String stateType, Map<String, Object> inputSnapshot,
                                       Map<String, Object> outputSnapshot, String status,
                                       String errorMessage, String errorCode,
                                       Integer durationMs, int attemptNumber) {
            String id = UUID.randomUUID().toString();
            FlowStepLogData log = new FlowStepLogData(
                id, executionId, stateId, stateName, stateType, status,
                inputSnapshot, outputSnapshot, errorMessage, errorCode,
                attemptNumber, durationMs, Instant.now(), null);
            stepLogs.computeIfAbsent(executionId, k -> new ArrayList<>()).add(log);
            return id;
        }

        @Override
        public void updateStepLog(String stepLogId, Map<String, Object> outputSnapshot,
                                  String status, String errorMessage, String errorCode, int durationMs) {
            // In-memory: find and replace
            for (List<FlowStepLogData> logs : stepLogs.values()) {
                for (int i = 0; i < logs.size(); i++) {
                    if (logs.get(i).id().equals(stepLogId)) {
                        FlowStepLogData old = logs.get(i);
                        logs.set(i, new FlowStepLogData(
                            old.id(), old.executionId(), old.stateId(), old.stateName(),
                            old.stateType(), status, old.inputSnapshot(), outputSnapshot,
                            errorMessage, errorCode, old.attemptNumber(), durationMs,
                            old.startedAt(), Instant.now()));
                        return;
                    }
                }
            }
        }

        @Override
        public List<FlowStepLogData> loadStepLogs(String executionId) {
            return stepLogs.getOrDefault(executionId, List.of());
        }

        @Override
        public List<FlowExecutionData> findExecutionsByFlow(String flowId, int limit, int offset) {
            return executions.values().stream()
                .filter(e -> e.flowId().equals(flowId))
                .toList();
        }

        @Override
        public List<FlowExecutionData> findWaitingExecutions() {
            return executions.values().stream()
                .filter(e -> FlowExecutionData.STATUS_WAITING.equals(e.status()))
                .toList();
        }

        @Override
        public String createPendingResume(String executionId, String tenantId,
                                          Instant resumeAt, String resumeEvent) {
            String id = UUID.randomUUID().toString();
            pendingResumes.put(id, Map.of("executionId", executionId));
            return id;
        }

        @Override
        public List<String> claimPendingResumes(String claimedBy, int limit) {
            return List.of();
        }

        @Override
        public Optional<String> claimPendingResumeByEvent(String resumeEvent, String claimedBy) {
            return Optional.empty();
        }

        @Override
        public void deletePendingResume(String executionId) {
            pendingResumes.values().removeIf(v -> executionId.equals(v.get("executionId")));
        }

        @Override
        public void logAuditEvent(String tenantId, String flowId, String action,
                                  String userId, Map<String, Object> details) {
            // No-op in tests
        }

        public List<FlowExecutionData> getAllExecutions() {
            return new ArrayList<>(executions.values());
        }
    }
}
