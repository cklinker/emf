package io.kelta.ai.service.agent;

import io.kelta.ai.model.AgentExecution;
import io.kelta.ai.repository.AgentExecutionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Persists and reads the governed-agent audit trail ({@code ai_agent_execution}). One row is written
 * per run — including refusals (disabled / quota) — by {@link AgentRuntimeService}.
 */
@Service
public class AgentExecutionService {

    /** Upper bound on how many execution rows a single list request may return. */
    static final int MAX_LIST_LIMIT = 200;
    private static final int DEFAULT_LIST_LIMIT = 50;

    private final AgentExecutionRepository repository;

    public AgentExecutionService(AgentExecutionRepository repository) {
        this.repository = repository;
    }

    public void record(AgentExecution execution) {
        repository.save(execution);
    }

    public List<AgentExecution> list(String tenantId, UUID agentId, Integer limit) {
        int effective = limit == null ? DEFAULT_LIST_LIMIT : Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
        return repository.findByAgent(tenantId, agentId, effective);
    }
}
