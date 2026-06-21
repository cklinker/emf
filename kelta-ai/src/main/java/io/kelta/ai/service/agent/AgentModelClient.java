package io.kelta.ai.service.agent;

/**
 * One Anthropic round-trip for the agent runtime, behind an SDK-free seam so {@link AgentRuntimeService}
 * can be unit-tested by scripting turns. The implementation owns all Anthropic SDK plumbing: building
 * the request with the agent's restricted tool subset and model/budget overrides, and extracting the
 * response into a portable {@link AgentTurn}.
 */
public interface AgentModelClient {

    AgentTurn nextTurn(AgentTurnRequest request);
}
