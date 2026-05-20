package io.kelta.ai.service.tools;

import io.kelta.ai.model.AiProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public ToolDispatcher(ToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public DispatchResult dispatch(String tenantId, String userId,
                                    String toolUseId, String toolName,
                                    Map<String, Object> input) {
        ToolHandler handler = registry.handler(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));

        return switch (handler) {
            case ReadToolHandler r -> dispatchRead(r, tenantId, userId, toolUseId, toolName, input);
            case ProposeToolHandler p -> dispatchPropose(p, toolUseId, toolName, input);
        };
    }

    private DispatchResult dispatchRead(ReadToolHandler handler, String tenantId, String userId,
                                         String toolUseId, String toolName, Map<String, Object> input) {
        try {
            Object data = handler.execute(tenantId, userId, input);
            String json = objectMapper.writeValueAsString(data);
            return DispatchResult.readResult(toolUseId, toolName, json, false);
        } catch (IllegalArgumentException e) {
            log.warn("Tool '{}' rejected input: {}", toolName, e.getMessage());
            return DispatchResult.readResult(toolUseId, toolName, errorJson("invalid_input", e.getMessage()), true);
        } catch (Exception e) {
            log.error("Tool '{}' failed: {}", toolName, e.getMessage(), e);
            return DispatchResult.readResult(toolUseId, toolName, errorJson(e.getClass().getSimpleName(), e.getMessage()), true);
        }
    }

    private DispatchResult dispatchPropose(ProposeToolHandler handler, String toolUseId,
                                            String toolName, Map<String, Object> input) {
        try {
            AiProposal proposal = handler.buildProposal(input);
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("status", "queued");
            ack.put("proposalId", proposal.id().toString());
            ack.put("message", "Proposal queued for user approval. Continue with any other tool calls or summarize.");
            String ackJson = objectMapper.writeValueAsString(ack);
            return DispatchResult.proposalQueued(toolUseId, toolName, ackJson, proposal);
        } catch (IllegalArgumentException e) {
            log.warn("Propose tool '{}' rejected input: {}", toolName, e.getMessage());
            return DispatchResult.readResult(toolUseId, toolName, errorJson("invalid_input", e.getMessage()), true);
        } catch (Exception e) {
            log.error("Propose tool '{}' failed: {}", toolName, e.getMessage(), e);
            return DispatchResult.readResult(toolUseId, toolName, errorJson(e.getClass().getSimpleName(), e.getMessage()), true);
        }
    }

    private String errorJson(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", code, "message", message != null ? message : ""));
        } catch (Exception e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
