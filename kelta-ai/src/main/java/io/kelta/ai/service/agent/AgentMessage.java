package io.kelta.ai.service.agent;

import java.util.List;
import java.util.Map;

/**
 * A portable conversation message accumulated during an agent run: a role ({@code "user"} or
 * {@code "assistant"}) and a list of content blocks as plain maps (text / tool_use / tool_result),
 * matching the block shape used by {@code ChatMessage.contentBlocks}. The model client converts
 * these to Anthropic {@code MessageParam}s per turn.
 */
public record AgentMessage(String role, List<Map<String, Object>> blocks) {

    public static AgentMessage userText(String text) {
        return new AgentMessage("user", List.of(Map.of("type", "text", "text", text)));
    }

    public static AgentMessage assistant(List<Map<String, Object>> blocks) {
        return new AgentMessage("assistant", blocks);
    }

    public static AgentMessage user(List<Map<String, Object>> blocks) {
        return new AgentMessage("user", blocks);
    }
}
