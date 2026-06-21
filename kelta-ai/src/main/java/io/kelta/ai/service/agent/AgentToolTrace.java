package io.kelta.ai.service.agent;

import java.util.Map;

/** Audit-friendly record of one tool invocation during an agent run, returned in the run result. */
public record AgentToolTrace(
        String name,
        Map<String, Object> input,
        String resultJson,
        boolean isError,
        boolean permitted
) {
}
