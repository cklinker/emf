package io.kelta.ai.service.agent;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.model.AgentExecution;
import io.kelta.ai.service.TokenTrackingService;
import io.kelta.ai.service.tools.DispatchResult;
import io.kelta.ai.service.tools.ToolDispatcher;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a governed {@link AgentDefinition}: a bounded tool-use loop that calls the model
 * ({@link AgentModelClient}), executes the requested tools ({@link ToolDispatcher}) — but only the
 * ones the agent is permitted to call — feeds results back, and repeats until the model finishes,
 * the iteration cap is hit, or the per-run token budget is exhausted. Token usage is recorded
 * against the tenant's monthly quota each turn.
 *
 * <p>This class is deliberately free of Anthropic SDK types so the loop is unit-testable by
 * scripting {@link AgentTurn}s through a mocked {@link AgentModelClient}.
 */
@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    /** Hard cap on model round-trips per run, to bound cost and stop runaway loops. */
    static final int MAX_ITERATIONS = 8;

    /** Hard cap on total tokens (input + output) per run. */
    static final long MAX_RUN_TOKENS = 100_000L;

    private static final String TOOL_USE = "tool_use";

    private final AgentModelClient modelClient;
    private final ToolDispatcher toolDispatcher;
    private final TokenTrackingService tokenTrackingService;
    private final AgentExecutionService executionService;
    private final PiiMaskingService piiMaskingService;
    private final ObjectMapper objectMapper;

    public AgentRuntimeService(AgentModelClient modelClient, ToolDispatcher toolDispatcher,
                               TokenTrackingService tokenTrackingService,
                               AgentExecutionService executionService,
                               PiiMaskingService piiMaskingService, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.toolDispatcher = toolDispatcher;
        this.tokenTrackingService = tokenTrackingService;
        this.executionService = executionService;
        this.piiMaskingService = piiMaskingService;
        this.objectMapper = objectMapper;
    }

    public AgentRunResult run(String tenantId, String userId, AgentDefinition agent, String userInput) {
        if (!agent.enabled()) {
            audit(tenantId, agent.id(), userId, userInput, "refused_disabled",
                    List.of(), 0, 0, 0, null, "Agent is disabled");
            throw new AgentExecutionException(AgentExecutionException.Reason.AGENT_DISABLED,
                    "Agent '" + agent.name() + "' is disabled");
        }
        if (tokenTrackingService.isTokenLimitExceeded(tenantId)) {
            audit(tenantId, agent.id(), userId, userInput, "refused_token_limit",
                    List.of(), 0, 0, 0, null, "Monthly AI token limit reached");
            throw new AgentExecutionException(AgentExecutionException.Reason.TOKEN_LIMIT_EXCEEDED,
                    "Monthly AI token limit reached for this tenant");
        }

        Set<String> allowed = new HashSet<>(agent.allowedTools());
        List<AgentMessage> history = new ArrayList<>();
        history.add(AgentMessage.userText(userInput));

        List<AgentToolTrace> traces = new ArrayList<>();
        int totalIn = 0;
        int totalOut = 0;
        int iterations = 0;
        String stopReason = null;
        String finalText = "";
        boolean completed = false;
        boolean budgetExceeded = false;

      try {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            iterations++;
            AgentTurn turn = modelClient.nextTurn(new AgentTurnRequest(
                    tenantId, agent.systemPrompt(), agent.model(), agent.maxTokens(),
                    agent.allowedTools(), history));

            totalIn += turn.inputTokens();
            totalOut += turn.outputTokens();
            tokenTrackingService.recordUsage(tenantId, turn.inputTokens(), turn.outputTokens());

            stopReason = turn.stopReason();
            finalText = turn.text();
            history.add(AgentMessage.assistant(assistantBlocks(turn)));

            if (!TOOL_USE.equals(stopReason) || !turn.requestedTools()) {
                completed = true;
                break;
            }

            List<Map<String, Object>> resultBlocks = new ArrayList<>();
            for (AgentToolCall call : turn.toolCalls()) {
                if (!allowed.contains(call.name())) {
                    // Defense in depth: the model was only offered allowed tools, but never execute
                    // an out-of-policy tool even if one is somehow requested.
                    log.warn("Agent '{}' requested non-permitted tool '{}'; refusing", agent.name(), call.name());
                    String err = errorJson("tool_not_permitted", call.name());
                    traces.add(new AgentToolTrace(call.name(), call.input(), err, true, false));
                    resultBlocks.add(toolResultBlock(call.id(), err, true));
                    continue;
                }
                DispatchResult result = toolDispatcher.dispatch(
                        tenantId, userId, call.id(), call.name(), call.input());
                // Mask PII pulled from the platform before it reaches the model or the audit trail.
                String maskedJson = piiMaskingService.mask(result.resultJson());
                traces.add(new AgentToolTrace(call.name(), call.input(),
                        maskedJson, result.isError(), true));
                resultBlocks.add(toolResultBlock(result.toolUseId(), maskedJson, result.isError()));
            }
            history.add(AgentMessage.user(resultBlocks));

            if (totalIn + totalOut > MAX_RUN_TOKENS) {
                log.warn("Agent '{}' run exceeded per-run token budget ({} tokens)",
                        agent.name(), totalIn + totalOut);
                budgetExceeded = true;
                break;
            }
        }

        boolean maxIterationsReached = !completed && !budgetExceeded;
        String status = budgetExceeded ? "budget_exceeded"
                : (maxIterationsReached ? "max_iterations" : "completed");
        audit(tenantId, agent.id(), userId, userInput, status, traces, totalIn, totalOut,
                iterations, finalText, null);
        return new AgentRunResult(finalText, traces, totalIn, totalOut, iterations,
                stopReason, budgetExceeded, maxIterationsReached);
      } catch (RuntimeException e) {
        log.error("Agent '{}' run failed: {}", agent.name(), e.getMessage(), e);
        audit(tenantId, agent.id(), userId, userInput, "error", traces, totalIn, totalOut,
                iterations, finalText, e.getMessage());
        throw e;
      }
    }

    private void audit(String tenantId, UUID agentId, String userId, String input, String status,
                       List<AgentToolTrace> traces, int inputTokens, int outputTokens, int iterations,
                       String finalText, String error) {
        try {
            executionService.record(new AgentExecution(UUID.randomUUID(), tenantId, agentId, userId,
                    input, status, List.copyOf(traces), inputTokens, outputTokens, iterations,
                    finalText, error, Instant.now()));
        } catch (RuntimeException e) {
            // Auditing must never break a run; log and continue.
            log.error("Failed to persist agent execution audit row: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> assistantBlocks(AgentTurn turn) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (turn.text() != null && !turn.text().isEmpty()) {
            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", turn.text());
            blocks.add(textBlock);
        }
        for (AgentToolCall call : turn.toolCalls()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "tool_use");
            block.put("id", call.id());
            block.put("name", call.name());
            block.put("input", call.input());
            blocks.add(block);
        }
        return blocks;
    }

    private Map<String, Object> toolResultBlock(String toolUseId, String content, boolean isError) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_result");
        block.put("tool_use_id", toolUseId);
        block.put("content", content);
        block.put("is_error", isError);
        return block;
    }

    private String errorJson(String error, String tool) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", error, "tool", tool));
        } catch (RuntimeException e) {
            return "{\"error\":\"" + error + "\"}";
        }
    }
}
