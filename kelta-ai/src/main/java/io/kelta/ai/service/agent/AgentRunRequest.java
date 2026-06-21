package io.kelta.ai.service.agent;

/** Payload for running an agent: the user's input prompt for this run. */
public record AgentRunRequest(String input) {
}
