package io.kelta.ai.service.agent;

import java.util.Map;

/** A tool-use request the model emitted during an agent turn (SDK-free, portable). */
public record AgentToolCall(String id, String name, Map<String, Object> input) {
}
